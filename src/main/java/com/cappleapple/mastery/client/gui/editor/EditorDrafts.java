package com.cappleapple.mastery.client.gui.editor;

import com.cappleapple.mastery.data.DefinitionLoader;
import com.google.gson.*;

/** Small, valid starting definitions. Full graph validation remains server-side. */
public final class EditorDrafts {
    public static final int MAX_PAYLOAD = 8192;
    private EditorDrafts() {}

    public static JsonObject tree() {
        JsonObject json = new JsonObject();
        json.addProperty("name", "New specialization");
        json.addProperty("description", "");
        json.addProperty("icon", "minecraft:book");
        json.addProperty("section", "south");
        json.add("unlock", com.cappleapple.mastery.data.UnlockPresentation.INHERIT.toJson());
        json.addProperty("theme", "default");
        json.addProperty("charge", "default");
        json.addProperty("modifier_slots", "default");
        json.addProperty("xp_base", 100);
        json.addProperty("xp_growth", 20);
        json.addProperty("point_every", 1);
        json.addProperty("points_per_award",1);
        return json;
    }

    public static JsonObject node(String tree, String parent) {
        JsonObject json = new JsonObject();
        json.addProperty("tree", tree);
        json.addProperty("name", "New skill");
        json.addProperty("description", "");
        json.addProperty("icon", "minecraft:book");
        json.addProperty("type", "passive");
        json.addProperty("max_rank", 1);
        json.addProperty("cost", 1);
        json.addProperty("level", 0);
        json.addProperty("world_tier", 0);
        JsonArray dependencies = new JsonArray();
        if (!parent.isBlank()) dependencies.add(parent);
        json.add("dependencies", dependencies);
        json.add("requirements", new JsonArray());
        json.add("effects", new JsonArray());
        json.addProperty("visibility", "available");
        json.addProperty("book_token", "");
        json.addProperty("theme", "default");json.addProperty("unlock", "default");
        json.addProperty("charge", "default");json.addProperty("modifier_slots", "default");
        return json;
    }

    public static String envelope(String kind, String id, String text, long revision) {
        if (!DefinitionLoader.KINDS.contains(kind)) throw new IllegalArgumentException("Unknown definition kind.");
        JsonElement parsed = JsonParser.parseString(text);
        if (!parsed.isJsonObject()) throw new IllegalArgumentException("Definition must be a JSON object.");
        JsonObject definition = parsed.getAsJsonObject();
        DefinitionLoader.parse(kind, id, definition);
        JsonObject payload = new JsonObject();
        payload.addProperty("kind", kind);
        payload.addProperty("revision", revision);
        payload.add("definition", definition);
        String result = payload.toString();
        if (result.length() > MAX_PAYLOAD) throw new IllegalArgumentException("Definition exceeds the 8192-character network limit. Shorten it before saving.");
        return result;
    }
}
