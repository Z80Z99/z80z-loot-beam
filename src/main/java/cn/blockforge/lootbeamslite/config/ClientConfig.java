package cn.blockforge.lootbeamslite.config;

import net.minecraftforge.common.ForgeConfigSpec;
import java.util.List;

public final class ClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static ForgeConfigSpec.EnumValue<BeamStyle> BEAM_STYLE;
    public static ForgeConfigSpec.BooleanValue ENABLED, ENABLE_BEAM, ENABLE_GLOW, ALL_ITEMS, ONLY_RARE,
            ONLY_EQUIPMENT, COMMON_SHORTER_BEAM, SOLID_BEAM, REQUIRE_ON_GROUND, USE_NAME_COLOR,
            ENABLE_CUSTOM_COLOR, SHOW_NAME, NAME_ON_LOOK, TEXT_BORDER, STACK_COUNT,
            CROUCH_TOOLTIPS, ENABLE_SOUND, ENABLE_DYNAMIC;
    public static ForgeConfigSpec.IntValue FADE_IN_TICKS, HALF_ROUND_TICKS, TOOLTIP_X, TOOLTIP_Y, RARE_ORDINAL_MIN;
    public static ForgeConfigSpec.DoubleValue FADE_IN_DISTANCE, BEAM_RADIUS, BEAM_HEIGHT, BEAM_Y_OFFSET,
            BEAM_ALPHA, GLOW_RADIUS, MAX_DISTANCE, NAME_DISTANCE, LOOK_SENSITIVITY, NAME_TEXT_ALPHA,
            NAME_BACKGROUND_ALPHA, NAME_SCALE, NAME_Y_OFFSET, SOUND_VOLUME;
    public static ForgeConfigSpec.ConfigValue<List<? extends String>> WHITELIST_NAMES, WHITELIST_TAGS,
            WHITELIST_MODS, BLACKLIST_NAMES, BLACKLIST_TAGS, BLACKLIST_MODS, SOUND_BLACKLIST_NAMES,
            SOUND_BLACKLIST_TAGS, SOUND_BLACKLIST_MODS, EQUIPMENT_NAMES, EQUIPMENT_TAGS, EQUIPMENT_MODS,
            EQUIPMENT_BLACKLIST, COLOR_BY_NAME, COLOR_BY_TAG, COLOR_BY_MOD;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("战利品光束 Lite 客户端配置；所有说明均为中文。");
        b.push("general");
        ENABLED = bool(b, "enabled", true, "总开关");
        MAX_DISTANCE = decimal(b, "maxDistance", 96, 8, 256, "最大渲染距离（方块）");
        b.pop();
        b.push("beam");
        ENABLE_BEAM = bool(b, "enableBeam", true, "显示垂直光柱");
        BEAM_STYLE = b.comment("光柱样式：CLASSIC=经典光柱，LASER=纤细激光，SOFT=柔光雾柱，CONE=锥形尖塔，"
                        + "SPIRAL=螺旋光带，DOUBLE_HELIX=双螺旋，RINGS=浮空光环")
                .defineEnum("style", BeamStyle.CLASSIC);
        FADE_IN_TICKS = integer(b, "fadeInTicks", 10, 1, 100, "生成后的淡入时间（刻）");
        FADE_IN_DISTANCE = decimal(b, "fadeInDistance", 3, 0, 100, "贴近物品时的淡出距离；设为 0 可关闭近距离淡出");
        BEAM_RADIUS = decimal(b, "radius", 0.75, 0.01, 5, "光柱半径");
        BEAM_HEIGHT = decimal(b, "height", 3.2, 0, 10, "光柱高度");
        BEAM_Y_OFFSET = decimal(b, "yOffset", 0.12, -30, 30, "光柱纵向偏移");
        BEAM_ALPHA = decimal(b, "alpha", 0.95, 0, 1, "光柱透明度");
        COMMON_SHORTER_BEAM = bool(b, "commonShorterBeam", true, "普通品质使用较短光柱");
        SOLID_BEAM = bool(b, "solidBeam", true, "使用实心光柱");
        REQUIRE_ON_GROUND = bool(b, "requireOnGround", true, "只有落地物品才显示光效");
        USE_NAME_COLOR = bool(b, "useNameColor", true, "没有自定义颜色时采用物品名称颜色");
        b.pop();
        b.push("glow");
        ENABLE_GLOW = bool(b, "enableGlow", true, "显示物品脚下光晕");
        GLOW_RADIUS = decimal(b, "radius", 0.85, 0.00001, 1, "光晕半径");
        b.pop();
        b.push("dynamic");
        ENABLE_DYNAMIC = bool(b, "enabled", true, "启用呼吸/旋转动态效果");
        HALF_ROUND_TICKS = integer(b, "halfRoundTicks", 30, 1, 400, "完成半圈动画所需刻数");
        b.pop();
        b.push("filter");
        ALL_ITEMS = bool(b, "allItems", false, "所有物品都显示光效");
        ONLY_RARE = bool(b, "onlyRare", false, "仅稀有物品显示光效");
        ONLY_EQUIPMENT = bool(b, "onlyEquipment", true, "仅装备和白名单物品显示光效");
        WHITELIST_NAMES = list(b, "whitelistByName", List.of("minecraft:totem_of_undying", "minecraft:end_crystal", "minecraft:nether_star", "minecraft:wither_skeleton_skull", "minecraft:diamond"), "按物品 ID 白名单");
        WHITELIST_TAGS = list(b, "whitelistByTag", List.of(), "按标签白名单，例如 minecraft:logs（无需 #）");
        WHITELIST_MODS = list(b, "whitelistByModId", List.of(), "按模组 ID 白名单");
        BLACKLIST_NAMES = list(b, "blacklistByName", List.of(), "按物品 ID 黑名单");
        BLACKLIST_TAGS = list(b, "blacklistByTag", List.of(), "按标签黑名单");
        BLACKLIST_MODS = list(b, "blacklistByModId", List.of(), "按模组 ID 黑名单");
        b.pop();
        b.push("customColor");
        ENABLE_CUSTOM_COLOR = bool(b, "enabled", false, "启用自定义颜色覆盖");
        COLOR_BY_NAME = list(b, "byName", List.of(), "物品颜色，格式 minecraft:diamond=#00FFFF");
        COLOR_BY_TAG = list(b, "byTag", List.of(), "标签颜色，格式 minecraft:logs=#RRGGBB");
        COLOR_BY_MOD = list(b, "byModId", List.of(), "模组颜色，格式 modid=#RRGGBB");
        b.pop();
        b.push("nameTag");
        SHOW_NAME = bool(b, "enabled", true, "显示掉落物名称");
        NAME_ON_LOOK = bool(b, "onlyWhenLooking", true, "仅在准星看向物品时显示名称");
        TEXT_BORDER = bool(b, "textBorder", true, "名称文字带背景边框");
        STACK_COUNT = bool(b, "stackCount", true, "名称中显示堆叠数量");
        LOOK_SENSITIVITY = decimal(b, "lookSensitivity", 0.018, 0.001, 0.5, "准星判定灵敏度");
        NAME_TEXT_ALPHA = decimal(b, "textAlpha", 1, 0, 1, "文字透明度");
        NAME_BACKGROUND_ALPHA = decimal(b, "backgroundAlpha", 0.5, 0, 1, "背景透明度");
        NAME_SCALE = decimal(b, "scale", 1, 0.1, 5, "名称缩放");
        NAME_Y_OFFSET = decimal(b, "yOffset", 0.75, -5, 10, "名称纵向偏移");
        NAME_DISTANCE = decimal(b, "distance", 16, 2, 64, "名称显示距离");
        b.pop();
        b.push("tooltip");
        CROUCH_TOOLTIPS = bool(b, "renderOnCrouch", true, "潜行并看向物品时显示完整物品提示框");
        TOOLTIP_X = integer(b, "offsetXFromLeft", 10, 0, 10000, "提示框距屏幕左侧偏移");
        TOOLTIP_Y = integer(b, "offsetYFromBottom", 72, 0, 10000, "提示框距屏幕底部偏移");
        b.pop();
        b.push("sound");
        ENABLE_SOUND = bool(b, "enabled", true, "新掉落物出现时播放提示音");
        SOUND_VOLUME = decimal(b, "volume", 1, 0, 1, "提示音音量");
        SOUND_BLACKLIST_NAMES = list(b, "blacklistByName", List.of(), "声音物品 ID 黑名单");
        SOUND_BLACKLIST_TAGS = list(b, "blacklistByTag", List.of(), "声音标签黑名单");
        SOUND_BLACKLIST_MODS = list(b, "blacklistByModId", List.of(), "声音模组 ID 黑名单");
        b.pop();
        b.push("equipmentAndRarity");
        EQUIPMENT_NAMES = list(b, "equipmentByName", List.of(), "额外视为装备的物品 ID");
        EQUIPMENT_TAGS = list(b, "equipmentByTag", List.of("minecraft:swords", "minecraft:axes", "forge:tools/tridents", "c:spears", "c:tools/daggers", "c:tools/clubs", "c:tools/hammers"), "额外视为装备的标签");
        EQUIPMENT_MODS = list(b, "equipmentByModId", List.of(), "整个模组的物品视为装备");
        EQUIPMENT_BLACKLIST = list(b, "equipmentBlacklist", List.of(), "从装备判断中排除的物品 ID");
        RARE_ORDINAL_MIN = integer(b, "rareOrdinalMinimum", 2, 0, 16, "视为稀有的最低品质序号（原版稀有=2）");
        b.pop();
        SPEC = b.build();
    }
    private static ForgeConfigSpec.BooleanValue bool(ForgeConfigSpec.Builder b, String key, boolean value, String comment) { return b.comment(comment).define(key, value); }
    private static ForgeConfigSpec.IntValue integer(ForgeConfigSpec.Builder b, String key, int value, int min, int max, String comment) { return b.comment(comment).defineInRange(key, value, min, max); }
    private static ForgeConfigSpec.DoubleValue decimal(ForgeConfigSpec.Builder b, String key, double value, double min, double max, String comment) { return b.comment(comment).defineInRange(key, value, min, max); }
    private static ForgeConfigSpec.ConfigValue<List<? extends String>> list(ForgeConfigSpec.Builder b, String key, List<String> value, String comment) { return b.comment(comment).defineListAllowEmpty(List.of(key), value, o -> o instanceof String); }
    private ClientConfig() {}
}
