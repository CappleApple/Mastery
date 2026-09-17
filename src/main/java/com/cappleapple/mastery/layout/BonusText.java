package com.cappleapple.mastery.layout;
import com.google.gson.JsonObject;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/** Player-facing totals for built-in effects, using the same rank scaling as their handlers. */
public final class BonusText {
    private BonusText(){}
    public static List<String> describe(JsonObject effect,int rank,Function<String,String> attributeName,Predicate<String> percentAttribute){
        var lines=new ArrayList<String>();String type=text(effect,"type","");
        if(effect.has("attribute")) {
            String id=effect.get("attribute").getAsString();boolean percent=!text(effect,"operation","add_value").equals("add_value")||percentAttribute.test(id)||masteryFraction(id);
            lines.add(signed(number(effect,"amount",0)*rank*(percent?100:1))+(percent?"%":"")+" "+attributeName.apply(id));
        } else if(type.equals("mastery:spell_modifier")||type.equals("spell_modifier")) {
            if(effect.has("spell_level"))lines.add(signed(number(effect,"spell_level",0)*rank)+" Spell Level");
            for(String key:List.of("mana_multiplier","cooldown_multiplier","cast_time_multiplier"))if(effect.has(key))
                lines.add(signed((Math.pow(number(effect,key,1),rank)-1)*100)+"% "+switch(key){case "mana_multiplier"->"Mana Cost";case "cooldown_multiplier"->"Cooldown";default->"Cast Time";});
        } else if(type.equals("mastery:bonus")||type.equals("bonus")) {
            String label=switch(text(effect,"key","")){case "active_capacity","spell_slots"->"Spell Slots";case "modifier_slots"->"Modifier Slots";default->text(effect,"key","Bonus").replace('_',' ');};
            lines.add(signed(number(effect,"amount",0)*rank)+" "+label);
        } else if(effect.has("description")&&!effect.get("description").getAsString().isBlank())lines.add(effect.get("description").getAsString());
        return List.copyOf(lines);
    }
    /** Mastery's registered additive fractions are stored in ordinary ranged attributes. */
    public static boolean masteryFraction(String id) {
        if(id.equals("mastery:elemental_damage")||id.equals("mastery:proc_chance"))return true;
        if(!id.startsWith("mastery:"))return false;
        String path=id.substring(8);
        for(String school:List.of("fire","ice","lightning","holy","ender","blood","evocation","nature","eldritch","slashing","piercing","blunt"))
            for(String suffix:List.of("weapon_damage","conversion","damage","attunement","potency","mitigation"))
                if(path.equals(school+"_"+suffix))return true;
        return false;
    }
    public static String signed(double value){if(Math.abs(value)<.0000001)value=0;return (value>=0?"+":"")+java.math.BigDecimal.valueOf(value).setScale(2,java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();}
    private static String text(JsonObject j,String k,String fallback){return j.has(k)?j.get(k).getAsString():fallback;}
    private static double number(JsonObject j,String k,double fallback){return j.has(k)?j.get(k).getAsDouble():fallback;}
}
