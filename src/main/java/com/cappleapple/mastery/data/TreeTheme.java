package com.cappleapple.mastery.data;

import com.google.gson.JsonObject;
import java.util.Locale;

/** RGB outline colors. Gradient is the fraction of the border occupied by the color transition. */
public record TreeTheme(String innerColor, String outerColor, double gradient) {
    public static final TreeTheme DEFAULT = new TreeTheme("#D3AD5B", "#604522", 1);
    public TreeTheme {
        innerColor = color(innerColor); outerColor = color(outerColor);
        if (!Double.isFinite(gradient) || gradient < 0 || gradient > 1)
            throw new IllegalArgumentException("theme.gradient must be between 0 and 1");
    }
    private static String color(String value) {
        if (value == null || !value.matches("#[0-9a-fA-F]{6}"))
            throw new IllegalArgumentException("theme colors must use #RRGGBB");
        return value.toUpperCase(Locale.ROOT);
    }
    public static TreeTheme parse(JsonObject json) {
        return new TreeTheme(json.has("inner_color") ? json.get("inner_color").getAsString() : DEFAULT.innerColor,
                json.has("outer_color") ? json.get("outer_color").getAsString() : DEFAULT.outerColor,
                json.has("gradient") ? json.get("gradient").getAsDouble() : DEFAULT.gradient);
    }
    public JsonObject toJson() {
        JsonObject json = new JsonObject(); json.addProperty("inner_color", innerColor);
        json.addProperty("outer_color", outerColor); json.addProperty("gradient", gradient); return json;
    }
    public int innerRgb() { return Integer.parseInt(innerColor.substring(1), 16); }
    public int outerRgb() { return Integer.parseInt(outerColor.substring(1), 16); }
}
