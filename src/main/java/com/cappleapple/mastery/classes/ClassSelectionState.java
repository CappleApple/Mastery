package com.cappleapple.mastery.classes;

import com.google.gson.JsonObject;

/** Saved alongside the player's inventory, so a reconnect can restore the exact pre-selector mode. */
public record ClassSelectionState(String gameMode, String dimension, double x, double y, double z, float yaw, float pitch) {
    public ClassSelectionState {
        if (!java.util.Set.of("survival", "creative", "adventure", "spectator").contains(gameMode))
            throw new IllegalArgumentException("Invalid saved game mode");
        if (!dimension.matches("[a-z0-9_.-]+:[a-z0-9/._-]+") || !Double.isFinite(x) || !Double.isFinite(y)
                || !Double.isFinite(z) || !Float.isFinite(yaw) || !Float.isFinite(pitch))
            throw new IllegalArgumentException("Invalid saved class selection anchor");
    }
    public JsonObject toJson() {
        var json = new JsonObject();
        json.addProperty("game_mode", gameMode); json.addProperty("dimension", dimension);
        json.addProperty("x", x); json.addProperty("y", y); json.addProperty("z", z);
        json.addProperty("yaw", yaw); json.addProperty("pitch", pitch);
        return json;
    }
    public static ClassSelectionState fromJson(JsonObject json) {
        try {
            return new ClassSelectionState(json.get("game_mode").getAsString(), json.get("dimension").getAsString(),
                    json.get("x").getAsDouble(), json.get("y").getAsDouble(), json.get("z").getAsDouble(),
                    json.get("yaw").getAsFloat(), json.get("pitch").getAsFloat());
        } catch (RuntimeException ignored) { return null; }
    }
}
