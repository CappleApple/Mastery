package com.cappleapple.mastery.api;

import com.cappleapple.mastery.MasteryRuntime;
import com.cappleapple.mastery.config.MasteryConfig;
import com.cappleapple.mastery.progression.ProgressionService;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Entry points for integrations. Register providers during common setup; access player state on the server thread. */
public final class MasteryAPI {
    private static final List<WorldTierProvider> TIERS = new CopyOnWriteArrayList<>();
    private MasteryAPI() {}
    /** Registers a provider. First nonnegative result wins, in registration order. */
    public static void registerWorldTierProvider(WorldTierProvider provider) { TIERS.add(java.util.Objects.requireNonNull(provider)); }
    /** Returns current tier, or -1 for uncapped fallback. */
    public static int worldTier(ServerLevel level) {
        for (var provider : TIERS) { int tier = provider.getTier(level); if (tier >= 0) return tier; }
        return MasteryConfig.DEFAULT_WORLD_TIER.get();
    }
    public static int level(ServerPlayer player, String tree) { var state=MasteryRuntime.progress(player).trees().get(tree); return state==null?0:state.level(); }
    public static double xp(ServerPlayer player, String tree) { var state=MasteryRuntime.progress(player).trees().get(tree); return state==null?0:state.xp(); }
    public static int points(ServerPlayer player, String tree) { var state=MasteryRuntime.progress(player).trees().get(tree); return state==null?0:state.points(); }
    public static int rank(ServerPlayer player, String node) { return MasteryRuntime.progress(player).rank(node); }
    /** Grants finite nonnegative XP and sends changed player state. Unknown trees and invalid amounts fail without mutation. */
    public static ProgressionService.Change grantXp(ServerPlayer player, String tree, double amount) { return MasteryRuntime.grantXp(player, tree, amount); }
    /** Class systems can grant specialization points to reveal a starting tree. No other tree balance changes. */
    public static ProgressionService.Change grantPoints(ServerPlayer player,String tree,int amount) { return MasteryRuntime.grantPoints(player,tree,amount); }
    /** Emits a custom usage event for datapack XP sources. Context fields use the condition schema. */
    public static void emitUsage(ServerPlayer player, String event, JsonObject context) { MasteryRuntime.usage(player, event, context); }
}
