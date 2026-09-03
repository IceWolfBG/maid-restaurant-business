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
