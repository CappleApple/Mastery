package com.cappleapple.mastery.data;

import com.google.gson.JsonObject;
import java.util.Set;

/** Tree values remain literal defaults; resolution happens against the current datapack snapshot. */
public record UnlockPresentation(String fillDirection, String progressSound, String completeSound, int holdDelayMs) {
    public static final UnlockPresentation INHERIT = new UnlockPresentation("default", "default", "default");
    public static final UnlockPresentation DEFAULTS = new UnlockPresentation("vertical", "minecraft:entity.experience_orb.pickup", "minecraft:block.beacon.power_select",500);
    public UnlockPresentation(String fillDirection,String progressSound,String completeSound){this(fillDirection,progressSound,completeSound,-1);}
    public UnlockPresentation {
        if(holdDelayMs < -1 || holdDelayMs > 60000)throw new IllegalArgumentException("unlock.hold_delay_ms must be default or 0..60000");
        if (!Set.of("default", "vertical", "horizontal").contains(fillDirection))
            throw new IllegalArgumentException("unlock.fill_direction must be default, vertical, or horizontal");
        for (String sound : new String[]{progressSound, completeSound})
            if (sound == null || !(sound.equals("default") || sound.equals("none") || sound.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")))
                throw new IllegalArgumentException("unlock sounds must be default, none, or a sound event ID");
    }
    public UnlockPresentation resolve(UnlockPresentation defaults) {
        return new UnlockPresentation(inherit(fillDirection, defaults.fillDirection), inherit(progressSound, defaults.progressSound), inherit(completeSound, defaults.completeSound),holdDelayMs<0?defaults.holdDelayMs:holdDelayMs);
    }
    private static String inherit(String value, String fallback) { return value.equals("default") ? fallback : value; }
    public static UnlockPresentation parse(JsonObject json) {
        return new UnlockPresentation(text(json,"fill_direction"),text(json,"progress_sound"),text(json,"complete_sound"),delay(json));
    }
    private static int delay(JsonObject json){
        if(!json.has("hold_delay_ms"))return -1;
        var value=json.get("hold_delay_ms");
        if(value.isJsonPrimitive()&&value.getAsJsonPrimitive().isString()&&value.getAsString().equals("default"))return -1;
        try{if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException();int delay=value.getAsBigDecimal().intValueExact();if(delay<0)throw new IllegalArgumentException();return delay;}
        catch(RuntimeException error){throw new IllegalArgumentException("unlock.hold_delay_ms must be default or an integer from 0 to 60000");}
    }
    private static String text(JsonObject json,String field) {
        if(!json.has(field))return "default";
        if(!json.get(field).isJsonPrimitive()||!json.getAsJsonPrimitive(field).isString())throw new IllegalArgumentException("unlock."+field+" must be a string");
        return json.get(field).getAsString();
    }
    public JsonObject toJson() {
        JsonObject json=new JsonObject();json.addProperty("fill_direction",fillDirection);
        json.addProperty("progress_sound",progressSound);json.addProperty("complete_sound",completeSound);
        if(holdDelayMs<0)json.addProperty("hold_delay_ms","default");else json.addProperty("hold_delay_ms",holdDelayMs);return json;
    }
}
