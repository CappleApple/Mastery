package com.cappleapple.mastery.integration;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import java.lang.reflect.Method;
import java.util.function.Supplier;

/** Optional display adapter using Tempo's own normalization and per-spell override rules. */
public final class TempoSpellStats {
    private TempoSpellStats() {}
    private record Access(Method enabled,Method normalize,Method override,Method multiplier,
                          Supplier<?> normalization,Supplier<?> normal,Supplier<?> shorter,Supplier<?> longer,Supplier<?> spread,
                          Object chargeManager,Method maxCharges) {}
    private static final Access ACCESS=resolve();
    private static boolean warned;
    private static Access resolve() {
        if(!net.neoforged.fml.ModList.get().isLoaded("temponottime"))return null;
        try {
            var config=Class.forName("com.cappleapple.temponottime.config.ServerConfig");
            var manager=Class.forName("com.cappleapple.temponottime.casting.CooldownManager");
            return new Access(config.getMethod("enabled"),
                    Class.forName("com.cappleapple.temponottime.casting.RechargeNormalizer").getMethod("normalizeEffectiveTicks",double.class,double.class,boolean.class,double.class,double.class,double.class,double.class),
                    Class.forName("com.cappleapple.temponottime.config.SpellOverrideManager").getMethod("get",String.class),
                    Class.forName("com.cappleapple.temponottime.config.SpellOverride").getMethod("cooldownMultiplier"),
                    value(config,"RECHARGE_NORMALIZATION_ENABLED"),value(config,"NORMAL_RECHARGE_SECONDS"),value(config,"SHORT_RECHARGE_STRENGTH"),value(config,"LONG_RECHARGE_STRENGTH"),value(config,"NORMALIZATION_SPREAD"),manager.getField("INSTANCE").get(null),
                    manager.getMethod("maxCharges",net.minecraft.world.entity.player.Player.class,AbstractSpell.class,int.class));
        }catch(ReflectiveOperationException|LinkageError error){com.mojang.logging.LogUtils.getLogger().warn("Tempo spell stat adapter unavailable; using native cooldown stats",error);return null;}
    }
    private static Supplier<?> value(Class<?> config,String field)throws ReflectiveOperationException{return (Supplier<?>)config.getField(field).get(null);}
    private static double number(Supplier<?> value){return ((Number)value.get()).doubleValue();}
    /** Server values for owned and previewed skills, including Tempo's overrides and charge events. */
    public static com.google.gson.JsonObject charges(net.minecraft.server.level.ServerPlayer player) {
        var result=new com.google.gson.JsonObject();
        if(ACCESS==null)return result;
        try {
            if(!(boolean)ACCESS.enabled.invoke(null))return result;
            for(String id:com.cappleapple.mastery.MasteryRuntime.definitions().spells().keySet()) {
                var spell=io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell(id);
                int level=com.cappleapple.mastery.spells.SpellService.effectiveLevel(player,id);
                result.addProperty(id,(int)ACCESS.maxCharges.invoke(ACCESS.chargeManager,player,spell,level));
            }
        }catch(ReflectiveOperationException|RuntimeException error){
            if(!warned){warned=true;com.mojang.logging.LogUtils.getLogger().warn("Tempo maximum charge stats could not be read",error);}
        }
        return result;
    }
    public static int cooldown(AbstractSpell spell,int effectiveTicks) {
        if(ACCESS==null)return effectiveTicks;
        try {
            if(!(boolean)ACCESS.enabled.invoke(null))return effectiveTicks;
            double normalized=((Number)ACCESS.normalize.invoke(null,(double)spell.getSpellCooldown(),(double)effectiveTicks,ACCESS.normalization.get(),number(ACCESS.normal),number(ACCESS.shorter),number(ACCESS.longer),number(ACCESS.spread))).doubleValue();
            double multiplier=((Number)ACCESS.multiplier.invoke(ACCESS.override.invoke(null,spell.getSpellId()))).doubleValue();
            double duration=normalized*multiplier;
            return Double.isFinite(duration)&&duration>0?(int)Math.clamp(Math.round(duration),1,Integer.MAX_VALUE):1;
        }catch(ReflectiveOperationException|RuntimeException error){
            if(!warned){warned=true;com.mojang.logging.LogUtils.getLogger().warn("Tempo cooldown stats could not be read; using native cooldown stats",error);}
            return effectiveTicks;
        }
    }
}
