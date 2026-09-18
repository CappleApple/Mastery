package com.cappleapple.mastery.classes;

import com.google.gson.*;
import java.util.*;

/** Offline-readable class grants. Registry-specific item components are checked separately at reload. */
public record ClassDefinition(String id, String name, String description, String icon,
        Map<String, Integer> startingPoints, Map<String, Integer> startingSkills,
        List<JsonObject> startingInventory, List<AttributeGrant> attributes) {
    public record AttributeGrant(String attribute, double amount, String operation) {}
    public ClassDefinition {
        startingPoints = Collections.unmodifiableMap(new LinkedHashMap<>(startingPoints)); startingSkills = Collections.unmodifiableMap(new LinkedHashMap<>(startingSkills));
        startingInventory = startingInventory.stream().map(JsonObject::deepCopy).toList();
        attributes = List.copyOf(attributes);
    }
    public static ClassDefinition parse(String id, JsonObject json) {
        identifier(id, "class ID");
        String name = text(json, "name", id), description = text(json, "description", ""), icon = text(json, "icon", "minecraft:book");
        var points = integerMap(json, "starting_points", 0);
        var skills = integerMap(json, "starting_skills", 1);
        var inventory = new ArrayList<JsonObject>();
        for (JsonElement entry : array(json, "starting_inventory")) {
            if (!entry.isJsonObject()) throw new IllegalArgumentException("starting_inventory entries must be item-stack objects");
            var stack = entry.getAsJsonObject();
            identifier(text(stack, "id", ""), "starting_inventory.id");
            if (stack.has("count")) integer(stack.get("count"), "starting_inventory.count", 1, 99);
            if (stack.has("components") && !stack.get("components").isJsonObject()) throw new IllegalArgumentException("starting_inventory.components must be an object");
            inventory.add(stack.deepCopy());
        }
        var attributes = new ArrayList<AttributeGrant>();
        for (JsonElement entry : array(json, "attributes")) {
            if (!entry.isJsonObject()) throw new IllegalArgumentException("attributes entries must be objects");
            var attribute = entry.getAsJsonObject();
            String key = text(attribute, "attribute", ""); identifier(key, "attributes.attribute");
            if (!attribute.has("amount") || !attribute.get("amount").isJsonPrimitive() || !attribute.getAsJsonPrimitive("amount").isNumber()
                    || !Double.isFinite(attribute.get("amount").getAsDouble())) throw new IllegalArgumentException("attributes.amount must be finite");
            String operation = text(attribute, "operation", "add_value");
            if (!Set.of("add_value", "add_multiplied_base", "add_multiplied_total").contains(operation)) throw new IllegalArgumentException("Unknown attribute operation " + operation);
            attributes.add(new AttributeGrant(key, attribute.get("amount").getAsDouble(), operation));
        }
        return new ClassDefinition(id, name, description, icon, points, skills, inventory, attributes);
    }
    private static Map<String, Integer> integerMap(JsonObject json, String key, int minimum) {
        var values = new LinkedHashMap<String, Integer>();
        if (!json.has(key)) return values;
        if (!json.get(key).isJsonObject()) throw new IllegalArgumentException(key + " must be an object");
        if (json.getAsJsonObject(key).size() > 1024) throw new IllegalArgumentException(key + " supports at most 1024 entries");
        json.getAsJsonObject(key).entrySet().forEach(entry -> {
            identifier(entry.getKey(), key); values.put(entry.getKey(), integer(entry.getValue(), key, minimum, Integer.MAX_VALUE));
        });
        return values;
    }
    private static int integer(JsonElement value, String key, int min, int max) {
        try {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException();
            int amount = value.getAsBigDecimal().intValueExact();
            if (amount < min || amount > max) throw new IllegalArgumentException();
            return amount;
        } catch (RuntimeException ex) { throw new IllegalArgumentException(key + " must be an integer between " + min + " and " + max); }
    }
    private static JsonArray array(JsonObject json, String key) {
        if (!json.has(key)) return new JsonArray();
        if (!json.get(key).isJsonArray()) throw new IllegalArgumentException(key + " must be an array");
        var result = json.getAsJsonArray(key);
        if (result.size() > 128) throw new IllegalArgumentException(key + " supports at most 128 entries");
        return result;
    }
    private static String text(JsonObject json, String key, String fallback) {
        if (!json.has(key)) return fallback;
        if (!json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isString()) throw new IllegalArgumentException(key + " must be a string");
        return json.get(key).getAsString();
    }
    private static void identifier(String value, String key) {
        if (!value.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) throw new IllegalArgumentException(key + " must be a namespaced ID");
    }
}
