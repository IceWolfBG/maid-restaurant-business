package com.icewolf.maidrestaurant.business.config;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Set;

/**
 * 多槽厨具「单任务批量烹饪」配置。
 *
 * <p>背景：蒸笼/烤箱/烤面包机属于多槽并行厨具（一个方块有多个烹饪槽位），搅拌机属于单槽连续厨具，
 * 女仆餐厅本体让一个 {@code CookRequest}（{@code requested=N}）由对应 cookTick 的多槽循环
 * （配合 extraData 的 "left" 计数）一次填满多个槽位并行蒸/烤、或连续制作多份，女仆只需到场一次。
 * 但这些厨具的配方 result 只有 1 个，营业中原先按「每任务一份」发布，多槽位从未被填满、
 * 需求 N 份要发 N 个单份任务，女仆反复寻路取料，效率很低。</p>
 *
 * <p>本配置放开单任务份数上限：白名单内厨具发布任务时，食材贪心计算的 maxCount 取这里的批量上限，
 * 最终 {@code requested = min(该厨师真实可做份数, 剩余需求, 批量上限)}。
 * 单槽串行厨具（汤锅/煎锅/农夫厨锅/沉浸火炉等）不在白名单，上限恒为 1，行为完全不变；
 * 煎锅/汤锅那种「一锅多份」由 _count_N 配方的 result 数量表达，走原有最高配方产出逻辑，不在此列。</p>
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "maid_restaurant_business", bus = net.neoforged.fml.common.EventBusSubscriber.Bus.MOD)
public class BatchCookingConfig {
    /** 森罗物语：厨房·蒸笼（整层 8 槽 / 半层 4 槽，可向上叠 4 层）。 */
    public static final String UID_STEAMER = "SteamerCookTask";
    /** 烘焙坊·烤箱（6 槽并行）。 */
    public static final String UID_OVEN = "OvenCookTask";
    /** 烘焙坊·烤面包机（2 槽并行，配方类型为原版营火烹饪）。 */
    public static final String UID_TOASTER = "ToasterCookTask";
    /** 烘焙坊·搅拌机（单杯连续制作，不并行，但一个任务可连做数杯）。 */
    public static final String UID_BLENDER = "BlenderCookTask";

    private static final Set<String> BATCH_UIDS = Set.of(UID_STEAMER, UID_OVEN, UID_TOASTER, UID_BLENDER);

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.IntValue STEAMER_CAP;
    public static final ModConfigSpec.IntValue OVEN_CAP;
    public static final ModConfigSpec.IntValue TOASTER_CAP;
    public static final ModConfigSpec.IntValue BLENDER_CAP;

    // 缓存值（配置加载/重载时从 SPEC 同步，运行期直接读静态字段，避免每 tick 查配置对象）
    public static boolean enabled = true;
    public static int steamerCap = 8;
    public static int ovenCap = 6;
    public static int toasterCap = 2;
    public static int blenderCap = 4;

    static {
        BUILDER.push("batch_cooking");
        ENABLED = BUILDER.comment(
                "多槽厨具批量烹饪总开关。",
                "true=一个烹饪任务可让女仆一次填满蒸笼/烤箱等多个槽位并行制作多份（或让搅拌机连续做多杯）；",
                "false=所有厨具恢复为逐份发布任务。")
                .define("enabled", true);
        STEAMER_CAP = BUILDER.comment(
                "蒸笼单个烹饪任务的最大份数。蒸笼整层 8 槽 / 半层 4 槽、最多叠 4 层；",
                "默认 8 = 一次填满一整层并行蒸，超出部分在同任务内分批蒸；设为 1 即关闭蒸笼批量。")
                .defineInRange("steamerBatchSize", 8, 1, 32);
        OVEN_CAP = BUILDER.comment(
                "烤箱单个烹饪任务的最大份数。烘焙坊烤箱为 6 个槽位，默认 6 = 一次填满。")
                .defineInRange("ovenBatchSize", 6, 1, 32);
        TOASTER_CAP = BUILDER.comment(
                "烤面包机单个烹饪任务的最大份数。烘焙坊烤面包机为 2 个槽位，默认 2。")
                .defineInRange("toasterBatchSize", 2, 1, 32);
        BLENDER_CAP = BUILDER.comment(
                "搅拌机单个烹饪任务的最大连续份数。搅拌机单杯制作、不并行，",
                "一个任务连续做完 N 杯可省去反复派单寻路，默认 4。")
                .defineInRange("blenderBatchSize", 4, 1, 32);
        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    @SubscribeEvent
    static void onLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SPEC) {
            loadValues();
        }
    }

    @SubscribeEvent
    static void onReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == SPEC) {
            loadValues();
        }
    }

    private static void loadValues() {
        enabled = ENABLED.get();
        steamerCap = STEAMER_CAP.get();
        ovenCap = OVEN_CAP.get();
        toasterCap = TOASTER_CAP.get();
        blenderCap = BLENDER_CAP.get();
    }

    /** 该厨具任务 UID 是否支持「单任务多份」（多槽并行或单槽连续）。 */
    public static boolean isBatchUid(String uid) {
        return uid != null && BATCH_UIDS.contains(uid);
    }

    /**
     * 返回该厨具单个烹饪任务允许的最大份数（即食材贪心计算的 maxCount）。
     * 非白名单厨具、总开关关闭、UID 为空时恒返回 1，保持逐份发布的原行为。
     */
    public static int getBatchCap(String uid) {
        if (!enabled || uid == null) {
            return 1;
        }
        switch (uid) {
            case UID_STEAMER:
                return Math.max(1, steamerCap);
            case UID_OVEN:
                return Math.max(1, ovenCap);
            case UID_TOASTER:
                return Math.max(1, toasterCap);
            case UID_BLENDER:
                return Math.max(1, blenderCap);
            default:
                return 1;
        }
    }
}
