package com.icewolf.maidrestaurant.business.core;

import cn.breezeth.ordertocook.block.entity.OrderMachineBlockEntity;
import cn.breezeth.ordertocook.core.ModConstants;
import cn.breezeth.ordertocook.core.OrderGenerator;
import cn.breezeth.ordertocook.util.CoinUtils;
import com.icewolf.maidrestaurant.business.MaidRestaurantBusiness;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import com.icewolf.maidrestaurant.business.util.ItemStackUtils;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * 玩家之间通过下单了(OTC)相互下单买菜的核心逻辑。
 * 流程：菜单右键打单机读可售快照 → 右键方块标记送餐点(限绑定机24格) → 右键空气开GUI下单
 * → 下单即扣款归店、生成特殊订单 → 出餐台打包成特殊餐盘/外卖袋 → 女仆或玩家送到送餐点
 * → 持有者 Shift+右键拆包，成品进背包、溢出掉脚下。
 */
public final class PlayerOrderManager {
    // ===== 菜单 NBT 键 =====
    public static final String M_MACHINE = "MenuMachine";
    public static final String M_DIM = "MenuDim";
    public static final String M_FOODS = "MenuFoods";
    public static final String M_READ_TICK = "MenuReadTick";
    public static final String M_DELIVERY_POS = "MenuDeliveryPos";
    public static final String M_MENU_LEVEL = "MenuLevel";
    public static final String M_MENU_TITLE = "MenuTitle";
    public static final String M_MENU_BG = "MenuBg";
    // ===== 订单 / 包裹 NBT 键 =====
    public static final String PLAYER_ORDER = "PlayerOrder";
    public static final String PLAYER_PACKAGE = "PlayerPackage";
    public static final String BUYER_UUID = "BuyerUuid";
    public static final String BUYER_NAME = "BuyerName";
    public static final String PLAYER_DELIVERY_POS = "PlayerDeliveryPos";
    public static final String UNPACKED = "PlayerUnpacked";

    public static final int MAX_KINDS = 6;
    public static final int MAX_PER_KIND = 6;
    public static final long DURATION_TICKS = 30L * 60L * 20L;
    public static final int BIND_RADIUS = 24;

    // ===== 配送时限档位（决定到期时长与时限加价，百分比）=====
    // 普通 30min ×1.0 / 加急 15min ×1.5 / 特急 10min ×2.0
    public static final int TIME_TIER_COUNT = 3;
    public static final long[] TIME_TIER_TICKS = { 36000L, 18000L, 12000L };
    public static final int[] TIME_TIER_RATE = { 100, 150, 200 };
    // ===== 小费档位（纯打赏，不缩短时间，百分比）=====
    // 无 0 / 小赏 +10% / 中赏 +20% / 厚赏 +30%
    public static final int TIP_TIER_COUNT = 4;
    public static final int[] TIP_TIER_RATE = { 0, 10, 20, 30 };

    /** 规整档位下标到合法范围。 */
    public static int clampTimeTier(int t) {
        return t < 0 ? 0 : (t >= TIME_TIER_COUNT ? TIME_TIER_COUNT - 1 : t);
    }

    public static int clampTipTier(int t) {
        return t < 0 ? 0 : (t >= TIP_TIER_COUNT ? TIP_TIER_COUNT - 1 : t);
    }

    /**
     * 按档位计算订单最终金额（客户端 GUI 与服务端共用，保证口径一致）。
     * 基础餐费复用 OTC 算价；时限金额与小费金额分别按百分比四舍五入（整数运算、确定性）。
     * 最终 = round(基础 × 时限系数) + round(基础 × 小费比例)，最高组合 ≈ 2.3 倍。
     */
    public static int computeTotalPrice(CompoundTag foodList, int machineLevel, int timeTier, int tipTier) {
        int base = computeBasePrice(foodList, machineLevel);
        if (base <= 0) return 0;
        timeTier = clampTimeTier(timeTier);
        tipTier = clampTipTier(tipTier);
        int timePrice = (base * TIME_TIER_RATE[timeTier] + 50) / 100;
        int tipPrice = (base * TIP_TIER_RATE[tipTier] + 50) / 100;
        return timePrice + tipPrice;
    }

    /** 按小费档位计算小费金额（基于基础餐费，四舍五入）；用于结算收款消息展示。 */
    public static int computeTipAmount(CompoundTag foodList, int machineLevel, int tipTier) {
        int base = computeBasePrice(foodList, machineLevel);
        if (base <= 0) return 0;
        tipTier = clampTipTier(tipTier);
        return (base * TIP_TIER_RATE[tipTier] + 50) / 100;
    }

