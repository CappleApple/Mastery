package com.cappleapple.mastery.client.gui.editor;

import com.google.gson.JsonObject;

/** JSON is the source of truth for both the toggle and the generated generic Skill Book. */
public final class BookGateEditor {
    private BookGateEditor() {}
    public static String token(JsonObject definition) {
        if (!definition.has("book_token")) return "";
        var value = definition.get("book_token");
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("book_token must be a string.");
        return value.getAsString();
    }
    public static JsonObject toggle(JsonObject definition, String nodeId, boolean enabled) {
        JsonObject result = definition.deepCopy();
        String token = enabled ? token(definition) : "";
        if (enabled && token.isBlank()) token = nodeId;
        if (!token.isBlank() && !token.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))
            throw new IllegalArgumentException("Choose a valid resource ID for the node or book_token.");
        result.addProperty("book_token", token);
        return result;
    }
    public static String giveCommand(String token) {
        if (token.isBlank() || !token.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) throw new IllegalArgumentException("Enable a valid Skill Book token first.");
        return "/give @s mastery:skill_book[minecraft:custom_data={mastery_unlock:\"" + token + "\"}]";
    }
}
