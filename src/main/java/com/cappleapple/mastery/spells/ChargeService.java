package com.cappleapple.mastery.spells;

import com.cappleapple.mastery.MasteryRuntime;
import com.cappleapple.mastery.data.ChargeDefinition;
import com.cappleapple.mastery.effects.EffectService;
import com.cappleapple.mastery.mixin.integration.IronMagicDataAccessor;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.*;
import io.redspace.ironsspellbooks.entity.spells.fireball.MagicFireball;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import java.util.*;

/** Adjusts native cast duration and delegates each configured burst to the native spell's onCast. */
public final class ChargeService {
    private static final class Charge {
        String spell;int slot,levels,base,baseLevel,tempoUnits=1,committingUnits;double strength=-1;long started,released=-1;boolean completed;
        ChargeDefinition options;
        Charge(String spell,int slot,int levels,long started,ChargeDefinition options){this.spell=spell;this.slot=slot;this.levels=levels;this.started=started;this.options=options;}
        double stages(long now){return options.stages((released<0?now:released)-started,base,levels);}
    }
    private record Burst(String spell,int level,int remaining,long next,int interval,String dimension){}
    private static final Map<UUID,Charge> CHARGES=new HashMap<>();
    private static final Map<UUID,Burst> BURSTS=new HashMap<>();
    private ChargeService(){}
    public static int stackedLevels(ServerPlayer player,String spell) {
        return Math.clamp(SpellService.grantingRank(player,spell),1,64);
    }
    public static void prepare(ServerPlayer player,String spell,int slot) {
        clear(player);
        var definition=MasteryRuntime.definitions().spells().get(spell);
        if(definition==null)return;
        var charge=com.cappleapple.mastery.data.ChargeDefinition.parse(spell,SpellService.spellSettings(player,spell).getAsJsonObject("charge"));
        if(charge.enabled()&&SpellRegistry.getSpell(spell).getCastType()!=CastType.CONTINUOUS) {
            var state=new Charge(spell,slot,stackedLevels(player,spell),player.level().getGameTime(),charge);
            state.baseLevel=SpellService.effectiveLevel(player,spell);CHARGES.put(player.getUUID(),state);
        }
    }
    public static int duration(ServerPlayer player,String spell,int nativeTicks) {
        var charge=CHARGES.get(player.getUUID());
        if(charge==null||!charge.spell.equals(spell)||!SpellService.initiating(player,spell))return nativeTicks;
        charge.base=nativeTicks==0?charge.options.instantBaseTicks():nativeTicks;
        int total=(int)Math.min(Integer.MAX_VALUE,(long)charge.base+charge.options.extraTicks(charge.levels));
        var hud=new com.google.gson.JsonObject();hud.addProperty("spell",spell);hud.addProperty("base",charge.base);
        hud.addProperty("levels",Math.clamp(charge.levels,1,charge.options.maxLevels()));hud.addProperty("ticks_per_level",charge.options.ticksPerLevel());
        hud.addProperty("total",total);
        var markers=new com.google.gson.JsonArray();int previousCost=1;
        double scale=charge.options.spellLevelsPerStage();
        var nativeSpell=SpellRegistry.getSpell(spell);
        for(int bonus=1;bonus<=Math.floor(Math.clamp(charge.levels,1,charge.options.maxLevels())*scale);bonus++) {
            int level=(int)Math.clamp((long)charge.baseLevel+bonus,1,Integer.MAX_VALUE);
            int cost=com.cappleapple.mastery.integration.TempoHeldCharges.units(player,nativeSpell,charge.baseLevel,level);
            if(cost>previousCost)markers.add(charge.base+(int)Math.ceil(bonus/scale*charge.options.ticksPerLevel()));
            previousCost=cost;
        }
        hud.add("charge_cost_markers",markers);
        com.cappleapple.mastery.network.MasteryNetwork.send(player,"charge",hud.toString());
        return total;
    }
    public static void release(ServerPlayer player,int slot) {
        var charge=CHARGES.get(player.getUUID());
        if(charge==null||charge.slot!=slot||charge.released>=0)return;
        charge.released=player.level().getGameTime();
        var magic=MagicData.getPlayerMagicData(player);
        if(magic.isCasting()&&magic.getCastingSpellId().equals(charge.spell))
            ((IronMagicDataAccessor)magic).mastery$remaining((int)Math.max(0,charge.base-(charge.released-charge.started)));
    }
    private static double strength(ServerPlayer player,Charge charge) {
        if(charge.strength>=0)return charge.strength;
        // Native casting can consume its first tick in the press tick. At native completion the full window is charged.
        if(charge.released<0&&MagicData.getPlayerMagicData(player).getCastDurationRemaining()<=0)
            return Math.clamp(charge.levels,1,charge.options.maxLevels());
        return charge.stages(player.level().getGameTime());
    }
    public static int nativeLevel(ServerPlayer player,String spell,int original) {
        var charge=CHARGES.get(player.getUUID());
        if(charge==null||!charge.spell.equals(spell))return original;
        double stages=strength(player,charge),scale=charge.options.spellLevelsPerStage();
        var nativeSpell=SpellRegistry.getSpell(spell);
        int budget=com.cappleapple.mastery.integration.TempoHeldCharges.budget(player,nativeSpell,charge.baseLevel);
        int bonus=(int)Math.floor(stages*scale),level=original;
        while(bonus>=0) {
            level=(int)Math.clamp(original+(long)bonus,1,Integer.MAX_VALUE);
            int cost=com.cappleapple.mastery.integration.TempoHeldCharges.units(player,nativeSpell,charge.baseLevel,level);
            if(cost<=Math.max(1,budget)||bonus==0){charge.tempoUnits=cost;break;}
            bonus--;
            stages=Math.min(stages,Math.max(0,(bonus+1)/scale-1.0/charge.options.ticksPerLevel()));
        }
        charge.strength=stages;
        return level;
    }
    public static int beginTempoCommit(ServerPlayer player,String spell) {
        var charge=CHARGES.get(player.getUUID());
        if(charge==null||!charge.spell.equals(spell)||!charge.completed)return 1;
        charge.committingUnits=charge.tempoUnits;return charge.tempoUnits;
    }
    public static void finishTempoCommit(ServerPlayer player,String spell) {
        var charge=CHARGES.get(player.getUUID());
        if(charge==null||!charge.spell.equals(spell)||charge.committingUnits==0)return;
        int units=charge.committingUnits;charge.committingUnits=0;
        com.cappleapple.mastery.integration.TempoHeldCharges.finish(player,spell,units);
    }
    public static void cast(ServerPlayer player,String spell,int level) {
        var charge=CHARGES.get(player.getUUID());
        if(charge==null||charge.completed||!charge.spell.equals(spell))return;
        charge.strength=strength(player,charge);charge.completed=true;
        int repeats=charge.options.repeats(charge.strength);
        if(repeats>0)BURSTS.put(player.getUUID(),new Burst(spell,level,repeats,player.level().getGameTime()+charge.options.burstIntervalTicks(),charge.options.burstIntervalTicks(),player.level().dimension().location().toString()));
    }
    @SubscribeEvent public static void projectile(EntityJoinLevelEvent event) {
        if(com.cappleapple.mastery.mechanics.ProcSpellCaster.casting())return;
        if(event.getEntity() instanceof MagicFireball fireball&&fireball.getOwner() instanceof ServerPlayer player) {
            var charge=CHARGES.get(player.getUUID());
            if(charge!=null&&charge.spell.equals("irons_spellbooks:fireball")&&charge.completed) {
                double stages=charge.strength;
                ((NativeProjectileScale)fireball).mastery$setScale((float)Math.min(16,1+stages*charge.options.fireballSizePerStage()));
                fireball.setExplosionRadius((float)Math.min(64,fireball.getExplosionRadius()*(1+stages*charge.options.fireballRadiusPerStage())));
            }
        }
    }
    public static void tick(ServerPlayer player) {
        var magic=MagicData.getPlayerMagicData(player);var charge=CHARGES.get(player.getUUID());
        if(charge!=null&&(!magic.isCasting()||!magic.getCastingSpellId().equals(charge.spell))) {CHARGES.remove(player.getUUID());com.cappleapple.mastery.network.MasteryNetwork.send(player,"charge","{}");}
        var burst=BURSTS.get(player.getUUID());
        if(burst==null)return;
        if(!player.isAlive()||player.isSpectator()||!SpellService.owned(player,burst.spell)||!player.level().dimension().location().toString().equals(burst.dimension)){clear(player);return;}
        if(player.level().getGameTime()<burst.next)return;
        var spell=SpellRegistry.getSpell(burst.spell);
        spell.onCast(player.level(),burst.level,player,CastSource.SPELLBOOK,magic);
        if(burst.remaining<=1)BURSTS.remove(player.getUUID());
        else BURSTS.put(player.getUUID(),new Burst(burst.spell,burst.level,burst.remaining-1,player.level().getGameTime()+burst.interval,burst.interval,burst.dimension));
    }
    public static void clear(ServerPlayer player){if(CHARGES.remove(player.getUUID())!=null)com.cappleapple.mastery.network.MasteryNetwork.send(player,"charge","{}");BURSTS.remove(player.getUUID());}
}
