package com.cappleapple.mastery.data;

import com.cappleapple.mastery.spells.SpellService;
import com.cappleapple.mastery.effects.EffectRegistry;
import com.cappleapple.mastery.requirements.RequirementRegistry;
import com.google.gson.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Checks registered extension IDs after common setup, in addition to the pure graph schema. */
public final class RuntimeDefinitionValidator {
    private RuntimeDefinitionValidator(){}
    public static List<String> validate(DefinitionSet definitions) {
        List<String> errors=new ArrayList<>(SpellService.validateDefinitions(definitions));
        com.cappleapple.mastery.elemental.ElementalValidation.validateRuntime(definitions,errors);
        com.cappleapple.mastery.mechanics.MechanicsValidation.validateRuntime(definitions,errors);
        com.cappleapple.mastery.crafting.CraftingService.validateRuntime(definitions,errors);
        com.cappleapple.mastery.costs.CostService.validateRuntime(definitions,errors);
        for(var node:definitions.nodes().values()){
            for(var requirement:node.requirements())check(requirement,false,"nodes/"+node.id(),errors);
            for(var effect:node.effects())check(effect,true,"nodes/"+node.id(),errors);
        }
        definitions.requirements().forEach((id,json)->check(json,false,"requirements/"+id,errors));
        definitions.effects().forEach((id,json)->check(json,true,"effects/"+id,errors));
        return errors;
    }
    private static void check(JsonElement value,boolean effect,String path,List<String> errors){
        if(value.isJsonArray()){for(var child:value.getAsJsonArray())check(child,effect,path,errors);return;}
        if(!value.isJsonObject())return;
        var json=value.getAsJsonObject();
        try {
            if(json.has("type")){
                String type=json.get("type").getAsString();
                if(effect?EffectRegistry.get(type)==null:!RequirementRegistry.registered(type))errors.add(path+": unregistered "+(effect?"effect ":"requirement ")+type);
                if(type.equals("mastery:attribute")){
                    var id=ResourceLocation.tryParse(json.get("attribute").getAsString());
                    if(id==null||!BuiltInRegistries.ATTRIBUTE.containsKey(id))errors.add(path+": unknown attribute "+json.get("attribute"));
                    if(!Set.of("add_value","add_multiplied_base","add_multiplied_total").contains(RequirementRegistry.string(json,"operation","add_value")))errors.add(path+": invalid attribute operation");
                }
                for(String key:List.of("amount","level","rank","tier","duration","strength","amplifier","ignite_ticks")){
                    if(json.has(key)&&(!json.get(key).isJsonPrimitive()||!json.getAsJsonPrimitive(key).isNumber()||!Double.isFinite(json.get(key).getAsDouble())))errors.add(path+": "+key+" must be a finite number");
                }
            }
            for(String key:List.of("and","or","not"))if(json.has(key))check(json.get(key),effect,path+"/"+key,errors);
        }catch(RuntimeException ex){errors.add(path+": malformed "+(effect?"effect":"requirement")+": "+ex.getMessage());}
    }
}