    private PlayerOrderManager() {}

    // ===== 1. 右键打单机：读取可售食物快照 =====
    public static void bindMachine(ServerLevel level, Player player, InteractionHand hand, BlockPos machine) {
        if (!(level.getBlockEntity(machine) instanceof OrderMachineBlockEntity boundMachine)) {
            player.displayClientMessage(Component.translatable("message.business.menu.need_machine"), true);
            return;
        }
        List<Item> foods = OrderMachineBlockEntity.getMenuFoodsForMachine(level, machine);
        CompoundTag foodsTag = new CompoundTag();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Item food : foods) {
            var rl = BuiltInRegistries.ITEM.getKey(food);
            if (rl == null) continue;
            if (seen.add(rl.toString())) {
                foodsTag.putInt(rl.toString(), 1);
            }
        }
        ItemStack menu = player.getItemInHand(hand);
        CompoundTag tag = ItemStackUtils.getOrCreateTag(menu);
        tag.putLong(M_MACHINE, machine.asLong());
        tag.putString(M_DIM, level.dimension().location().toString());
        tag.put(M_FOODS, foodsTag);
        tag.putLong(M_READ_TICK, level.getGameTime());
        tag.putInt(M_MENU_LEVEL, boundMachine.snapshotStats().level());
        tag.remove(M_DELIVERY_POS);
        ItemStackUtils.setTag(menu, tag);
        player.displayClientMessage(Component.translatable("message.business.menu.bound", foodsTag.size()), true);
    }

    // ===== 2. 右键方块：标记送餐点 =====
    public static boolean selectDeliveryPoint(ServerLevel level, Player player, InteractionHand hand,
                                              BlockPos clicked, Direction face) {
        ItemStack menu = player.getItemInHand(hand);
        CompoundTag tag = ItemStackUtils.getTag(menu);
        if (tag == null || !tag.contains(M_MACHINE)) {
            player.displayClientMessage(Component.translatable("message.business.menu.need_bind"), true);
            return false;
        }
        BlockPos machine = BlockPos.of(tag.getLong(M_MACHINE));
        String dim = tag.getString(M_DIM);
        if (!dim.equals(level.dimension().location().toString())) {
            player.displayClientMessage(Component.translatable("message.business.menu.wrong_dim"), true);
            return false;
        }
        if (machine.distSqr(clicked) > BIND_RADIUS * BIND_RADIUS) {
            player.displayClientMessage(Component.translatable("message.business.menu.too_far"), true);
            return false;
        }

        BlockEntity be = level.getBlockEntity(clicked);
        BlockPos point;
        boolean container = false;
        if (be != null && OrderBridge.getItemHandler(be) != null) {
            point = clicked;
            container = true;
        } else {
            point = clicked.relative(face);
            if (!isValidServePos(level, point)) {
                player.displayClientMessage(Component.translatable("message.business.menu.bad_point"), true);
                return false;
            }
        }

        CompoundTag dp = new CompoundTag();
        dp.putInt("x", point.getX());
        dp.putInt("y", point.getY());
        dp.putInt("z", point.getZ());
        dp.putString("dim", level.dimension().location().toString());
        dp.putBoolean("container", container);
        tag.put(M_DELIVERY_POS, dp);
        ItemStackUtils.setTag(menu, tag);
        player.displayClientMessage(
                Component.translatable("message.business.menu.point_set", point.getX(), point.getY(), point.getZ()), true);
        return true;
    }

    private static boolean isValidServePos(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).canBeReplaced()) return false;
        BlockState below = level.getBlockState(pos.below());
        return below.isFaceSturdy(level, pos.below(), Direction.UP);
    }

    public static boolean canOpenOrderMenu(ItemStack menu) {
        CompoundTag t = ItemStackUtils.getTag(menu);
        return t != null && t.contains(M_MACHINE) && t.contains(M_FOODS) && t.contains(M_DELIVERY_POS);
    }

    // ===== 3. 提交下单（GUI 网络包 → 服务端） =====
    public static void submitOrder(ServerLevel level, ServerPlayer buyer, ItemStack menuStack,
                                   LinkedHashMap<String, Integer> selection, int timeTier, int tipTier) {
        timeTier = clampTimeTier(timeTier);
        tipTier = clampTipTier(tipTier);
        CompoundTag tag = ItemStackUtils.getTag(menuStack);
        if (tag == null || !tag.contains(M_MACHINE) || !tag.contains(M_FOODS)) {
            fail(buyer, "message.business.order.need_bind");
            return;
        }
        if (!tag.contains(M_DELIVERY_POS)) {
            fail(buyer, "message.business.order.need_point");
            return;
        }
        CompoundTag snap = tag.getCompound(M_FOODS);

        CompoundTag foodList = new CompoundTag();
        int kinds = 0;
        for (Map.Entry<String, Integer> e : selection.entrySet()) {
            String id = e.getKey();
            int cnt = e.getValue();
            if (cnt <= 0) continue;
            if (!snap.contains(id)) {
                fail(buyer, "message.business.order.invalid_food");
                return;
            }
            if (cnt > MAX_PER_KIND) cnt = MAX_PER_KIND;
            foodList.putInt(id, cnt);
            kinds++;
        }
        if (kinds == 0) {
            fail(buyer, "message.business.order.empty");
            return;
        }
        if (kinds > MAX_KINDS) {
            fail(buyer, "message.business.order.too_many_kinds");
            return;
        }

        BlockPos machine = BlockPos.of(tag.getLong(M_MACHINE));
        String dim = tag.getString(M_DIM);
        if (!dim.equals(level.dimension().location().toString())) {
            fail(buyer, "message.business.menu.wrong_dim");
            return;
        }
        CompoundTag dp = tag.getCompound(M_DELIVERY_POS);

        OrderMachineBlockEntity mbe =
                level.getBlockEntity(machine) instanceof OrderMachineBlockEntity m ? m : null;
        int machineLevel = mbe != null ? mbe.snapshotStats().level() : 0;

        int price = computeTotalPrice(foodList, machineLevel, timeTier, tipTier);
        int tipAmount = computeTipAmount(foodList, machineLevel, tipTier);
        if (price <= 0) {
            fail(buyer, "message.business.order.price_fail");
            return;
        }

        if (!CoinUtils.tryConsumeWithChange(buyer, price)) {
            buyer.displayClientMessage(
                    Component.translatable("message.business.order.not_enough_money", price).withStyle(ChatFormatting.RED),
                    false);
            return;
        }

        // 扣款后冻结于托管台账，买家拆包确认收货时才给店铺到账
        ItemStack order = createPlayerOrder(level, machine, mbe, foodList, buyer, dp, price, timeTier);
        CompoundTag onbt = ItemStackUtils.getTag(order);
        if (onbt != null) {
            PlayerOrderEscrow.get(level.getServer()).createOrder(
                    onbt.getString(ModConstants.NBT_ORDER_ID),
                    price,
                    tipAmount,
                    buyer.getUUID(),
                    buyer.getName().getString(),
                    machine.asLong(),
                    level.dimension().location().toString(),
                    onbt.getLong(ModConstants.NBT_EXPIRY_TICK));
        }
        giveOrderToBuyer(level, buyer, order);

        buyer.displayClientMessage(
                Component.translatable("message.business.order.placed", price).withStyle(ChatFormatting.GREEN), false);
    }

    private static int computeBasePrice(CompoundTag foodList, int machineLevel) {
        CompoundTag tmp = new CompoundTag();
        tmp.put(ModConstants.NBT_FOOD_LIST, foodList);
        tmp.putInt(ModConstants.NBT_TYPE, 0);
        tmp.putBoolean(ModConstants.NBT_DELIVERY, true);
        tmp.putBoolean(ModConstants.NBT_URGENT, false);
        tmp.putBoolean(ModConstants.NBT_IS_LONG_DISTANCE, false);
        return OrderGenerator.recalcPrestigeFromNbt(tmp, machineLevel);
    }

    /**
     * 给店铺到账：店主在线直接发放，否则按「维度#打单机坐标」计入 ShopEarnings 待领。
     */
    public static void creditShop(ServerLevel level, BlockPos machine, int amount, int tipAmount) {
        if (amount <= 0) return;
        OrderMachineBlockEntity mbe =
                level.getBlockEntity(machine) instanceof OrderMachineBlockEntity m ? m : null;
        String ownerName = readOwnerName(mbe);
        if (ownerName != null && !ownerName.isBlank()) {
            ServerPlayer owner = level.getServer().getPlayerList().getPlayerByName(ownerName);
            if (owner != null) {
                CoinUtils.giveCoins(owner, amount);
                MutableComponent earned = tipAmount > 0
                        ? Component.translatable("message.business.order.shop_earned_tip", amount, tipAmount)
                        : Component.translatable("message.business.order.shop_earned", amount);
                owner.displayClientMessage(earned.withStyle(ChatFormatting.GOLD), false);
                return;
            }
        }
        ShopEarnings.get(level)
                .add(ShopEarnings.key(level.dimension().location().toString(), machine.asLong()), amount);
    }

    private static ItemStack createPlayerOrder(ServerLevel level, BlockPos machine, OrderMachineBlockEntity mbe,
                                               CompoundTag foodList, ServerPlayer buyer, CompoundTag dp, int price, int timeTier) {
        ItemStack order = new ItemStack(OtcCompat.ORDER());
        CompoundTag nbt = new CompoundTag();
        nbt.put(ModConstants.NBT_FOOD_LIST, foodList);
        String orderId = cn.breezeth.ordertocook.core.OtcRuntimeIdState.get(level).allocateOrderId();
        nbt.putString(ModConstants.NBT_ORDER_ID, orderId);
        nbt.putInt(ModConstants.NBT_TYPE, 0);
        nbt.putBoolean(ModConstants.NBT_DELIVERY, true);
        nbt.putBoolean(ModConstants.NBT_URGENT, false);
        nbt.putBoolean(ModConstants.NBT_IS_LONG_DISTANCE, false);
        nbt.putLong(ModConstants.NBT_EXPIRY_TICK, level.getGameTime() + TIME_TIER_TICKS[timeTier]);
        nbt.putInt(ModConstants.NBT_PRESTIGE, price);
        nbt.putString(ModConstants.NBT_CUSTOMER_NAME, buyer.getName().getString());
        nbt.putBoolean(PLAYER_ORDER, true);
        nbt.putBoolean(PLAYER_PACKAGE, true);
        nbt.putUUID(BUYER_UUID, buyer.getUUID());
        nbt.putString(BUYER_NAME, buyer.getName().getString());
        nbt.put(PLAYER_DELIVERY_POS, dp.copy());
        if (mbe != null) {
            nbt.putInt(ModConstants.NBT_MACHINE_ID, mbe.ensureMachineId(level));
            nbt.putLong(ModConstants.NBT_MACHINE_POS, machine.asLong());
            nbt.putString(ModConstants.NBT_MACHINE_DIM, level.dimension().location().toString());
        }
        ItemStackUtils.setTag(order, nbt);
        order.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                buildOrderDisplayName(buyer.getName().getString(), foodList, timeTier));
        return order;
    }

    /**
     * 玩家订单显示名：按菜品种类数反推套餐档次（颜色对齐 OTC 稀有度），
     * 并按时限档位追加「加急 / 特急」后缀。买家名与"的"为白色，档次名带稀有度色。
     */
    public static net.minecraft.network.chat.MutableComponent buildOrderDisplayName(
            String buyerName, CompoundTag foodList, int timeTier) {
        int kinds = foodList != null ? foodList.getAllKeys().size() : 0;
        String tierKey;
        ChatFormatting tierColor;
        if (kinds <= 1) {
            tierKey = "order.business.name.tier0"; tierColor = ChatFormatting.WHITE;
        } else if (kinds == 2) {
            tierKey = "order.business.name.tier1"; tierColor = ChatFormatting.GREEN;
        } else if (kinds == 3) {
            tierKey = "order.business.name.tier2"; tierColor = ChatFormatting.BLUE;
        } else if (kinds == 4) {
            tierKey = "order.business.name.tier3"; tierColor = ChatFormatting.LIGHT_PURPLE;
        } else {
            tierKey = "order.business.name.tier4"; tierColor = ChatFormatting.RED;
        }

        net.minecraft.network.chat.MutableComponent builder = Component.literal(buyerName + "的")
                .append(Component.translatable(tierKey).withStyle(tierColor));
        if (timeTier == 1) {
            builder.append(Component.translatable("order.business.suffix.rush").withStyle(ChatFormatting.GOLD));
        } else if (timeTier == 2) {
            builder.append(Component.translatable("order.business.suffix.express").withStyle(ChatFormatting.RED));
        }
        return builder;
    }

    private static void giveOrderToBuyer(ServerLevel level, ServerPlayer buyer, ItemStack order) {
        ItemStack rem = ItemHandlerHelper.insertItemStacked(new InvWrapper(buyer.getInventory()), order, false);
        if (!rem.isEmpty()) {
            Block.popResource(level, buyer.blockPosition(), rem);
        }
    }

    // ===== 4. 送达：在送餐点放成包裹方块（容器则入容器） =====
    public static boolean placePackageAt(ServerLevel level, ItemStack pkg, BlockPos pos) {
        if (pkg.isEmpty()) return false;

        BlockEntity be = level.getBlockEntity(pos);
        if (be != null) {
            IItemHandler handler = OrderBridge.getItemHandler(be);
            if (handler != null) {
                ItemStack rem = ItemHandlerHelper.insertItemStacked(handler, pkg.copy(), false);
                if (rem.isEmpty()) {
                    pkg.shrink(1);
                    return true;
                }
            }
        }

        if (!level.getBlockState(pos).canBeReplaced()) return false;

        // 判定是外卖袋还是餐盘：优先用我们自己的 NBT 标记，回退按注册名判定（不依赖 instanceof OTC 类型）
        boolean isBag = isPlayerPackageOrBag(pkg);

        Block block = getOtcBlock(pkg, isBag ? "takeout_bag" : "food_plate_display");
        if (block == null || block == Blocks.AIR) {
            MaidRestaurantBusiness.LOGGER.warn("送达: 找不到对应 OTC 方块(是外卖袋={}), 无法在 {} 放置包裹", isBag, pos);
            return false;
        }
        BlockState bs = block.defaultBlockState();
        // 朝向属性跨 loader 安全：从 BlockState 动态取属性，不引用 OTC 类静态字段
        var facingProp = bs.getBlock().getStateDefinition().getProperty("facing");
        if (facingProp instanceof net.minecraft.world.level.block.state.properties.DirectionProperty dp) {
            bs = bs.setValue(dp, Direction.NORTH);
        }
        level.setBlock(pos, bs, Block.UPDATE_ALL);

        // 写入堆叠：用反射调用 setBagStack/setPlateStack（与 getTakeoutBagStack/clearTakeoutBag 一致），
        // 避免 instanceof TakeoutBagBlockEntity（跨类加载器下类型判断不可靠）
        BlockEntity placed = level.getBlockEntity(pos);
        if (placed != null && setStackOnBlockEntityReflective(placed, pkg.copy())) {
            pkg.shrink(1);
            return true;
        }
        return false;
    }

    /** 跨 loader 安全：从全局注册表按注册名取 OTC 方块（namespace 与物品保持一致，默认 ordertocook）。 */
    private static Block getOtcBlock(ItemStack pkg, String path) {
        ResourceLocation itemRl = BuiltInRegistries.ITEM.getKey(pkg.getItem());
        String namespace = itemRl.getNamespace();
        if (namespace == null || namespace.isEmpty() || namespace.equals("minecraft")) {
            namespace = "ordertocook";
        }
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    /** 判定 pkg 是否为玩家外卖袋（玩家订单包裹一定是外卖袋）。优先 NBT 标记，回退注册名。 */
    private static boolean isPlayerPackageOrBag(ItemStack stack) {
        CompoundTag t = ItemStackUtils.getTag(stack);
        if (t != null && (t.getBoolean(PLAYER_PACKAGE) || t.getBoolean(PLAYER_ORDER))) {
            return true;
        }
        String name = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return name.contains("takeout") || name.contains("bag");
    }

    /** 跨 loader 安全：用反射把堆叠写入 OTC 方块实体（setBagStack / setPlateStack / 兼容别名）。 */
    private static boolean setStackOnBlockEntityReflective(BlockEntity be, ItemStack stack) {
        if (be == null) return false;
        try {
            for (java.lang.reflect.Method m : be.getClass().getMethods()) {
                String n = m.getName();
                if (n.equals("setBagStack") || n.equals("setTakeoutStack")
                        || n.equals("setItemStack") || n.equals("setPlateStack")) {
                    Class<?>[] params = m.getParameterTypes();
                    if (params.length == 1 && ItemStack.class.isAssignableFrom(params[0])) {
                        m.invoke(be, stack);
                        return true;
                    }
                }
            }
        } catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.error("送达: 反射写入包裹堆叠失败", t);
        }
        return false;
    }

    // ===== 5. 拆包：成品进背包、溢出掉脚下；成功后店铺才收款 =====
    public static void unpack(Level level, Player player, ItemStack pkg) {
        CompoundTag nbt = ItemStackUtils.getTag(pkg);
        if (nbt == null || !nbt.getBoolean(PLAYER_PACKAGE)) return;
        if (nbt.getBoolean(UNPACKED)) return;

        MinecraftServer server = level.getServer();
        String orderId = nbt.getString(ModConstants.NBT_ORDER_ID);
        boolean escrowed = server != null && !orderId.isEmpty();
        // 托管门控：已超时 / 已退款 / 已结算的包裹不可拆
        if (escrowed && !PlayerOrderEscrow.get(server).canUnpack(orderId, server.overworld().getGameTime())) {
            player.displayClientMessage(
                    Component.translatable("message.business.order.package_invalid").withStyle(ChatFormatting.RED),
                    false);
            return;
        }

        CompoundTag foodList = nbt.getCompound(ModConstants.NBT_FOOD_LIST);

        for (String key : foodList.getAllKeys()) {
            var rl = ResourceLocation.tryParse(key);
            if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) continue;
            Item item = BuiltInRegistries.ITEM.get(rl);
            ItemStack give = new ItemStack(item, foodList.getInt(key));
            ItemStack rem = ItemHandlerHelper.insertItemStacked(new InvWrapper(player.getInventory()), give, false);
            if (!rem.isEmpty()) {
                Block.popResource(level, player.blockPosition(), rem);
            }
        }
        level.playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4f, 1.2f);
        nbt.putBoolean(UNPACKED, true);

        // 确认收货：托管记录结算，全额给店铺到账
        if (escrowed) {
            PlayerOrderEscrow.Entry e = PlayerOrderEscrow.get(server).settle(orderId);
            if (e != null) {
                ServerLevel shopLevel = levelByDim(server, e.machineDim);
                if (shopLevel == null) shopLevel = server.overworld();
                creditShop(shopLevel, BlockPos.of(e.machinePos), e.amount, e.tipAmount);
            }
        }

        ItemStackUtils.setTag(pkg, nbt);
        pkg.shrink(1);
    }

    /** 送达失败（送餐点被占 / 容器满 / 找不到送餐点）：半退结算，失效包裹掉落到世界。 */
    public static void handleDeliveryFailure(ServerLevel level, ItemStack pkg, Entity dropAt) {
        if (pkg == null || pkg.isEmpty()) return;
        CompoundTag t = ItemStackUtils.getTag(pkg);
        MinecraftServer server = level.getServer();
        String orderId = t != null ? t.getString(ModConstants.NBT_ORDER_ID) : "";
        if (server != null && !orderId.isEmpty()) {
            PlayerOrderEscrow.get(server).reportDeliveryFailure(server, orderId);
        }
        Block.popResource(level, dropAt.blockPosition(), pkg);
    }

    private static ServerLevel levelByDim(MinecraftServer server, String dim) {
        if (dim == null) return null;
        for (ServerLevel lvl : server.getAllLevels()) {
            if (lvl.dimension().location().toString().equals(dim)) return lvl;
        }
        return null;
    }

    private static String readOwnerName(OrderMachineBlockEntity mbe) {
        if (mbe == null) return null;
        try {
            Field f = findField(mbe.getClass(), "restaurantOwner");
            if (f != null) {
                Object v = f.get(mbe);
                return v instanceof String s ? s : null;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldError | NoSuchFieldException ignored) {}
        }
        return null;
    }

    private static void fail(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), false);
    }

    // ===== 店主右键打单机：自动领取离线期间的待领收益 =====
    @EventBusSubscriber(modid = "maid_restaurant_business", bus = EventBusSubscriber.Bus.GAME)
    public static class EventHandlers {
        @SubscribeEvent
        public static void onLogin(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer sp && sp.getServer() != null) {
                PlayerRefunds.get(sp.getServer()).grantOnLogin(sp);
            }
        }

        @SubscribeEvent
        public static void onRightClickMachine(PlayerInteractEvent.RightClickBlock event) {
            Level level = event.getLevel();
            if (level.isClientSide || !(level instanceof ServerLevel sl)) return;
            BlockPos pos = event.getPos();
            if (!(sl.getBlockEntity(pos) instanceof OrderMachineBlockEntity mbe)) return;
            Player player = event.getEntity();
            String owner = readOwnerName(mbe);
            if (owner == null || !owner.equals(player.getName().getString())) return;
            String key = ShopEarnings.key(sl.dimension().location().toString(), pos.asLong());
            int amount = ShopEarnings.get(sl).take(key);
            if (amount > 0) {
                CoinUtils.giveCoins(player, amount);
                player.displayClientMessage(
                        Component.translatable("message.business.order.claim", amount).withStyle(ChatFormatting.GOLD),
                        false);
            }
        }
    }
}
