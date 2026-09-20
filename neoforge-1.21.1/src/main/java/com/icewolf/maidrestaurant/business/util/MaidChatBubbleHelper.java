package com.icewolf.maidrestaurant.business.util;

import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.IChatBubbleData;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.implement.TextChatBubbleData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 女仆对话气泡工具类
 * 用于在女仆工作的关键节点显示可爱的对话气泡
 */
public class MaidChatBubbleHelper {
    private static final Random RANDOM = new Random();
    
    // 记录每个女仆的气泡状态（使用ConcurrentHashMap确保多人模式线程安全）
    private static final Map<UUID, Long> lastBubbleTime = new ConcurrentHashMap<>();
    private static final Map<UUID, String> lastBubbleType = new ConcurrentHashMap<>();
    
    // ==================== 厨师女仆气泡 ====================
    
    /**
     * 厨师开始烹饪
     */
    public static void chefStartCooking(EntityMaid maid) {
        showBubble(maid, "chef_start",
            new String[]{
                "让我来做这道菜吧~(≧▽≦)",
                "开始烹饪啦~",
                "今天也要好好做饭哦",
                "做饭时间到~"
            },
            60);
    }
    
    /**
     * 厨师烹饪完成
     */
    public static void chefCookingDone(EntityMaid maid) {
        showBubble(maid, "chef_done",
            new String[]{
                "做好啦！(๑•̀ㅂ•́)و✧",
                "新鲜出炉~",
                "请品尝一下吧~",
                "完成~"
            },
            60);
    }
    
    /**
     * 厨师去拿食材
     */
    public static void chefGetIngredients(EntityMaid maid) {
        showBubble(maid, "chef_get_ingredients",
            new String[]{
                "先去拿点食材~",
                "准备材料中...",
                "食材在哪里呢~",
                "去拿材料啦"
            },
            40);
    }
    
    /**
     * 厨师备菜完成
     */
    public static void chefPrepDone(EntityMaid maid) {
        showBubble(maid, "chef_prep_done",
            new String[]{
                "食材准备好啦~(｡･ω･｡)",
                "准备完毕~",
                "可以开始做了"
            },
            40);
    }
    
    /**
     * 厨师食材不足
     */
    public static void chefNoIngredients(EntityMaid maid) {
        showBubble(maid, "chef_no_ingredients",
            new String[]{
                "食材不够了...(；′⌒`)",
                "需要更多材料呢...",
                "这个...材料不太够呀",
                "材料不足..."
            },
            60);
    }
    
    /**
     * 厨师没有厨具（根本没有这种厨具）
     */
    public static void chefNoDeviceAtAll(EntityMaid maid, String deviceName) {
        showCustomBubble(maid, "chef_no_device_all_" + deviceName,
            new String[]{
                "好像没有" + deviceName + "呢..",
                "需要一个" + deviceName + "才行呢..",
                "没有" + deviceName + "做不了呢...",
                deviceName + "在哪里呢，找不到"
            },
            80);
    }

    /**
     * 厨师完全没有所需厨具（非四类设备）。
     * deviceName 为可翻译组件（来自女仆餐厅 getIcon().getHoverName()），随客户端语言显示，
     * 如中文环境下显示"烤炉"，不再硬编码猜测。
     */
    public static void chefNoDeviceAtAll(EntityMaid maid, Component deviceName) {
        Component d = (deviceName != null) ? deviceName : Component.literal("所需厨具");
        Component[] msgs = new Component[]{
            Component.literal("好像没有").append(d).append("呢.."),
            Component.literal("需要一个").append(d).append("才行呢.."),
            Component.literal("没有").append(d).append("做不了呢..."),
            d.copy().append("在哪里呢，找不到")
        };
        showBubbleComponent(maid, "chef_no_device_all_comp", msgs, 80);
    }

