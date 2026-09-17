package com.cappleapple.mastery.spells;

import com.cappleapple.mastery.api.spell.SpellModifierRegistry;
import com.cappleapple.mastery.data.DefinitionSet;
import com.google.gson.*;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Rejects unknown native spell references and malformed Mastery modifier/context data at reload. */
final class SpellValidation {
    private static final Set<String> CONDITION_IDS=Set.of("item","item_tag","offhand","offhand_tag");
    private static final Set<String> CONDITION_FLAGS=Set.of("blocking","empty","dual_wield","requires_unlock","provider_only");
    private SpellValidation() {}
    static List<String> validate(DefinitionSet definitions) {
        List<String> errors=new ArrayList<>();
        definitions.spells().forEach((id,definition) -> {
            var spell=SpellRegistry.getSpell(id);
            if(spell==SpellRegistry.none()) errors.add("spell "+id+": no existing Iron spell has this registry ID");
            else if(definition.level()<1||definition.level()>spell.getMaxLevel()) errors.add("spell "+id+": base level must be between 1 and "+spell.getMaxLevel());
        });
        definitions.nodes().forEach((id,node) -> {
            if(!node.modifier().isBlank()&&SpellModifierRegistry.get(node.modifier())==null) errors.add("node "+id+": unknown native spell modifier "+node.modifier());
            if(!node.modifier().isBlank()&&node.spell().isBlank()&&node.effects().stream().noneMatch(e->hasExplicitTarget(e,definitions,0)))errors.add("node "+id+": equipped modifier needs a node spell or an explicit spell_modifier spell filter");
            for(var effect:node.effects()) modifier(effect,"node "+id,definitions,errors,0);
        });
        definitions.effects().forEach((id,effect)->modifier(effect,"effect "+id,definitions,errors,0));
        definitions.contexts().forEach((id,context) -> {
            condition(context.condition(),"context "+id,errors,0);
            if(id.equals("mastery:two_handed")&&(!context.condition().has("provider_only")||!context.condition().get("provider_only").isJsonPrimitive()
                    ||!context.condition().get("provider_only").getAsJsonPrimitive().isBoolean()||!context.condition().get("provider_only").getAsBoolean()))
                errors.add("context mastery:two_handed must use provider_only:true; Better Combat owns classification");
        });
        return List.copyOf(errors);
    }
    private static void modifier(JsonObject effect,String path,DefinitionSet definitions,List<String> errors,int depth) {
        if(depth>32) return;
        if(effect.has("ref")&&isString(effect.get("ref"))) {
            var next=definitions.effects().get(effect.get("ref").getAsString()); if(next!=null) modifier(next,path,definitions,errors,depth+1); return;
        }
        if(!effect.has("type")||!isString(effect.get("type")))return;
        String type=effect.get("type").getAsString();
        if(!type.equals("mastery:spell_modifier")&&!type.equals("mastery:unlock_spell"))return;
        if(effect.has("spell")) {
            if(!isString(effect.get("spell"))||!definitions.spells().containsKey(effect.get("spell").getAsString()))errors.add(path+": spell must reference an existing Mastery spell binding");
        } else if(type.equals("mastery:unlock_spell"))errors.add(path+": unlock_spell needs spell");
        if(type.equals("mastery:unlock_spell")) {
            for(String key:List.of("level","levels_per_rank"))if(effect.has(key)) {
                var value=effect.get(key);int minimum=key.equals("level")?1:0;
                if(!isNumber(value)||value.getAsDouble()!=Math.rint(value.getAsDouble())||value.getAsDouble()<minimum||value.getAsDouble()>255)
                    errors.add(path+": "+key+" must be an integer between "+minimum+" and 255");
            }
            return;
        }
        for(var entry:effect.entrySet()) {
            String key=entry.getKey(); var value=entry.getValue();
            if(key.equals("spell_level")) {
                if(!isNumber(value)||value.getAsDouble()!=Math.rint(value.getAsDouble())||Math.abs(value.getAsDouble())>255)
                    errors.add(path+": spell_level must be an integer between -255 and 255");
            } else if(Set.of("mana_multiplier","cooldown_multiplier","cast_time_multiplier").contains(key)) {
                if(!isNumber(value)||value.getAsDouble()<=0||value.getAsDouble()>100) errors.add(path+": "+key+" must be finite, greater than zero and at most 100");
            }
        }
    }
    private static boolean hasExplicitTarget(JsonObject effect,DefinitionSet definitions,int depth) {
        if(depth>32)return false;
        if(effect.has("ref")&&isString(effect.get("ref"))) {
            var next=definitions.effects().get(effect.get("ref").getAsString());return next!=null&&hasExplicitTarget(next,definitions,depth+1);
        }
        return effect.has("type")&&isString(effect.get("type"))&&effect.get("type").getAsString().equals("mastery:spell_modifier")&&effect.has("spell")&&isString(effect.get("spell"));
    }
    private static void condition(JsonObject condition,String path,List<String> errors,int depth) {
        if(depth>32) { errors.add(path+": context condition nesting exceeds 32"); return; }
        for(var entry:condition.entrySet()) {
            String key=entry.getKey(); var value=entry.getValue();
            if(CONDITION_IDS.contains(key)) {
                if(!isString(value)||ResourceLocation.tryParse(value.getAsString())==null) errors.add(path+": "+key+" must be a resource ID");
            } else if(CONDITION_FLAGS.contains(key)) {
                if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isBoolean()) errors.add(path+": "+key+" must be a boolean");
            } else if(key.equals("not")) {
                if(!value.isJsonObject()) errors.add(path+": not must be an object"); else condition(value.getAsJsonObject(),path,errors,depth+1);
            } else if(key.equals("and")||key.equals("or")) {
                if(!value.isJsonArray()) errors.add(path+": "+key+" must be an array");
                else for(var child:value.getAsJsonArray()) {
                    if(!child.isJsonObject()) errors.add(path+": "+key+" entries must be objects"); else condition(child.getAsJsonObject(),path,errors,depth+1);
                }
            } else errors.add(path+": unsupported context condition "+key);
        }
    }
    private static boolean isString(JsonElement value) { return value.isJsonPrimitive()&&value.getAsJsonPrimitive().isString(); }
    private static boolean isNumber(JsonElement value) { return value.isJsonPrimitive()&&value.getAsJsonPrimitive().isNumber()&&Double.isFinite(value.getAsDouble()); }
}
