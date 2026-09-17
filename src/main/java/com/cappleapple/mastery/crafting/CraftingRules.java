package com.cappleapple.mastery.crafting;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Set;

/** Data-only crafting schema validation, shared by reloads, the editor and tests. */
public final class CraftingRules {
    public static final Set<String> TYPES = Set.of("mastery:crafting_attribute", "mastery:crafting_food", "mastery:crafting_potion", "mastery:placed_comfort");
    private CraftingRules() {}
    public static void validate(JsonObject effect, List<String> errors, String path) {
        try {
            String type = text(effect, "type", "");
            if (!TYPES.contains(type)) return;
            range(effect, "chance", 0, 1, errors, path);
            for (String key : List.of("item", "item_tag", "block", "block_tag", "attribute", "added_effect")) {
                if (effect.has(key) && !text(effect,key,"").matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) errors.add(path + ": " + key + " must be a resource ID");
            }
            if (type.equals("mastery:crafting_attribute")) {
                if (!effect.has("attribute")) errors.add(path + ": crafting_attribute needs attribute");
                range(effect,"amount",-1000000,1000000,errors,path);
                if (!Set.of("add_value","add_multiplied_base","add_multiplied_total").contains(text(effect,"operation","add_value"))) errors.add(path + ": unsupported crafting attribute operation");
                if (!Set.of("any","mainhand","offhand","hand","feet","legs","chest","head","armor","body").contains(text(effect,"slot","mainhand"))) errors.add(path + ": unsupported equipment slot");
            }
            if (type.equals("mastery:crafting_food")) {
                for (String key : List.of("nutrition_bonus","saturation_bonus","buff_strength_bonus","buff_duration_bonus","meal_strength_bonus","meal_duration_bonus")) range(effect,key,0,100,errors,path);
            }
            if (type.equals("mastery:crafting_potion")) {
                range(effect,"duration_bonus",0,100,errors,path);
                integer(effect,"amplifier_bonus",0,254,errors,path);
                integer(effect,"added_amplifier",0,254,errors,path);
                integer(effect,"added_duration",1,1728000,errors,path);
            }
            if (type.equals("mastery:placed_comfort")) {
                range(effect,"amount",0,1000000,errors,path);
                range(effect,"radius",1,64,errors,path);
                if (text(effect,"comfort_type","mastery_crafted").isBlank()) errors.add(path + ": comfort_type cannot be blank");
            }
        } catch (RuntimeException ex) { errors.add(path + ": malformed crafting effect: " + ex.getMessage()); }
    }
    private static void range(JsonObject effect,String key,double min,double max,List<String> errors,String path) {
        if (!effect.has(key)) return;
        if(!effect.get(key).isJsonPrimitive()||!effect.getAsJsonPrimitive(key).isNumber()){errors.add(path+": "+key+" must be a JSON number");return;}
        double value=effect.get(key).getAsDouble();
        if (!Double.isFinite(value)||value<min||value>max) errors.add(path + ": " + key + " must be between " + min + " and " + max);
    }
    private static void integer(JsonObject effect,String key,int min,int max,List<String> errors,String path) {
        range(effect,key,min,max,errors,path);
        if(effect.has(key)&&effect.get(key).isJsonPrimitive()&&effect.getAsJsonPrimitive(key).isNumber()&&effect.get(key).getAsDouble()!=Math.rint(effect.get(key).getAsDouble())) errors.add(path + ": " + key + " must be an integer");
    }
    public static double number(JsonObject effect,String key,double fallback) { return effect.has(key)?effect.get(key).getAsDouble():fallback; }
    public static String text(JsonObject effect,String key,String fallback) {if(!effect.has(key))return fallback;var value=effect.get(key);if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())throw new IllegalArgumentException(key+" must be a string");return value.getAsString();}
    public static double chance(double base,double bonus) { return Math.clamp(base+bonus,0,1); }
    public static int duration(int ticks,double bonus) { return ticks<0?ticks:(int)Math.clamp(Math.round(ticks*(1+bonus)),1,1728000); }
    public static int strength(int amplifier,double bonus) { return (int)Math.clamp(Math.floor((amplifier+1)*(1+bonus))-1,0,254); }
}