    /**
     * 与 showBubble 相同，但消息为可翻译 Component[]（设备名随客户端语言翻译）。
     */
    private static void showBubbleComponent(EntityMaid maid, String type, Component[] messages, int duration) {
        if (maid == null || maid.level().isClientSide) {
            return;
        }
        UUID uuid = maid.getUUID();
        long now = maid.level().getGameTime();
        long cooldown = getBubbleCooldown();
        Long lastTime = lastBubbleTime.get(uuid);
        if (lastTime != null && now - lastTime < cooldown) {
            return;
        }
        String lastType = lastBubbleType.get(uuid);
        if (type.equals(lastType)) {
            return;
        }
        Component text = messages[RANDOM.nextInt(messages.length)];
        try {
            TextChatBubbleData bubbleData = TextChatBubbleData.create(
                duration, text, IChatBubbleData.TYPE_2, IChatBubbleData.DEFAULT_PRIORITY);
            Object manager = maid.getChatBubbleManager();
            if (manager == null) {
                return;
            }
            maid.getChatBubbleManager().addChatBubble(bubbleData);
            lastBubbleTime.put(uuid, now);
            lastBubbleType.put(uuid, type);
        } catch (Exception e) {
            com.icewolf.maidrestaurant.business.MaidRestaurantBusiness.LOGGER.error("[气泡] 添加失败 maid={} type={}", maid.getName().getString(), type, e);
        }
    }

    /**
     * 厨师没有空闲厨具（厨具都被占用了）
     */
    public static void chefNoDeviceBusy(EntityMaid maid, String deviceName) {
        showCustomBubble(maid, "chef_no_device_busy_" + deviceName,
            new String[]{
                deviceName + "都被占用了呢...",
                "没有空闲的" + deviceName + "了呢...",
                deviceName + "都在忙呢，等一下吧~",
                "想要用" + deviceName + "...但是都在用"
            },
            80);
    }

    /**
     * 厨师备菜时操作台成品格（及附近冰箱）都满了，做好的菜放不下
     */
    public static void chefCounterFull(EntityMaid maid) {
        showBubble(maid, "chef_counter_full",
            new String[]{
                "做好的菜没地方放啦~ 主人来收一下好不好？",
                "这边堆得满满的…主人帮我腾点地方嘛~",
                "菜做好了，可是放不下了 (｡•́︿•̀｡)"
            },
            80);
    }

    /**
     * 厨师空闲
     */
    public static void chefIdle(EntityMaid maid) {
        showBubble(maid, "chef_idle",
            new String[]{
                "今天生意真好~(≧∇≦)ﾉ",
                "休息一下...zzZ",
                "有订单叫我哦~",
                "好无聊呀..."
            },
            100);
    }
    
    // ==================== 侍者女仆气泡 ====================
    
    /**
     * 侍者开始送餐
     */
    public static void waiterStartDelivery(EntityMaid maid) {
        showBubble(maid, "waiter_delivery",
            new String[]{
                "您的餐来了~(๑•̀ㅂ•́)و✧",
                "送餐啦！",
                "久等了~(｡･ω･｡)",
                "来啦来啦~"
            },
            60);
    }
    
    /**
     * 侍者送餐完成
     */
    public static void waiterDeliveryDone(EntityMaid maid) {
        showBubble(maid, "waiter_delivery_done",
            new String[]{
                "请慢用~(≧▽≦)",
                "祝您用餐愉快~",
                "有需要再叫我哦~",
                "完成~"
            },
            60);
    }
    
    /**
     * 侍者打包食物
     */
    public static void waiterPacking(EntityMaid maid) {
        showBubble(maid, "waiter_packing",
            new String[]{
                "打包中...(｡･ω･｡)",
                "马上就好~",
                "包装一下啦",
                "打包打包~"
            },
            40);
    }
    
    /**
     * 侍者打包完成
     */
    public static void waiterPackingDone(EntityMaid maid) {
        showBubble(maid, "waiter_packing_done",
            new String[]{
                "打包好啦！(๑•̀ㅂ•́)و✧",
                "包装完成~",
                "可以送餐啦"
            },
            40);
    }
    
    /**
     * 侍者收盘子
     */
    public static void waiterCollectPlate(EntityMaid maid) {
        showBubble(maid, "waiter_collect",
            new String[]{
                "我来收拾一下~",
                "盘子收走啦~",
                "清理一下哦",
                "收盘子啦"
            },
            40);
    }
    
    /**
     * 侍者洗碗
     */
    public static void waiterWashing(EntityMaid maid) {
        showBubble(maid, "waiter_washing",
            new String[]{
                "洗刷刷~(≧∇≦)ﾉ",
                "盘子洗干净啦~",
                "闪闪发亮~",
                "洗碗中..."
            },
            60);
    }
    
