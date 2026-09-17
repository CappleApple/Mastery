package com.cappleapple.mastery.effects;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import java.util.HashMap;
import java.util.Map;

/** Registered effect lifecycle. Implementations run on the server thread; remove must undo only their own effects. */
public final class EffectRegistry {
    public interface Handler {
        void apply(ServerPlayer player, String node, int rank, JsonObject parameters);
        default void remove(ServerPlayer player, String node, JsonObject parameters) {}
        default void onUsage(ServerPlayer player, String node, int rank, JsonObject parameters, String event, JsonObject context) {}
    }
    private static final Map<String,Handler> HANDLERS = new HashMap<>();
    private EffectRegistry() {}
    /** Register during common setup. Repeated IDs fail explicitly. */
    public static void register(String id, Handler handler) {
        net.minecraft.resources.ResourceLocation.parse(id);
        if(HANDLERS.putIfAbsent(id,handler)!=null) throw new IllegalArgumentException("Duplicate effect " + id);
    }
    public static Handler get(String id) { return HANDLERS.get(id); }
}
