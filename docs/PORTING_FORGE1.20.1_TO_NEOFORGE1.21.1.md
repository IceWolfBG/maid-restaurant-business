# 移植经验：Forge 1.20.1 → NeoForge 1.21.1

> 本文记录本模组（maid_restaurant_business，包名 `com.icewolf.maidrestaurant.business`）
> 在两条代码线之间移植功能时实证过的 API 映射、踩坑与验证流程。
> **下次移植新功能前先读本文**，可直接照表替换，避免重复试错。
>
> - 1.20.1 工程：`forge-1.20.1/`（Java 17，ForgeGradle，`net.minecraftforge.*`）
> - 1.21.1 工程：`neoforge-1.21.1/`（Java 21，ModDevGradle，`net.neoforged.*`，NeoForge 21.1.x）
> - 两线功能必须对齐；**只改目标线，不动已发布的另一条线**。

---

## 0. 构建与部署（命令实证可用）

```bat
:: 1.21.1（JDK21，离线；务必用 cmd /c 包裹，重定向由 cmd 处理，勿用 PowerShell 的 2>&1）
cmd /c "set GRADLE_USER_HOME=D:\DoubaoWork\gradle_cache&& set JAVA_HOME=C:\Users\26529\AppData\Roaming\.minecraft\runtime\java-runtime-delta&& cd /d <工程目录>&& gradlew.bat build --no-daemon --offline > build_log.txt 2>&1"

:: 1.20.1 把 JAVA_HOME 换成 JDK17：C:\Users\26529\.jdks\ms-17.0.20.1
```

- 用 JDK17 编 1.21.1 会触发联网下载 JDK21 并超时失败，必须显式指 JAVA_HOME。
- 加 `--offline` 避免联网；依赖已缓存在 `D:\DoubaoWork\gradle_cache`。
- 产物：`build/libs/maid_restaurant_business-<mc>-<forge|neoforge>-<版本>.jar`。
- **jar 文件名必须纯 ASCII**：中文/全角字符会让 NeoForge securejarhandler 的 ModuleClassLoader
  在中文 Windows(GBK) 下运行时 `ClassNotFoundException`（外部类能加载、内部类/部分类找不到，
  侍者不打包不配送、日志刷几百次），极具迷惑性。中文显示名只放 mods.toml 的 displayName。
- 版本号只改 `gradle.properties` 的 `mod_version`；toml 用 `${mod_version}` / `${file.jarVersion}` 占位，
  由 build.gradle 的 processResources（含 filteringCharset=UTF-8）注入，禁止硬编码。

---

## 1. import / 包名替换

| Forge 1.20.1 | NeoForge 1.21.1 |
|---|---|
| `net.minecraftforge.items.IItemHandler` | `net.neoforged.neoforge.items.IItemHandler` |
| `net.minecraftforge.items.ItemHandlerHelper` | `net.neoforged.neoforge.items.ItemHandlerHelper` |
| `net.minecraftforge.items.IItemHandlerModifiable` | `net.neoforged.neoforge.items.IItemHandlerModifiable` |
| `ForgeRegistries.BLOCK/ITEM/...` | `BuiltInRegistries.BLOCK/ITEM/BLOCK_ENTITY_TYPE/MENU/SOUND_EVENT` |
| `RegistryObject<T>` + `DeferredRegister.create(ForgeRegistries.X, MODID)` | `DeferredHolder<T,T>` / `DeferredBlock<T>` / `DeferredItem<T>` + `DeferredRegister.create(BuiltInRegistries.X, MODID)` |
| `MinecraftForge.EVENT_BUS` | `net.neoforged.neoforge.common.NeoForge.EVENT_BUS` |
| `@Mod` 构造取 `FMLJavaModLoadingContext` | `@Mod` 构造直接注入 `IEventBus modEventBus` |

---

## 2. ItemStack / NBT（最容易错，必须用工程内工具类）

1.21 改用 DataComponents，**不要直接依赖 `stack.getTag()`/`setTag()` 的旧语义**，统一走工程内
`util/ItemStackUtils.java`：

- `ItemStackUtils.getTag(stack)` → **只读**，返回的是副本，改了不会回写。
- `ItemStackUtils.getOrCreateTag(stack)` → 即使已有 tag 也返回**副本**，用于「读-改-写」。
- `ItemStackUtils.setTag(stack, nbt)` → 改完必须用它回写，否则修改丢失。

