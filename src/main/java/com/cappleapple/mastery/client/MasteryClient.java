package com.cappleapple.mastery.client;

import com.cappleapple.mastery.client.gui.MasteryScreen;
import com.cappleapple.mastery.network.MasteryNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

/** Physical-client entry points. Common initialization never resolves this class. */
@EventBusSubscriber(modid = "mastery", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class MasteryClient {
    public static final KeyMapping FOCUS_NODE = new KeyMapping("key.mastery.focus_node", KeyConflictContext.GUI, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F, "key.categories.mastery");
    public static final KeyMapping OPEN = key("open", GLFW.GLFW_KEY_M);
    public static final KeyMapping PREVIOUS_PAGE=key("previous_spell_page",GLFW.GLFW_KEY_LEFT_BRACKET);
    public static final KeyMapping NEXT_PAGE=key("next_spell_page",GLFW.GLFW_KEY_RIGHT_BRACKET);
    public static final KeyMapping[] ACTIVE_SLOTS = {
            key("active_slot_1", GLFW.GLFW_KEY_Q), key("active_slot_2", GLFW.GLFW_KEY_E),
            key("active_slot_3", GLFW.GLFW_KEY_F), key("active_slot_4", GLFW.GLFW_KEY_C)
    };
    private MasteryClient() {}
    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key.mastery." + name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, code, "key.categories.mastery");
    }
    @SubscribeEvent public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN);event.register(FOCUS_NODE);event.register(PREVIOUS_PAGE);event.register(NEXT_PAGE);
        for (KeyMapping mapping : ACTIVE_SLOTS) event.register(mapping);
    }
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        MasteryNetwork.setClientReceiver((kind, json) -> {
            switch (kind) {
                case "definitions" -> ClientState.acceptDefinitions(json);
                case "charge" -> ChargeHud.accept(json);
                case "progress" -> ClientState.acceptProgress(json);
                case "relayout" -> ClientState.reorganize();
                case "editor_definition" -> com.cappleapple.mastery.client.gui.editor.EditorClient.acceptDefinition(json);
                case "editor_status" -> com.cappleapple.mastery.client.gui.editor.EditorClient.acceptStatus(json);
                case "export" -> com.cappleapple.mastery.client.export.ClientExport.accept(json);
                default -> { }
            }
        });
    }

    @EventBusSubscriber(modid = "mastery", value = Dist.CLIENT)
    public static final class Events {
        @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            com.cappleapple.mastery.client.gui.ClassSelectionScreen.enforce();
            if(mc.player!=null)NativeSpellHud.view(io.redspace.ironsspellbooks.player.ClientMagicData.getSpellSelectionManager());
            while(PREVIOUS_PAGE.consumeClick()) {ContextualInput.releaseAll();ClientState.cycleBindingPage(-1);}
            while(NEXT_PAGE.consumeClick()) {ContextualInput.releaseAll();ClientState.cycleBindingPage(1);}
            ClientState.bindingPage=ClientState.progress().bindingMode().equals("quick_cast")?Math.min(ClientState.bindingPage,Math.max(0,(ClientState.capacity()-1)/4)):0;
            while (OPEN.consumeClick()) if (mc.player != null && mc.screen == null) mc.setScreen(new MasteryScreen());
            if (mc.screen != null || mc.player == null || !mc.isWindowActive()) ContextualInput.releaseAll();
        }
        @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
            ContextualInput.clear();NativeSpellHud.clear();ChargeHud.clear();
            com.cappleapple.mastery.client.gui.editor.EditorClient.clear();
            ClientState.clear();
        }
    }
}