    /**
     * 侍者空闲
     */
    public static void waiterIdle(EntityMaid maid) {
        showBubble(maid, "waiter_idle",
            new String[]{
                "有什么需要帮忙的吗？(｡･ω･｡)",
                "随时待命~",
                "今天真热闹呀~",
                "站着有点累..."
            },
            100);
    }

    /**
     * 侍者开始迎接到店（walk-in）顾客
     */
    public static void waiterGreetCustomer(EntityMaid maid) {
        showBubble(maid, "waiter_greet",
            new String[]{
                "欢迎光临♪ 今天想吃点什么呢？",
                "欢迎光临~ 客人这边请。",
                "来客人啦，我去招呼一下！",
                "欢迎光临~ 要点什么吗？"
            },
            60);
    }

    // ==================== 错误提示气泡 ====================
    
    /**
     * 找不到顾客
     */
    public static void waiterCustomerNotFound(EntityMaid maid) {
        forceShowBubble(maid, "waiter_customer_not_found",
            new String[]{
                "咦？客人去哪里了？",
                "找不到客人了呢...(｡•́︿•̀｡)",
                "客人好像走掉了",
                "嗯？客人不在呀"
            },
            60);
    }
    
    /**
     * 找不到盘子架
     */
    public static void waiterPlateRackNotFound(EntityMaid maid) {
        forceShowBubble(maid, "waiter_plate_rack_not_found",
            new String[]{
                "盘子架在哪里呢？",
                "找不到放盘子的地方了...(；´д｀)",
                "嗯？盘子架不见了？",
                "干净盘子放哪里好呢"
            },
            60);
    }
    
    /**
     * 速递站已满
     */
    public static void waiterStationFull(EntityMaid maid) {
        forceShowBubble(maid, "waiter_station_full",
            new String[]{
                "速递站满了呀...",
                "酒狐速递站放不下了",
                "嗯？速递站都满了？(｡･ω･｡)",
                "外卖袋放不进去了呢"
            },
            60);
    }
    
    public static void waiterRestocking(EntityMaid maid) {
        showBubble(maid, "waiter_restocking",
            new String[]{
                "货架的皮革不够啦，我去补一些~",
                "去拿点包装用的皮革哦~",
                "外卖包装要补货啦 (｡･ω･｡)",
                "我来给货架添些皮革~"
            },
            60);
    }

    public static void waiterRestockNoLeather(EntityMaid maid) {
        forceShowBubble(maid, "waiter_restock_no_leather",
            new String[]{
                "包装用的皮革没有了呢…",
                "找不到皮革，外卖要包不了啦 (｡•́︿•̀｡)",
                "店里没有皮革了，主人补一点吧~",
                "哎？皮革都用光了？"
            },
            60);
    }

    // ==================== 核心方法 ====================
    
    /**
     * 显示气泡
     * @param maid 女仆实体
     * @param type 气泡类型（用于去重）
     * @param messages 可选消息列表
     * @param duration 持续时间（tick）
     */
    private static void showBubble(EntityMaid maid, String type, String[] messages, int duration) {
        if (maid == null) {
            com.icewolf.maidrestaurant.business.MaidRestaurantBusiness.LOGGER.warn("[气泡] maid为null，跳过显示 type={}", type);
            return;
        }
        if (maid.level().isClientSide) {
            com.icewolf.maidrestaurant.business.MaidRestaurantBusiness.LOGGER.warn("[气泡] 客户端侧，跳过显示 maid={} type={}", maid.getName().getString(), type);
            return;
        }
        
        UUID uuid = maid.getUUID();
        long now = maid.level().getGameTime();
        String maidName = maid.getName().getString();
        
        // 获取冷却时间（从配置读取，默认200tick=10秒）
        long cooldown = getBubbleCooldown();
        
        // 检查全局冷却
        Long lastTime = lastBubbleTime.get(uuid);
        if (lastTime != null && now - lastTime < cooldown) {
            return;
        }
        
        // 同类气泡去重（状态没变就不显示）
        String lastType = lastBubbleType.get(uuid);
        if (type.equals(lastType)) {
            return;
        }
        
        // 随机选一条消息
        String msg = messages[RANDOM.nextInt(messages.length)];
        Component text = Component.literal(msg);
        
        try {
            // 显示气泡（使用create方法自定义持续时间）
            TextChatBubbleData bubbleData = TextChatBubbleData.create(
                duration, 
                text, 
                IChatBubbleData.TYPE_2, 
                IChatBubbleData.DEFAULT_PRIORITY
            );
            
            // 检查ChatBubbleManager是否可用
            Object manager = maid.getChatBubbleManager();
            if (manager == null) {
                com.icewolf.maidrestaurant.business.MaidRestaurantBusiness.LOGGER.error("[气泡] ChatBubbleManager为null maid={}", maidName);
                return;
            }
            
            long key = maid.getChatBubbleManager().addChatBubble(bubbleData);
            
            // 记录状态
            lastBubbleTime.put(uuid, now);
            lastBubbleType.put(uuid, type);
        } catch (Exception e) {
            com.icewolf.maidrestaurant.business.MaidRestaurantBusiness.LOGGER.error("[气泡] 添加失败 maid={} type={} error={}", maidName, type, e.toString(), e);
        }
    }
    
