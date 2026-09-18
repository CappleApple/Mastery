package com.cappleapple.mastery.progression;

import com.cappleapple.mastery.MasteryRuntime;
import com.cappleapple.mastery.data.DefinitionSet;
import com.cappleapple.mastery.effects.EffectService;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;

/** Server-thread earned-XP modifiers, evaluated once before the progression transaction. */
public final class ExperienceModifiers {
    private ExperienceModifiers() {}
    public static double apply(ServerPlayer player, String treeId, double amount) {
        var definitions = MasteryRuntime.definitions();
        var tree = definitions.trees().get(treeId);
        if (tree == null) return amount;
        String attribute = tree.xpAttribute().isBlank() ? ExperienceAttributes.defaultAttribute(treeId) : tree.xpAttribute();
        double bonus = 0;
        for (var node : definitions.nodes().values()) {
            int rank = EffectService.effectiveRank(player, node);
            if (rank <= 0) continue;
            for (var raw : node.effects()) {
                var effect = resolve(definitions, raw, 0);
                if (effect != null) bonus += ExperienceRules.effectAmount(effect, treeId, rank);
            }
        }
        return ExperienceRules.scale(amount, attribute(player, "mastery:experience_gain"), attribute(player, attribute), bonus);
    }
    private static JsonObject resolve(DefinitionSet definitions, JsonObject effect, int depth) {
        if (effect == null || depth > 16) return null;
        return effect.has("ref") ? resolve(definitions, definitions.effects().get(effect.get("ref").getAsString()), depth + 1) : effect;
    }
    private static double attribute(ServerPlayer player, String id) {
        if (id.isBlank()) return 1;
        var key = ResourceLocation.tryParse(id);
        if (key == null) return 1;
        var holder = BuiltInRegistries.ATTRIBUTE.getHolder(key);
        if (holder.isEmpty() || player.getAttribute(holder.get()) == null) return 1;
        return player.getAttributeValue(holder.get());
    }
    public static void validateRuntime(DefinitionSet definitions, List<String> errors) {
        definitions.trees().forEach((id, tree) -> {
            if (tree.xpAttribute().isBlank()) return;
            var key = ResourceLocation.tryParse(tree.xpAttribute());
            if (key == null || !BuiltInRegistries.ATTRIBUTE.containsKey(key))
                errors.add("trees/" + id + ": unknown xp_attribute " + tree.xpAttribute());
        });
    }
}
