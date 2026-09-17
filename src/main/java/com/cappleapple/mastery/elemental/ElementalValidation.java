package com.cappleapple.mastery.elemental;

import com.cappleapple.mastery.data.DefinitionSet;
import com.google.gson.JsonObject;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

public final class ElementalValidation {
    private ElementalValidation() {}
    public static void validate(DefinitionSet definitions,List<String> errors) {
        Set<String> schools=new HashSet<>(),types=new HashSet<>();
        definitions.elements().forEach((id,j)->{
            String path="elements/"+id;
            try {
                boolean school=j.has("school")&&!j.get("school").getAsString().isBlank(),type=j.has("damage_type")&&!j.get("damage_type").getAsString().isBlank();
                if(!school&&!type)throw new IllegalArgumentException("school or damage_type is required");
                if(school&&!schools.add(required(j,"school")))throw new IllegalArgumentException("duplicate school");
                if(type&&!types.add(required(j,"damage_type")))throw new IllegalArgumentException("duplicate damage_type");
                for(String key:List.of("enchantment","weapon_attribute","power_attribute","conversion_attribute","attunement_attribute","potency_attribute","mitigation_attribute"))if(j.has(key)&&!j.get(key).getAsString().isEmpty())required(j,key);
                if(j.has("elemental")&&(!j.get("elemental").isJsonPrimitive()||!j.getAsJsonPrimitive("elemental").isBoolean()))throw new IllegalArgumentException("elemental must be boolean");
                if(j.has("damage_per_level"))number(j,"damage_per_level",0,100);
                if(j.has("damage_modifiers"))for(var entry:j.getAsJsonObject("damage_modifiers").entrySet()) {
                    if(!definitions.mobTypes().containsKey(entry.getKey()))throw new IllegalArgumentException("unknown mob type "+entry.getKey());
                    number(j.getAsJsonObject("damage_modifiers"),entry.getKey(),-1,100);
                }
            }catch(RuntimeException ex){errors.add(path+": "+ex.getMessage());}
        });
        definitions.mobTypes().forEach((id,j)->{
            try {selectors(j,"entities","entity_tags");}catch(RuntimeException ex){errors.add("mob_types/"+id+": "+ex.getMessage());}
        });
        definitions.weaponTypes().forEach((id,j)->{
            try {
                selectors(j,"items","item_tags");String element=required(j,"element");
                if(!definitions.elements().containsKey(element))throw new IllegalArgumentException("unknown element "+element);
                if(j.has("priority")){double value=number(j,"priority",-1000000,1000000);if(value!=Math.rint(value))throw new IllegalArgumentException("priority must be an integer");}
            }catch(RuntimeException ex){errors.add("weapon_types/"+id+": "+ex.getMessage());}
        });
    }
    public static void validateRuntime(DefinitionSet definitions,List<String> errors) {
        Map<String,String> resolvedTypes=new HashMap<>();
        var server=net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        definitions.elements().forEach((id,j)->{
            String path="elements/"+id;
            try {
                var type=new DamageTypes.Type(id,j);
                if(j.has("school")&&!j.get("school").getAsString().isEmpty()&&!SchoolRegistry.REGISTRY.containsKey(ResourceLocation.parse(required(j,"school")))) {
                    errors.add(path+": unknown Iron's school");return;
                }
                String nativeType=type.damageType().location().toString();
                String previous=resolvedTypes.putIfAbsent(nativeType,id);
                if(previous!=null)errors.add(path+": native damage type "+nativeType+" is already mapped by "+previous);
                if(server!=null) {
                    var damageTypes=server.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE);
                    if(!damageTypes.containsKey(ResourceLocation.parse(nativeType)))errors.add(path+": unknown registered damage_type "+nativeType);
                    if(j.has("enchantment")&&!j.get("enchantment").getAsString().isEmpty()
                            &&!server.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).containsKey(ResourceLocation.parse(j.get("enchantment").getAsString())))
                        errors.add(path+": unknown enchantment "+j.get("enchantment").getAsString());
                }
                for(String key:List.of("weapon_attribute","power_attribute","conversion_attribute","attunement_attribute","potency_attribute","mitigation_attribute"))if(j.has(key)&&!j.get(key).getAsString().isEmpty()&&!BuiltInRegistries.ATTRIBUTE.containsKey(ResourceLocation.parse(j.get(key).getAsString())))errors.add(path+": unknown "+key);
            }catch(RuntimeException ex){errors.add(path+": "+ex.getMessage());}
        });

    }
    private static double number(JsonObject json,String key,double min,double max) {
        var value=json.getAsJsonPrimitive(key);double n=value.getAsDouble();
        if(!value.isNumber()||!Double.isFinite(n)||n<min||n>max)throw new IllegalArgumentException(key+" must be numeric in "+min+".."+max);return n;
    }
    private static void selectors(JsonObject json,String... keys) {
        int count=0;
        for(String key:keys)if(json.has(key))for(var entry:json.getAsJsonArray(key)) {
            if(!entry.isJsonPrimitive()||!entry.getAsJsonPrimitive().isString()||!entry.getAsString().matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))throw new IllegalArgumentException(key+" must contain resource IDs");count++;
        }
        if(count==0)throw new IllegalArgumentException("at least one entity/item ID or tag is required");
    }
    private static String required(JsonObject json,String key) {
        if(!json.has(key)||!json.get(key).isJsonPrimitive()||!json.getAsJsonPrimitive(key).isString())throw new IllegalArgumentException(key+" must be a resource ID");
        String value=json.get(key).getAsString();if(!value.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))throw new IllegalArgumentException(key+" must be a resource ID");return value;
    }
}
