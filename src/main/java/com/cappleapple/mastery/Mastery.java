package com.cappleapple.mastery;

import com.cappleapple.mastery.commands.MasteryCommands;
import com.cappleapple.mastery.config.MasteryConfig;
import com.cappleapple.mastery.data.MasteryReloadListener;
import com.cappleapple.mastery.effects.EffectService;
import com.cappleapple.mastery.network.MasteryNetwork;
import com.cappleapple.mastery.storage.MasteryAttachments;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.slf4j.Logger;

@Mod(Mastery.MOD_ID)
public final class Mastery {
    public static final String MOD_ID="mastery";
    public static final Logger LOGGER=LogUtils.getLogger();
    public Mastery(IEventBus bus,ModContainer container) {
        MasteryAttachments.TYPES.register(bus);
        com.cappleapple.mastery.elemental.ElementalAttributes.TYPES.register(bus);
        bus.addListener(com.cappleapple.mastery.elemental.ElementalAttributes::attach);
        com.cappleapple.mastery.progression.ExperienceAttributes.TYPES.register(bus);
        bus.addListener(com.cappleapple.mastery.progression.ExperienceAttributes::attach);
        NeoForge.EVENT_BUS.register(com.cappleapple.mastery.elemental.ElementalDamage.class);
        com.cappleapple.mastery.items.MasteryItems.TYPES.register(bus);
        container.registerConfig(ModConfig.Type.SERVER,MasteryConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT,com.cappleapple.mastery.config.MasteryClientConfig.SPEC);
        bus.addListener(MasteryNetwork::register);
        bus.addListener(MasteryRuntime::setup);
        EffectService.initialize();
        com.cappleapple.mastery.mechanics.MechanicsRuntime.initialize();
        com.cappleapple.mastery.crafting.CraftingService.initialize();
        NeoForge.EVENT_BUS.register(com.cappleapple.mastery.crafting.CraftingEvents.class);
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e)->e.addListener(new MasteryReloadListener()));
        NeoForge.EVENT_BUS.addListener(MasteryCommands::register);
        NeoForge.EVENT_BUS.register(MasteryEvents.class);
    }
}
