package com.cappleapple.mastery.client.gui.editor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;

/** Shared card/form defaults for the server's declarative combat scripts. */
public final class ScriptSchema {
    private ScriptSchema() {}
    public static final List<String> ACTIONS = List.of("keyword", "remove_keyword", "damage", "effect", "spell", "heal", "lightning", "particles");
    public static JsonObject object(String json) { return JsonParser.parseString(json).getAsJsonObject(); }
    public static boolean scripted(String kind) { return kind.equals("triggers") || kind.equals("keywords"); }
    public static JsonObject root(String kind) {
        return kind.equals("triggers")
                ? object("{\"event\":\"hit\",\"chance\":1,\"cooldown\":0,\"conditions\":[],\"actions\":[]}")
                : object("{\"name\":\"New keyword\",\"description\":\"\",\"max_stacks\":10,\"duration\":100,\"tick_interval\":20,\"decay_delay\":0,\"decay_interval\":20,\"decay_stacks\":0,\"stacks_lost_actions\":[],\"all_stacks_lost_actions\":[],\"tick_actions\":[],\"threshold\":5,\"consume_stacks\":true,\"threshold_actions\":[]}");
    }
    public static JsonObject condition(String type) {
        if(type.equals("damage"))return object("{\"type\":\"damage\",\"categories\":[\"melee\"]}");
        return type.equals("keyword") ? object("{\"type\":\"keyword\",\"target\":\"target\",\"keyword\":\"\",\"min\":1}")
                : object("{\"type\":\"health\",\"target\":\"target\",\"unit\":\"fraction\",\"max\":0.5}");
    }
    public static JsonObject conditionFields(JsonObject current) {
        if(current.has("type")&&current.get("type").getAsString().equals("damage"))return object("{\"type\":\"damage\",\"categories\":[],\"elements\":[],\"damage_types\":[],\"damage_tags\":[]}");
        return current.has("type") && current.get("type").getAsString().equals("keyword")
                ? object("{\"type\":\"keyword\",\"target\":\"target\",\"keyword\":\"\",\"min\":1,\"max\":10}")
                : object("{\"type\":\"health\",\"target\":\"target\",\"unit\":\"fraction\",\"min\":0,\"max\":1}");
    }
    public static JsonObject action(String type) {
        JsonObject result = object("{\"type\":\"" + type + "\",\"target\":\"target\"}");
        JsonObject details = switch (type) {
            case "keyword" -> object("{\"keyword\":\"\",\"stacks\":1}");
            case "remove_keyword" -> object("{\"keyword\":\"\",\"stacks\":0}");
            case "damage" -> object("{\"amount\":1,\"school\":\"irons_spellbooks:fire\",\"per_stack\":false}");
            case "effect" -> object("{\"effect\":\"minecraft:slowness\",\"duration\":100,\"amplifier\":0}");
            case "spell" -> object("{\"spell\":\"irons_spellbooks:firebolt\",\"level\":1}");
            case "heal", "lightning" -> object("{\"amount\":2}");
            case "particles" -> object("{\"particle\":\"minecraft:flame\",\"count\":12,\"spread\":0.5,\"speed\":0.01}");
            default -> new JsonObject();
        };
        details.entrySet().forEach(e -> result.add(e.getKey(), e.getValue()));
        return result;
    }
    public static JsonObject actionFields(JsonObject current) {
        JsonObject result = action(current.has("type") ? current.get("type").getAsString() : "damage");
        result.addProperty("center", "target"); result.addProperty("include_players", false); result.addProperty("per_rank", false); result.addProperty("per_stack", false); result.addProperty("chance", 1);
        result.addProperty("radius", 8); result.addProperty("limit", 16); result.addProperty("include_allies", false);
        if(result.get("type").getAsString().equals("damage")||result.get("type").getAsString().equals("lightning"))result.addProperty("damage_fraction",0);
        if(java.util.List.of("damage","lightning","heal").contains(result.get("type").getAsString()))result.addProperty("element",result.has("school")?result.get("school").getAsString():result.get("type").getAsString().equals("heal")?"irons_spellbooks:holy":"irons_spellbooks:lightning");
        result.add("conditions", new com.google.gson.JsonArray());
        if (result.get("type").getAsString().equals("keyword")) result.addProperty("duration", 100);
        return result;
    }
}
