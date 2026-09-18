package com.cappleapple.mastery.mechanics;
import com.cappleapple.mastery.MasteryRuntime;
import com.cappleapple.mastery.data.*;
import com.google.gson.JsonObject;

/** Tree modifiers activate without spell slots and inherit their tree's damage scope. */
public final class TreeModifiers {
    private TreeModifiers() {}
    public static boolean matches(NodeDefinition node,DamageContext damage) {
        if(!node.treeModifier())return true;
        return matches(MasteryRuntime.definitions(),node,damage);
    }
    public static boolean matches(DefinitionSet definitions,NodeDefinition node,DamageContext damage) {
        if(!node.treeModifier())return true;
        var settings=SettingsResolver.forNode(definitions,node.id());
        var filter=settings.getAsJsonObject("damage_filter");
        if(!filter.entrySet().stream().allMatch(e->e.getValue().getAsJsonArray().isEmpty())&&!damage.matches(filter))return false;
        if(damage.spell().isBlank())return true;
        var spell=definitions.spells().get(damage.spell());
        if(spell==null)return false;
        return spell.tree().equals(node.tree())||settings.get("inherit_subtrees").getAsBoolean()&&PromotedTrees.inherits(definitions,spell.tree(),node.tree());
    }
    public static void validate(JsonObject filter) {
        if(filter==null)throw new IllegalArgumentException("damage_filter must be an object");
        for(var entry:filter.entrySet()) {
            if(!DamageContext.FIELDS.contains(entry.getKey())||!entry.getValue().isJsonArray())throw new IllegalArgumentException("Invalid tree damage filter "+entry.getKey());
            if(entry.getValue().getAsJsonArray().size()>64)throw new IllegalArgumentException("Too many damage filters");
            for(var value:entry.getValue().getAsJsonArray()) {
                if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Damage filters require strings");
                if(entry.getKey().equals("categories")?!DamageContext.CATEGORIES.contains(value.getAsString()):net.minecraft.resources.ResourceLocation.tryParse(value.getAsString())==null)
                    throw new IllegalArgumentException("Invalid damage filter "+value);
            }
        }
    }
}