典型「读-改-写」（订单 NBT 补 machine 信息）：
```java
CompoundTag nbt = ItemStackUtils.getOrCreateTag(order);
nbt.putLong(ModConstants.NBT_MACHINE_POS, pos.asLong());
nbt.putString(ModConstants.NBT_MACHINE_DIM, level.dimension().location().toString());
ItemStackUtils.setTag(order, nbt);
```
纯读取（如比对 OrderId）用 `ItemStackUtils.getTag(stack)`。

物品栈比较：`ItemStack.isSameItemSameComponents(a, b)`（不是旧的 equals/areItemStackTagEqual）。

---

## 3. BlockEntity 持久化（HolderLookup.Provider）

1.21.1 读写 NBT 都多一个注册表查找参数：

```java
@Override
protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.saveAdditional(tag, registries);
    tag.put("Order", this.order.save(registries));          // ItemStack 保存
}
@Override
protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
    super.loadAdditional(tag, registries);
    this.order = tag.contains("Order")
        ? ItemStack.parse(registries, tag.getCompound("Order")).orElse(ItemStack.EMPTY)
        : ItemStack.EMPTY;
}
```

- **是 `ItemStack.parse(registries, compound)` 返回 `Optional<ItemStack>`，没有 `parseOptional`**
  （parseOptional 在该 mappings 下返回裸 ItemStack，调 `.orElse` 会编译报错）。
- 同步包 `getUpdateTag` / `handleUpdateTag` 同样要带 `HolderLookup.Provider`。
- IItemHandler 序列化：`handler.serializeNBT(registries)` / `deserializeNBT(registries, tag)`。

---

## 4. 方块交互拆分（useWithoutItem / useItemOn）

1.20.1 一个 `use(level, player, hand)` 在 1.21.1 必须拆成两个：

```java
@Override // 空手右键
public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) { ... return InteractionResult.SUCCESS; }

@Override // 手持物品右键
public ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) { ... return ItemInteractionResult.SUCCESS; }
```

- 「手持订单存入、空手取出」这类逻辑：在 useItemOn 里处理存入（返回 CONSUME/SUCCESS），
  useWithoutItem 处理取出。
- 取玩家当前快捷栏槽位：**1.21.1 用 public 字段 `inventory.selected`**；
  `inventory.getSelectedSlot()` 是 1.21.2+ 才有的方法，1.21.1 编译报找不到符号。
  取当前手持物品栈 `inventory.getSelected()` 仍可用。
- 创造模式也要消耗物品时，不能依赖 `player.getAbilities().instabuild` 跳过，照常 `shrink`。

### 方块基类 / codecs
继承 `BaseEntityBlock`（或 EntityBlock 实现）时需要：
```java
public static final MapCodec<OrderClipBlock> CODEC = MapCodec.unit(OrderClipBlock::new);
@Override public MapCodec<? extends Block> codec() { return CODEC; }
```
方块状态用 `createBlockStateDefinition` + `EnumProperty<Direction>` / `BooleanProperty`，
`BlockState.setValue(...)`、`state.getValue(PROP)`。

### 掉落
1.21 服务端资源目录是单数 `data/<ns>/recipe/`、`loot_table/`。若不写 loot_table，
需要在 `onRemove(...)` 里手动 `Block.popResource(level, pos, stack)` 掉落内容，再 `super.onRemove`。

---

## 5. Capability 注册（1.21.1 NeoForge）

在主类构造里 `modEventBus.addListener(this::registerCapabilities)`：
```java
private void registerCapabilities(RegisterCapabilitiesEvent event) {
    event.registerBlock(
        Capabilities.ItemHandler.BLOCK,
        (level, pos, state, be, side) -> be instanceof OrderClipBlockEntity clip ? clip.getViewHandler() : null,
        ModBlocks.ORDER_CLIP.get());
}
```
- 只读视图可返回一个只实现 getSlots/getStackInSlot/getSlotLimit、插入抽取都拒绝的 IItemHandler，
  供漏斗/其它模组“看见”夹上的订单但不能改。
- 通用取方块物品处理器的兜底顺序：TakeoutBox 反射 inventory →
  `level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side)` → BE `instanceof IItemHandler`
  → 反射 `getItems()`。

---

## 6. 客户端

- 透明/薄板模型渲染层：
```java
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
// onClientSetup 开头：
event.enqueueWork(() -> ItemBlockRenderTypes.setRenderLayer(ModBlocks.ORDER_CLIP.get(), RenderType.cutout()));
```
- Screen 注册：`event.enqueueWork(() -> MenuScreens.register(ModMenuTypes.X.get(), XScreen::new));`
  （工程内对部分 Screen 用反射注册以规避类加载，保持现状即可）。
