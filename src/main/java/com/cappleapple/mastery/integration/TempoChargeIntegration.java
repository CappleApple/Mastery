package com.cappleapple.mastery.integration;

import com.cappleapple.mastery.spells.SpellService;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import java.lang.reflect.Method;

/** Registers Tempo's public charge event only when the optional mod is loaded. */
public final class TempoChargeIntegration {
    private TempoChargeIntegration() {}
    private record Access(Method player,Method spell,Method charges,Method setCharges) {}
    private static boolean initialized,warned;
    public static void initialize() {
        if(initialized)return;initialized=true;
        if(!net.neoforged.fml.ModList.get().isLoaded("temponottime"))return;
        try {
            var type=Class.forName("com.cappleapple.temponottime.api.event.ChargeCalculationEvent").asSubclass(Event.class);
            listen(type,new Access(type.getMethod("getPlayer"),type.getMethod("getSpell"),type.getMethod("getCharges"),type.getMethod("setCharges",int.class)));
        }catch(ReflectiveOperationException|LinkageError error){
            com.mojang.logging.LogUtils.getLogger().warn("Tempo charge event unavailable; extra spell charges will not apply",error);
        }
    }
    private static <T extends Event> void listen(Class<T> type,Access access) {
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL,false,type,event->apply(access,event));
    }
    private static void apply(Access access,Event event) {
        try {
            if(!(access.player.invoke(event) instanceof ServerPlayer player))return;
            var spell=(AbstractSpell)access.spell.invoke(event);
            var modifiers=SpellService.modifiers(player,spell.getSpellId());
            if(modifiers.extraCharges()>0)access.setCharges.invoke(event,modifiers.withCharges((int)access.charges.invoke(event)));
        }catch(ReflectiveOperationException error){
            if(!warned){warned=true;com.mojang.logging.LogUtils.getLogger().warn("Could not apply Mastery spell charges through Tempo's charge event",error);}
        }
    }
}
