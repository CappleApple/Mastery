package com.cappleapple.mastery.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server policy; datapacks define progression and upgrades for existing spells. */
public final class MasteryConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue ACTIVE_CAPACITY;
    public static final ModConfigSpec.IntValue DEFAULT_WORLD_TIER;
    public static final ModConfigSpec.BooleanValue CREATIVE_XP;
    static {
        var b = new ModConfigSpec.Builder();
        ACTIVE_CAPACITY = b.comment("Extra assignable spells across contexts, in addition to equipped Iron's spell capacity and progression bonuses.").defineInRange("baseActiveCapacity", 0, 0, 256);
        DEFAULT_WORLD_TIER = b.comment("-1 ignores tier caps when no world-tier provider is registered; 0 starts at tier zero.").defineInRange("defaultWorldTier", -1, -1, 1000000);
        CREATIVE_XP = b.comment("Award usage XP to creative-mode players.").define("creativeUsageXp", false);
        SPEC = b.build();
    }
    private MasteryConfig() {}
}
