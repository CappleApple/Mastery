package com.cappleapple.mastery.client.gui;

import com.cappleapple.mastery.client.ClientState;
import com.google.gson.*;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Summaries are derived from the same scripts that run on the server. */
public final class ScriptText {
    private ScriptText() {}
    public static String trigger(JsonObject trigger) {
        String event=switch(text(trigger,"event","hit")){case "hurt"->"when hit";case "death"->"on death";case "kill"->"on kill";default->"on hit";};
        var actions=actions(trigger,"actions");
        double chance=number(trigger,"chance",1);
        var player=net.minecraft.client.Minecraft.getInstance().player;
        var attribute=net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.getHolder(net.minecraft.resources.ResourceLocation.parse("mastery:proc_chance"));
        if(player!=null&&attribute.isPresent())chance=com.cappleapple.mastery.mechanics.MechanicsRules.chance(chance,player.getAttributeValue(attribute.get()));
        String result=String.join("; ",actions)+" "+event+(chance<1?" ("+format(chance*100)+"%)":"");
        if(trigger.has("conditions")&&!trigger.getAsJsonArray("conditions").isEmpty())result+="; "+String.join("; ",conditions(trigger.getAsJsonArray("conditions")));
        if(number(trigger,"cooldown",0)>0)result+="; "+format(number(trigger,"cooldown",0)/20)+"s cooldown";
        return result;
    }
    public static String damageScope(JsonObject filter) {
        var copy=filter.deepCopy();copy.addProperty("type","damage");var list=new JsonArray();list.add(copy);
        String result=String.join("; ",conditions(list));return result.isBlank()?"":"; "+result;
    }
    public static List<Component> keyword(String id) {
        var definition=ClientState.definitions().keywords().get(id);if(definition==null)return List.of();
        var lines=new ArrayList<Component>();lines.add(Component.literal(keywordName(id)));
        if(!text(definition,"description","").isBlank())lines.add(Component.literal(text(definition,"description","")));
        lines.add(Component.literal("Up to "+format(number(definition,"max_stacks",1))+" stacks; "+(number(definition,"duration",100)==0?"no expiration":format(number(definition,"duration",100)/20)+"s")));
        if(number(definition,"decay_stacks",0)>0)lines.add(Component.literal("After "+format(number(definition,"decay_delay",0)/20)+"s without reapplication: lose "+format(number(definition,"decay_stacks",0))+" stacks every "+format(number(definition,"decay_interval",20)/20)+"s"));
        for(String field:List.of("stacks_lost_actions","all_stacks_lost_actions")) {
            var loss=actions(definition,field);if(!loss.isEmpty())lines.add(Component.literal((field.equals("stacks_lost_actions")?"On stacks lost: ":"On all stacks lost: ")+String.join("; ",loss)));
        }
        var ticks=actions(definition,"tick_actions");if(!ticks.isEmpty())lines.add(Component.literal("Every "+format(number(definition,"tick_interval",20)/20)+"s: "+String.join("; ",ticks)));
        var threshold=actions(definition,"threshold_actions");if(number(definition,"threshold",0)>0&&!threshold.isEmpty())
            lines.add(Component.literal("At "+format(number(definition,"threshold",0))+" stacks: "+String.join("; ",threshold)+(!definition.has("consume_stacks")||definition.get("consume_stacks").getAsBoolean()?"; consumes stacks":"")));
        return List.copyOf(lines);
    }
    private static List<String> actions(JsonObject definition,String field) {
        if(!definition.has(field))return List.of();var result=new ArrayList<String>();
        for(var element:definition.getAsJsonArray(field)) {
            var action=element.getAsJsonObject();String type=text(action,"type","");if(type.equals("particles"))continue;
            String suffix=(action.has("per_stack")&&action.get("per_stack").getAsBoolean()?" per stack":"")+(action.has("per_rank")&&action.get("per_rank").getAsBoolean()?" per rank":"");
            String amount=format(number(action,"amount",0))+(number(action,"damage_fraction",0)>0?" + "+format(number(action,"damage_fraction",0)*100)+"% of hit damage":"");
            String label=switch(type) {
                case "keyword"->keywordName(text(action,"keyword",""))+(number(action,"stacks",1)>1?" x"+format(number(action,"stacks",1)):"")+suffix;
                case "remove_keyword"->"Remove "+keywordName(text(action,"keyword",""));
                case "damage"->amount+" "+readable(text(action,"element",text(action,"school","damage")))+" damage"+suffix;
                case "heal"->amount+" "+readable(text(action,"element",text(action,"school","irons_spellbooks:holy")))+" healing"+suffix;
                case "lightning"->amount+" lightning damage"+suffix;
                case "spell"->ClientState.name(text(action,"spell",""))+" level "+format(number(action,"level",1));
                case "effect"->readable(text(action,"effect",""))+" "+format(number(action,"amplifier",0)+1)+" for "+format(number(action,"duration",100)/20)+"s";
                default->readable(type);
            };
            String target=text(action,"target","target");
            if(target.equals("self")||target.equals("owner"))label+=" on self";
            if(target.equals("nearby"))label+=" within "+format(number(action,"radius",8))+" blocks"+(action.has("limit")?" (up to "+format(number(action,"limit",0))+" targets)":"");
            if(action.has("conditions")&&!action.getAsJsonArray("conditions").isEmpty())label+=" when "+String.join("; ",conditions(action.getAsJsonArray("conditions")));
            if(action.has("chance"))label+=" ("+format(number(action,"chance",1)*100)+"%)";
            result.add(label);
        }return result;
    }
    private static List<String> conditions(JsonArray conditions) {
        var result=new ArrayList<String>();for(var element:conditions) {
            var c=element.getAsJsonObject();String type=text(c,"type","health");
            if(type.equals("damage")) {
                for(String key:List.of("elements","categories","damage_types","damage_tags"))if(c.has(key)&&!c.getAsJsonArray(key).isEmpty())result.add("with "+String.join(" / ",java.util.stream.StreamSupport.stream(c.getAsJsonArray(key).spliterator(),false).map(v->readable(v.getAsString())).toList())+" damage");
            } else if(type.equals("keyword")) {
                result.add(keywordName(text(c,"keyword",""))+" stacks "+range(c,1,""));
            } else if(type.equals("health")) {
                boolean fraction=text(c,"unit","points").equals("fraction");
                result.add(readable(text(c,"target","target"))+" health "+range(c,fraction?100:1,fraction?"%":""));
            }
        }return result;
    }
    private static String range(JsonObject j,double scale,String suffix) {
        return (j.has("min")?">= "+format(number(j,"min",0)*scale)+suffix:"")+(j.has("min")&&j.has("max")?", ":"")+(j.has("max")?"<= "+format(number(j,"max",0)*scale)+suffix:"");
    }
    public static String keywordName(String id){var d=ClientState.definitions().keywords().get(id);String name=d==null?"":text(d,"name","");return name.isBlank()?readable(id):name;}
    private static String readable(String value){String s=value.substring(value.indexOf(':')+1).replace('_',' ');return s.isEmpty()?s:Character.toUpperCase(s.charAt(0))+s.substring(1);}
    private static String text(JsonObject j,String key,String fallback){return j.has(key)?j.get(key).getAsString():fallback;}
    private static double number(JsonObject j,String key,double fallback){return j.has(key)?j.get(key).getAsDouble():fallback;}
    private static String format(double n){return java.math.BigDecimal.valueOf(n).stripTrailingZeros().toPlainString();}
}
