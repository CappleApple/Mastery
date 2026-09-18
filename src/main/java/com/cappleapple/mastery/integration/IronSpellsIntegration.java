package com.cappleapple.mastery.integration;

import com.cappleapple.mastery.spells.SpellModifiers;
import com.cappleapple.mastery.spells.SpellService;
import io.redspace.ironsspellbooks.api.events.*;
import io.redspace.ironsspellbooks.api.spells.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.bus.api.SubscribeEvent;

/** Public native spell events compose Mastery modifiers with Iron's casting, mana and cooldown systems. */
public final class IronSpellsIntegration {
    private IronSpellsIntegration() {}
    public static int equipmentCapacity(ServerPlayer player) {
        int capacity=0;
        for(var slot:new EquipmentSlot[]{EquipmentSlot.MAINHAND,EquipmentSlot.OFFHAND,EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET}) {
            var stack=player.getItemBySlot(slot);
            if(!ISpellContainer.isSpellContainer(stack)) continue;
            var container=ISpellContainer.get(stack);
            boolean held=slot==EquipmentSlot.MAINHAND||slot==EquipmentSlot.OFFHAND;
            if(container.isSpellWheel()&&(!held||!container.mustEquip())) capacity+=Math.max(0,container.getMaxSpellCount());
        }
        return capacity;
    }
    @SubscribeEvent public static void requireMasteryAssignment(SpellPreCastEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer player)) return;
        if((event.getCastSource()==CastSource.SPELLBOOK||event.getCastSource()==CastSource.SWORD)&&!SpellService.initiating(player,event.getSpellId())) {
            event.setCanceled(true); player.displayClientMessage(Component.literal("Prepare and cast this spell through Mastery."),true);
        }
    }
    @SubscribeEvent public static void level(ModifySpellLevelEvent event) {
        if(event.getEntity() instanceof ServerPlayer player) {
            int bonus=SpellService.modifiers(player,event.getSpell().getSpellId()).levels();
            if(bonus!=0) event.setLevel(Math.clamp((long)event.getLevel()+bonus,1,Integer.MAX_VALUE));
        }
    }
    @SubscribeEvent public static void mana(SpellOnCastEvent event) {
        if(event.getEntity() instanceof ServerPlayer player) {
            if(!com.cappleapple.mastery.mechanics.ProcSpellCaster.casting()) {
            int level=com.cappleapple.mastery.spells.ChargeService.nativeLevel(player,event.getSpellId(),event.getSpellLevel());
            if(level!=event.getSpellLevel()) {
                var spell=io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell(event.getSpellId());
                int baseCost=spell.getManaCost(event.getSpellLevel());
                event.setManaCost(baseCost>0?SpellModifiers.scale(event.getManaCost(),spell.getManaCost(level)/(double)baseCost):spell.getManaCost(level));
            }
            event.setSpellLevel(level);
            com.cappleapple.mastery.spells.ChargeService.cast(player,event.getSpellId(),level);
            }
            event.setManaCost(SpellModifiers.scale(event.getManaCost(),SpellService.modifiers(player,event.getSpellId()).manaMultiplier()));
        }
    }
    @SubscribeEvent public static void cooldown(SpellCooldownAddedEvent.Pre event) {
        if(event.getEntity() instanceof ServerPlayer player)
            event.setEffectiveCooldown(SpellModifiers.scale(event.getEffectiveCooldown(),SpellService.modifiers(player,event.getSpell().getSpellId()).cooldownMultiplier()));
    }
}
