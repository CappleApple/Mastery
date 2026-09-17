package com.cappleapple.mastery;

import com.cappleapple.mastery.spells.SpellService;
import com.cappleapple.mastery.effects.EffectService;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import java.util.*;

/** Vanilla usage adapters. Integrations emit the same event contexts through MasteryAPI. */
public final class MasteryEvents {
    private static final Map<UUID,Vec3> POSITIONS=new HashMap<>();
    private static final Map<UUID,Long> CRITICAL=new HashMap<>();
    private MasteryEvents(){}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p)MasteryRuntime.login(p);}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){
        if(e.getEntity() instanceof ServerPlayer p){MasteryRuntime.logout(p);POSITIONS.remove(p.getUUID());CRITICAL.remove(p.getUUID());}
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e){
        if(e.getEntity() instanceof ServerPlayer p){SpellService.interrupt(p,"respawn");MasteryRuntime.login(p);}
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){
        if(e.getEntity() instanceof ServerPlayer p){SpellService.interrupt(p,"dimension changed");POSITIONS.remove(p.getUUID());EffectService.rebuild(p);MasteryRuntime.sync(p);}
    }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post e) {
        if(!(e.getEntity() instanceof ServerPlayer p))return;
        MasteryRuntime.tick(p);
        if(p.tickCount%20==0) {
            Vec3 previous=POSITIONS.put(p.getUUID(),p.position());
            if(previous!=null&&!p.getAbilities().flying&&!p.isPassenger()&&p.onGround()) {
                double distance=previous.distanceTo(p.position());
                if(distance>0&&distance<20){JsonObject j=new JsonObject();j.addProperty("distance",distance);j.addProperty("sprinting",p.isSprinting());MasteryRuntime.usage(p,"travel",j);}
            }
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void equipment(LivingEquipmentChangeEvent e){if(e.getEntity() instanceof ServerPlayer p)EffectService.refresh(p);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void attack(AttackEntityEvent e){if(e.getEntity() instanceof ServerPlayer p)EffectService.refresh(p);}
    @SubscribeEvent public static void critical(CriticalHitEvent e) {
        if(e.isCriticalHit()&&e.getEntity() instanceof ServerPlayer p)CRITICAL.put(p.getUUID(),p.level().getGameTime());
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void projectileSpawn(EntityJoinLevelEvent e) {
        if(!e.getLevel().isClientSide&&e.getEntity() instanceof Projectile projectile&&projectile.getOwner() instanceof ServerPlayer player) {
            EffectService.refresh(player);
            var tag=projectile.getPersistentData();
            if(!tag.contains("mastery_weapon_context"))tag.putString("mastery_weapon_context",SpellService.combatContext(player));
            if(!tag.contains("mastery_weapon_item"))tag.putString("mastery_weapon_item",BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).toString());
            if(projectile instanceof AbstractArrow arrow&&arrow.getWeaponItem()!=null)tag.putString("mastery_weapon_item",BuiltInRegistries.ITEM.getKey(arrow.getWeaponItem().getItem()).toString());
        }
    }
    @SubscribeEvent public static void damage(LivingDamageEvent.Post e) {
        if(e.getSource().getEntity() instanceof ServerPlayer p&&e.getEntity()!=p&&e.getNewDamage()>0)
            MasteryRuntime.usage(p,"damage",damageContext(p,e.getSource(),e.getEntity(),e.getNewDamage(),false));
        if(e.getEntity() instanceof ServerPlayer p&&e.getNewDamage()>0)SpellService.interrupt(p,"damage");
    }
    @SubscribeEvent public static void death(LivingDeathEvent e) {
        if(e.getSource().getEntity() instanceof ServerPlayer p&&e.getEntity()!=p)MasteryRuntime.usage(p,"kill",damageContext(p,e.getSource(),e.getEntity(),0,true));
        if(e.getEntity() instanceof ServerPlayer p)SpellService.interrupt(p,"death");
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void shield(LivingShieldBlockEvent e) {
        if(e.getEntity() instanceof ServerPlayer p&&e.getBlocked()&&e.getBlockedDamage()>0) {
            Entity attacker=e.getDamageSource().getEntity();
            var data=damageContext(p,e.getDamageSource(),attacker,e.getBlockedDamage(),false);
            data.addProperty("context","mastery:shield");data.addProperty("item",BuiltInRegistries.ITEM.getKey(p.getUseItem().getItem()).toString());
            MasteryRuntime.usage(p,"block",data);
        }
    }
    public static JsonObject damageContext(ServerPlayer p,DamageSource source,Entity target,double amount,boolean kill) {
        JsonObject j=new JsonObject();
        j.addProperty("damage",amount);j.addProperty("amount",amount);j.addProperty("kill",kill);
        j.addProperty("critical",CRITICAL.getOrDefault(p.getUUID(),-1L)==p.level().getGameTime()||(source.getDirectEntity() instanceof AbstractArrow arrow&&arrow.isCritArrow()));
        j.addProperty("projectile",source.getDirectEntity() instanceof Projectile);
        j.addProperty("damage_type",source.typeHolder().unwrapKey().map(k->k.location().toString()).orElse(""));
        com.cappleapple.mastery.integration.SchoolDamage.addContext(source,j);
        if(target!=null){j.addProperty("entity",BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString());j.addProperty("target_id",target.getId());j.addProperty("distance",p.distanceTo(target));}
        if(source.getDirectEntity() instanceof Projectile projectile) {
            var tag=projectile.getPersistentData();
            j.addProperty("weapon_context",tag.getString("mastery_weapon_context"));
            j.addProperty("item",tag.getString("mastery_weapon_item"));
        }
        return j;
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void block(BlockEvent.BreakEvent e) {
        if(e.getPlayer() instanceof ServerPlayer p&&!e.isCanceled()) {
            JsonObject j=new JsonObject();j.addProperty("block",BuiltInRegistries.BLOCK.getKey(e.getState().getBlock()).toString());MasteryRuntime.usage(p,"block_break",j);
        }
    }
    @SubscribeEvent public static void craft(PlayerEvent.ItemCraftedEvent e) {
        if(e.getEntity() instanceof ServerPlayer p){JsonObject j=new JsonObject();j.addProperty("item",BuiltInRegistries.ITEM.getKey(e.getCrafting().getItem()).toString());j.addProperty("amount",e.getCrafting().getCount());MasteryRuntime.usage(p,"craft",j);}
    }
    @SubscribeEvent public static void smelt(PlayerEvent.ItemSmeltedEvent e) {
        if(e.getEntity() instanceof ServerPlayer p){JsonObject j=new JsonObject();j.addProperty("item",BuiltInRegistries.ITEM.getKey(e.getSmelting().getItem()).toString());j.addProperty("amount",e.getSmelting().getCount());MasteryRuntime.usage(p,"smelt",j);}
    }
    @SubscribeEvent public static void advancement(AdvancementEvent.AdvancementEarnEvent e) {
        if(e.getEntity() instanceof ServerPlayer p){JsonObject j=new JsonObject();j.addProperty("advancement",e.getAdvancement().id().toString());MasteryRuntime.usage(p,"advancement",j);}
    }
    @SubscribeEvent public static void brew(net.neoforged.neoforge.event.brewing.PlayerBrewedPotionEvent e) {
        if(e.getEntity() instanceof ServerPlayer p)MasteryRuntime.usage(p,"brew",new JsonObject());
    }
    @SubscribeEvent public static void consume(LivingEntityUseItemEvent.Finish e) {
        if(e.getEntity() instanceof ServerPlayer p&&e.getItem().has(net.minecraft.core.component.DataComponents.FOOD)){JsonObject j=new JsonObject();j.addProperty("item",BuiltInRegistries.ITEM.getKey(e.getItem().getItem()).toString());MasteryRuntime.usage(p,"consume",j);}
    }
}
