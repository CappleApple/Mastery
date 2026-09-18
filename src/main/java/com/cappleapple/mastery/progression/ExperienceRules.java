package com.cappleapple.mastery.progression;

import com.cappleapple.mastery.data.DefinitionSet;
import com.google.gson.JsonObject;
import java.util.List;

/** Pure earned-XP arithmetic and definition validation. Vanilla XP and point grants are separate. */
public final class ExperienceRules {
    public static final double MAX_AWARD = 1.0E12;
    public static final double MAX_EFFECT_BONUS = 100;
    private ExperienceRules() {}

    public static double scale(double amount, double global, double tree, double effectBonus) {
        // Preserve invalid inputs so the authoritative progression service rejects them.
        if (!Double.isFinite(amount) || amount < 0) return amount;
        if (amount == 0) return 0;
        double multiplier = factor(global) * factor(tree) * Math.clamp(1 + finite(effectBonus, 0), 0, 101);
        return Math.min(MAX_AWARD, amount * multiplier);
    }
    private static double finite(double value, double fallback) { return Double.isFinite(value) ? value : fallback; }
    private static double factor(double value) { return Math.clamp(finite(value, 1), 0, 100); }

    public static double effectAmount(JsonObject effect, String tree, int rank) {
        if (rank <= 0 || !text(effect, "type", "").equals("mastery:experience_gain")) return 0;
        String filter = text(effect, "tree", "");
        if (!filter.isEmpty() && !filter.equals(tree)) return 0;
        return effect.has("amount") ? effect.get("amount").getAsDouble() * rank : 0;
    }
    public static void validate(DefinitionSet definitions, List<String> errors) {
        definitions.trees().forEach((id, tree) -> {
            String attribute = tree.xpAttribute();
            if (!attribute.isBlank() && !attribute.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))
                errors.add("trees/" + id + ": xp_attribute must be a resource ID");
        });
        definitions.nodes().forEach((id, node) -> {
            for (var effect : node.effects()) validateEffect(effect, definitions, errors, "nodes/" + id + "/effects");
        });
        definitions.effects().forEach((id, effect) -> validateEffect(effect, definitions, errors, "effects/" + id));
    }
    public static void validateEffect(JsonObject effect, DefinitionSet definitions, List<String> errors, String path) {
        try {
            if (!text(effect, "type", "").equals("mastery:experience_gain")) return;
            if (!effect.has("amount") || !effect.get("amount").isJsonPrimitive()
                    || !effect.getAsJsonPrimitive("amount").isNumber()) {
                errors.add(path + ": experience_gain amount must be a JSON number");
            } else {
                double amount = effect.get("amount").getAsDouble();
                if (!Double.isFinite(amount) || amount < -MAX_EFFECT_BONUS || amount > MAX_EFFECT_BONUS)
                    errors.add(path + ": experience_gain amount must be between -100 and 100");
            }
            String tree = text(effect, "tree", "");
            if (!tree.isEmpty() && !definitions.trees().containsKey(tree)) errors.add(path + ": unknown experience_gain tree " + tree);
        } catch (RuntimeException ex) { errors.add(path + ": malformed experience_gain effect: " + ex.getMessage()); }
    }
    private static String text(JsonObject json, String key, String fallback) {
        if (!json.has(key)) return fallback;
        var value = json.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException(key + " must be a string");
        return value.getAsString();
    }
}
