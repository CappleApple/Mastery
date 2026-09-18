package com.cappleapple.mastery.integration;

import com.cappleapple.mastery.spells.*;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Supplier;

/** Optional Tempo adapter. Each paid base charge uses Tempo's normal persisted recharge lifecycle. */
public final class TempoHeldCharges {
    private record Access(Object manager,Method enabled,Method draw,Method maximum,Method data,Method sync,
                          Method override,Method allowed,Supplier<?> charges,Supplier<?> creative,
                          Class<?> dataType,Class<?> instanceType) {}
    private static final Access ACCESS=resolve();
    private static boolean warned;
    private TempoHeldCharges() {}
    private static Access resolve() {
        if(!net.neoforged.fml.ModList.get().isLoaded("temponottime"))return null;
        try {
            var c=Class.forName("com.cappleapple.temponottime.config.ServerConfig");
            var m=Class.forName("com.cappleapple.temponottime.casting.CooldownManager");
            return new Access(m.getField("INSTANCE").get(null),c.getMethod("enabled"),
                    m.getMethod("castingDraw",Player.class,AbstractSpell.class,int.class),m.getMethod("maxCharges",Player.class,AbstractSpell.class,int.class),
                    m.getMethod("data",Player.class),m.getMethod("sync",ServerPlayer.class),
                    Class.forName("com.cappleapple.temponottime.config.SpellOverrideManager").getMethod("get",String.class),
                    Class.forName("com.cappleapple.temponottime.config.SpellOverride").getMethod("chargesAllowed",boolean.class),
                    (Supplier<?>)c.getField("CHARGES_ENABLED").get(null),(Supplier<?>)c.getField("CREATIVE_BYPASSES_CHARGES").get(null),
                    Class.forName("com.cappleapple.temponottime.data.PlayerCooldownData"),Class.forName("com.cappleapple.temponottime.data.CooldownInstance"));
        }catch(ReflectiveOperationException|LinkageError error){warn(error);return null;}
    }
    private static void warn(Throwable error){if(!warned){warned=true;com.mojang.logging.LogUtils.getLogger().warn("Tempo held-charge integration unavailable",error);}}
    private static boolean active(ServerPlayer player,AbstractSpell spell)throws ReflectiveOperationException {
        return ACCESS!=null&&(boolean)ACCESS.enabled.invoke(null)&&(boolean)ACCESS.charges.get()
                &&!(player.isCreative()&&(boolean)ACCESS.creative.get())
                &&(boolean)ACCESS.allowed.invoke(ACCESS.override.invoke(null,spell.getSpellId()),true);
    }
    public static int units(ServerPlayer player,AbstractSpell spell,int baseLevel,int heldLevel) {
        try {
            if(heldLevel<=baseLevel||!active(player,spell))return 1;
            double base=(double)ACCESS.draw.invoke(ACCESS.manager,player,spell,baseLevel);
            double held=(double)ACCESS.draw.invoke(ACCESS.manager,player,spell,heldLevel);
            return HeldChargeCost.units(base,held);
        }catch(ReflectiveOperationException|RuntimeException error){warn(error);return 1;}
    }
    /** Excludes this cast's pending reservation; committed uses still consume the base-level pool. */
    public static int budget(ServerPlayer player,AbstractSpell spell,int baseLevel) {
        try {
            if(!active(player,spell))return Integer.MAX_VALUE;
            Object data=ACCESS.data.invoke(ACCESS.manager,player);
            var uses=(List<?>)ACCESS.dataType.getMethod("forSpell",String.class).invoke(data,spell.getSpellId());
            return Math.max(0,(int)ACCESS.maximum.invoke(ACCESS.manager,player,spell,baseLevel)-uses.size());
        }catch(ReflectiveOperationException|RuntimeException error){warn(error);return 1;}
    }
    /** Called only after Tempo actually added a recharge; preserves total reserve and recovery debt. */
    public static void finish(ServerPlayer player,String spell,int units) {
        if(ACCESS==null||units<=1)return;
        try {
            Object data=ACCESS.data.invoke(ACCESS.manager,player);
            var uses=(List<?>)ACCESS.dataType.getMethod("forSpell",String.class).invoke(data,spell);
            if(uses.isEmpty())return;
            Object first=uses.getLast();var type=ACCESS.instanceType;
            int level=(int)type.getMethod("spellLevel").invoke(first);
            double draw=(double)type.getMethod("castingDraw").invoke(first);
            double duration=(double)type.getMethod("durationTicks").invoke(first);
            double mana=(double)type.getMethod("recoveryManaCost").invoke(first)/units;
            double penalty=(double)type.getMethod("cooldownPenaltyMultiplier").invoke(first);
            boolean reserves=(boolean)type.getMethod("occupiesCastingReserve").invoke(first),load=(boolean)type.getMethod("appliesLoad").invoke(first);
            var recovery=type.getMethod("setRecoveryManaCost",double.class);recovery.invoke(first,mana);
            var add=ACCESS.dataType.getMethod("add",String.class,int.class,double.class,double.class,boolean.class,boolean.class,boolean.class);
            for(int i=1;i<units;i++) {
                Object next=add.invoke(data,spell,level,draw,duration,true,reserves,load);
                recovery.invoke(next,mana);type.getMethod("setCooldownPenaltyMultiplier",double.class).invoke(next,penalty);
            }
            ACCESS.sync.invoke(ACCESS.manager,player);
        }catch(ReflectiveOperationException|RuntimeException error){warn(error);}
    }
}
