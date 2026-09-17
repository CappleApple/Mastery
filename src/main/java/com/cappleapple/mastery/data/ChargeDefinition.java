package com.cappleapple.mastery.data;
import com.google.gson.JsonObject;

/** Charge upgrades extend an existing native spell cast. Durations are server ticks. */
public record ChargeDefinition(boolean enabled,int ticksPerLevel,int instantBaseTicks,int maxLevels,
        double spellLevelsPerStage,double fireballSizePerStage,double fireballRadiusPerStage,
        double extraCastsPerStage,int burstIntervalTicks) {
    public static final ChargeDefinition OFF=new ChargeDefinition(false,20,10,16,0,0,0,0,3);
    public ChargeDefinition {
        if(ticksPerLevel<1||ticksPerLevel>1200||instantBaseTicks<0||instantBaseTicks>1200||maxLevels<1||maxLevels>64||burstIntervalTicks<1||burstIntervalTicks>200)
            throw new IllegalArgumentException("charge durations or level limit out of range");
        for(double value:new double[]{spellLevelsPerStage,fireballSizePerStage,fireballRadiusPerStage,extraCastsPerStage})
            if(!Double.isFinite(value)||value<0||value>16)throw new IllegalArgumentException("charge scaling must be between 0 and 16");
    }
    public static ChargeDefinition defaults(String spell) {
        return switch(spell) {
            case "irons_spellbooks:fireball" -> new ChargeDefinition(true,20,10,16,0,.5,.5,0,3);
            case "irons_spellbooks:firebolt" -> new ChargeDefinition(true,20,10,16,0,0,0,1,3);
            default -> OFF;
        };
    }
    public static ChargeDefinition parse(String spell,JsonObject json) {
        var d=defaults(spell);
        return new ChargeDefinition(json.has("enabled")?json.get("enabled").getAsBoolean():d.enabled,
                integer(json,"ticks_per_level",d.ticksPerLevel),integer(json,"instant_base_ticks",d.instantBaseTicks),integer(json,"max_levels",d.maxLevels),
                number(json,"spell_levels_per_stage",d.spellLevelsPerStage),number(json,"fireball_size_per_stage",d.fireballSizePerStage),
                number(json,"fireball_radius_per_stage",d.fireballRadiusPerStage),number(json,"extra_casts_per_stage",d.extraCastsPerStage),integer(json,"burst_interval_ticks",d.burstIntervalTicks));
    }
    private static int integer(JsonObject j,String k,int d){if(!j.has(k))return d;try{return j.get(k).getAsBigDecimal().intValueExact();}catch(RuntimeException e){throw new IllegalArgumentException("charge."+k+" must be an integer");}}
    private static double number(JsonObject j,String k,double d){return j.has(k)?j.get(k).getAsDouble():d;}
    public int extraTicks(int levels){return Math.clamp(levels,1,maxLevels)*ticksPerLevel;}
    public double stages(long elapsed,int base,int levels){return Math.clamp((elapsed-base)/(double)ticksPerLevel,0,Math.clamp(levels,1,maxLevels));}
    public int repeats(double stages){return (int)Math.clamp(Math.floor(stages*extraCastsPerStage),0,64);}
}