    /**
     * 显示自定义气泡（公共方法，用于外部调用）
     * @param maid 女仆实体
     * @param type 气泡类型（用于去重）
     * @param messages 可选消息列表
     * @param duration 持续时间（tick）
     */
    public static void showCustomBubble(EntityMaid maid, String type, String[] messages, int duration) {
        showBubble(maid, type, messages, duration);
    }
    
    
    /**
     * 强制显示气泡（忽略全局冷却，用于错误提示）
     * @param maid 女仆实体
     * @param type 气泡类型（用于去重）
     * @param messages 可选消息列表
     * @param duration 持续时间（tick）
     */
    private static void forceShowBubble(EntityMaid maid, String type, String[] messages, int duration) {
        if (maid == null) {
            com.icewolf.maidrestaurant.business.MaidRestaurantBusiness.LOGGER.warn("[气泡] maid为null，跳过显示 type={}", type);
            return;
        }
        if (maid.level().isClientSide) {
            com.icewolf.maidrestaurant.business.MaidRestaurantBusiness.LOGGER.warn("[气泡] 客户端侧，跳过显示 maid={} type={}", maid.getName().getString(), type);
            return;
        }
        
        UUID uuid = maid.getUUID();
        long now = maid.level().getGameTime();
        String maidName = maid.getName().getString();
        
        // 同类气泡去重（状态没变就不显示）
        String lastType = lastBubbleType.get(uuid);
        if (type.equals(lastType)) {
            return;
        }
        
        // 随机选一条消息
        String msg = messages[RANDOM.nextInt(messages.length)];
        Component text = Component.literal(msg);
        
        try {
            // 显示气泡（使用create方法自定义持续时间）
            TextChatBubbleData bubbleData = TextChatBubbleData.create(
                duration, 
                text, 
                IChatBubbleData.TYPE_2, 
                IChatBubbleData.DEFAULT_PRIORITY
            );
            
            // 检查ChatBubbleManager是否可用
            Object manager = maid.getChatBubbleManager();
            if (manager == null) {
                com.icewolf.maidrestaurant.business.MaidRestaurantBusiness.LOGGER.error("[气泡] ChatBubbleManager为null maid={}", maidName);
                return;
            }
            
            long key = maid.getChatBubbleManager().addChatBubble(bubbleData);
            
            // 记录状态
            lastBubbleTime.put(uuid, now);
            lastBubbleType.put(uuid, type);
        } catch (Exception e) {
            com.icewolf.maidrestaurant.business.MaidRestaurantBusiness.LOGGER.error("[气泡] 添加失败 maid={} type={} error={}", maidName, type, e.toString(), e);
        }
    }
    
    /**
     * 状态改变时调用，清除同类气泡去重标记
     * 这样女仆切换任务时可以重新显示气泡
     */
    public static void onStateChanged(EntityMaid maid) {
        if (maid != null) {
            lastBubbleType.remove(maid.getUUID());
        }
    }
    
    /**
     * 获取气泡冷却时间（tick）
     */
    private static long getBubbleCooldown() {
        try {
            // 从配置读取，默认200tick（10秒）
            return com.icewolf.maidrestaurant.business.config.BusinessConfig.getBubbleCooldown();
        } catch (Exception e) {
            return 200L;
        }
    }
    
    /**
     * 清理已卸载女仆的数据（避免内存泄漏）
     */
    public static void cleanup(UUID uuid) {
        lastBubbleTime.remove(uuid);
        lastBubbleType.remove(uuid);
    }
}