- 模型 JSON 里也可写 `"render_type": "minecraft:cutout"` 双保险。
- 资源引用纹理**不带 .png 后缀**；1.21.1 pack_format 34。

---

## 7. 时间时钟（★任务超时是否生效全靠这个）

`TaskManager` 单例的 `currentTick` 来自 `BusinessManager.tickCounter`（每个服务器 tick 在维度循环外自增一次），
任务的 `createTime / assignTime / lastHeartbeat` 以及所有超时判断（hardStuck、heartbeat 超时、
ASSIGNED 超时、厨具占用超时）**全部基于这个 tickCounter**。

因此：
- `MaidUtils.startTask(maid, machine, type, tick)` 的 tick 参数、
  `TaskManager.heartbeat(uuid, tick)` 的 tick 参数，**必须传 `manager.getTickCounter()`**，
  与 TaskManager 同源。传 `level.getGameTime()` 会让 lastHeartbeat 跳到世界时间（远大于 tickCounter），
  导致 `currentTick - lastHeartbeat` 恒为大负数、heartbeat 级超时/重试失效。
- **Bridge 内部自管的 PersistentData 时间戳**（如 F_START、TAG_START、等待 WAIT_SINCE）
  用 `level.getGameTime()` 即可——只要“记录”和“比较”都用同一个时钟、自洽就行。
- `createTask(type, target, machine)` 不接收时间参数，内部自己取 currentTick。

---

## 8. 仍保留的常用 1.21 API（与 1.20.1 基本同名）

- 实体 scoreboardTag：`entity.getTags()` / `addTag(s)` / `removeTag(s)`。
- 范围取实体：`level.getEntitiesOfClass(LivingEntity.class, new AABB(...))`。
- 区块级方块实体枚举（避免全局遍历）：
  `LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);`（未加载返回 null，跳过）
  `for (BlockPos p : chunk.getBlockEntitiesPos()) { BlockEntity be = chunk.getBlockEntity(p); }`
- 寻路/记忆：`maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(pos, 0.4f, 1));`
  `maid.getNavigation().stop();`
- 动画：`maid.swing(InteractionHand.OFF_HAND);`（本模组统一副手）。
- 掉落：`Block.popResource(level, pos, stack);`
- 音效：`level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.8f, 1f);`
  接待成功可用 `SoundEvents.EXPERIENCE_ORB_PICKUP`。
- CompoundTag：`putUUID/getUUID`、`putLong/getLong`、`putString/getString`、`contains`。

---

## 9. 下单了 OTC（neoforge-1.21.1）关键 API 签名（已逐行核对）

