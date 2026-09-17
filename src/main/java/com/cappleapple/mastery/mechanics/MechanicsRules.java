package com.cappleapple.mastery.mechanics;

import com.google.gson.JsonObject;

/** Pure numeric rules shared by runtime mechanics, the editor, and tests. */
public final class MechanicsRules {
    private MechanicsRules() {}

    public static double chance(double base, double bonus) {
        return Double.isFinite(base) && Double.isFinite(bonus) ? Math.clamp(base + bonus, 0, 1) : 0;
    }

    public static boolean health(double health, double maximum, JsonObject condition) {
        double value = text(condition, "unit", "points").equals("fraction")
                ? (maximum > 0 ? health / maximum : 0) : health;
        return value >= number(condition, "min", 0) && value <= number(condition, "max", Double.MAX_VALUE);
    }

    public static int stacks(int current, int added, int maximum) {
        return (int)Math.clamp((long)current + added, 0, maximum);
    }

    public static boolean crossed(int previous, int next, int threshold) {
        return threshold > 0 && previous < threshold && next >= threshold;
    }

    public static double number(JsonObject json, String key, double fallback) {
        return json.has(key) ? json.get(key).getAsDouble() : fallback;
    }

    public static String text(JsonObject json, String key, String fallback) {
        return json.has(key) ? json.get(key).getAsString() : fallback;
    }

    public static boolean bool(JsonObject json, String key, boolean fallback) {
        return json.has(key) ? json.get(key).getAsBoolean() : fallback;
    }
}