源码根：`D:\DoubaoWork\开发所需mod源码\OrdertoCook-src\OrdertoCook-main\neoforge-1.21.1\`
包前缀 `cn.breezeth.ordertocook`。

- `block/entity/OrderMachineBlockEntity`
  - `public void onOrderAccepted()`（**无参**；抽单后调用，清空同批候选并复位 IDLE/FRAME0，
    实现“女仆和玩家不能在同一刷新各接一张”）
  - `public int ensureMachineId(ServerLevel)`（>0 时把 id 写进订单 NBT）
  - `public static List<Item> getBoundBoardMenuFoods(ServerLevel, BlockPos machinePos)`
- `block/entity/TakeoutBoxBlockEntity`
  - `private final NonNullList<ItemStack> inventory = NonNullList.withSize(13, ItemStack.EMPTY)`，
    槽 0 是订单槽；反射拿 `getItems()` 后 `((List)inventory).set(0, stack)` 放入订单。
- `core/OrderGenerator`
  - `static ItemStack generateWalkInOrder(ServerLevel, BlockPos pos, int level, long spawnTick, String customerName, List<Item> menuFoods)`（6 参；5 参重载内部传空 list）
- `core/OrderNpcManager`
  - 常量 `TAG_WALKIN="otc_walkin"`、`TAG_WALKIN_SPAWN_TIME="otc_walkin_spawn_time:"`、
    `TAG_WALKIN_SPAWN_SYSTEM_TIME="otc_walkin_spawn_sys:"`、`TAG_WALKIN_MACHINE_POS_PREFIX="otc_walkin_machine_pos:"`
  - `static void tagNpc(Player player, String orderId, long expiryTick, long expirySys, LivingEntity npc)`
    （5 参，**player 可传 null**，已验证安全）
  - `static void changeToNormalTeam(ServerLevel, LivingEntity)`
- `core/WalkInNpcManager`
  - `static void changeToNormalTeam(ServerLevel, LivingEntity)`（委托 OrderNpcManager）
  - walk-in 生成时打的 tag：TAG_WALKIN / SPAWN_TIME+gameTime / SPAWN_SYSTEM_TIME+毫秒 / MACHINE_POS+asLong
- `core/ModConstants` NBT 键：`OrderId`、`ExpiryTick`、`ExpiryTime`、`order_machine.pos_long`、
  `order_machine.dimension`、`order_machine.id`、`Delivery`、`delivery_pos`、`Order_Type` 等。
- OTC 字面量（其内部 private，只能照抄字符串）：`otc_npc`、`otc_walkin_interacted`、`otc_level:`。
- 合规：只用 public API 或反射，**不复制 OTC 源码、不打包对方 class**。

---

## 10. 资源文件移植清单（编译不校验，必须单独核对）

新增一个方块要齐：
- `assets/<ns>/blockstates/<name>.json`（facing × 状态变体，注意 y 旋转 90/180/270）
- `assets/<ns>/models/block/<name>.json`（+ 带状态变体，如 `<name>_filled.json`）
- `assets/<ns>/models/item/<name>.json`（`{"parent":"<ns>:block/<name>"}`）
- `assets/<ns>/textures/block/<name>*.png`
- `assets/<ns>/lang/zh_cn.json`、`en_us.json`（译名 key）
- `data/<ns>/recipe/<name>.json`（**1.21.1 单数 recipe**；1.20.1 是复数 recipes）
- 注册四处：ModBlocks / ModItems(BlockItem) / ModBlockEntities(若有 BE) / ModCreativeTabs
- 无 loot_table 时 onRemove 手动掉落。

**编译只检查 Java，不检查资源是否缺失/JSON 是否合法/纹理是否对得上**，
移植后必须：解包 jar 确认上述条目都在、JSON 合法、模型纹理键名一致。

---

## 11. 发布前验证流程（每次照做）

1. `gradlew build --offline` BUILD SUCCESSFUL（deprecation/unchecked 提示可忽略）。
2. 解包/列 jar 条目：新增 class（含内部类）、blockstate/model/texture/recipe/lang 都在。
3. 部署到测试 mods 目录，**核对 jar 修改时间与大小**（中文路径曾导致部署失败）。
4. 进游戏验收功能；验收期临时日志确认完即删（成功路径不留 info/debug/System.out，
   只保留 catch 里的 error 与吞单/超时兜底的低频 warn）。
5. 最终 jar 归档到 `releases/<loader>/`（该目录 gitignore，不进 git）。
6. `git add -A` → commit → `git tag -a release/<目录名>-<版本>` → push main 与 tag。
7. GitHub 建 Release 关联 tag、上传 jar。
8. **下载 Release 的 jar，与本地归档比 SHA256/大小/版本号**，一致才算发完（三重保险）。
9. tag 命名固定 `release/neoforge-1.21.1-<版本>`；**已存在的 tag/release 不要覆盖**，
   需要重排先与人确认（旧快照降 `-betaN`，新版本占正式号）。

---

## 12. 本次挂单夹移植涉及的类（备查）

- 新增：`block/OrderClipBlock`、`block/entity/OrderClipBlockEntity`、
  `core/OrderFetchBridge`（打单机/挂单夹订单 → 厨师两段寻路取单入台）、
  `core/WalkInGreetBridge`（侍者接待 walk-in 顾客 → 订单进背包 → 夹到挂单夹）。
- 改动：`MaidRestaurantBusiness`（注册接线 + capability）、`client/ClientSetup`（cutout）、
  `core/TaskManager`（TYPE_GREET/TYPE_FETCH_ORDER、挂单夹缓存 ensureClipCaches/getCachedEmptyClips/
  getCachedClipsWithOrder、clearAll）、`core/OrderBridge`（删除旧瞬移接单、保留操作台扫描/成品检测/
  生成顾客、刷新时 invalidate 取单缓存）、`core/BusinessManager`（每 10tick 调度 WalkIn+OrderFetch）、
  registry 四处、资源与合成表。
- 设计约束：一切检索以激活打单机为中心并按机隔离（水平 24/垂直 8），禁全局遍历、禁 (0,0,0) 中心；
  订单先进女仆背包/失败掉落防吞单；超时保护仅最后保底；无空夹/接不了单不弹错误气泡；
  接待气泡文案待后续统一讨论。
