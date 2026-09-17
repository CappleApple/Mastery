package com.cappleapple.mastery.client;

import com.cappleapple.mastery.network.MasteryNetwork;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

/** Intercepts only a bound contextual action. Empty slots leave the original key/mouse path untouched. */
public final class ContextualInput {
    private static final boolean[] HELD = new boolean[4];
    private static final int[] HELD_SLOTS=new int[4];
    private ContextualInput() {}

    public static boolean key(long window, int key, int scanCode, int action) {
        Minecraft mc = Minecraft.getInstance();
        if (window != mc.getWindow().getWindow()) return false;
        for (int slot = 0; slot < 4; slot++) if (MasteryClient.ACTIVE_SLOTS[slot].matches(key, scanCode)
                && (action == GLFW.GLFW_RELEASE && HELD[slot] || MasteryClient.ACTIVE_SLOTS[slot].isActiveAndMatches(InputConstants.getKey(key, scanCode)))
                && handle(slot, action)) return true;
        return false;
    }
    public static boolean mouse(long window, int button, int action) {
        Minecraft mc = Minecraft.getInstance();
        if (window != mc.getWindow().getWindow()) return false;
        for (int slot = 0; slot < 4; slot++) if (MasteryClient.ACTIVE_SLOTS[slot].matchesMouse(button)
                && (action == GLFW.GLFW_RELEASE && HELD[slot] || MasteryClient.ACTIVE_SLOTS[slot].isActiveAndMatches(InputConstants.Type.MOUSE.getOrCreate(button)))
                && handle(slot, action)) return true;
        return false;
    }
    private static boolean handle(int slot, int action) {
        Minecraft mc = Minecraft.getInstance();
        ActiveInputPolicy.Action type = switch(action) {
            case GLFW.GLFW_PRESS -> ActiveInputPolicy.Action.PRESS;
            case GLFW.GLFW_RELEASE -> ActiveInputPolicy.Action.RELEASE;
            case GLFW.GLFW_REPEAT -> ActiveInputPolicy.Action.REPEAT;
            default -> null;
        };
        if (type == null) return false;
        var decision = ActiveInputPolicy.decide(type, HELD[slot], !ClientState.boundSpell(ClientState.inputSlot(slot)).isBlank(),
                mc.player != null && mc.screen == null && mc.isWindowActive());
        switch(decision) {
            case START -> { HELD[slot] = true; NativeSpellHud.lastCast=ClientState.boundSpell(ClientState.inputSlot(slot)); HELD_SLOTS[slot]=ClientState.inputSlot(slot); MasteryNetwork.sendAction("press", "", "", ClientState.inputSlot(slot)); }
            case RELEASE -> { HELD[slot] = false; MasteryNetwork.sendAction("release", "", "", HELD_SLOTS[slot]); }
            default -> { }
        }
        return decision != ActiveInputPolicy.Decision.PASS;
    }
    public static void releaseAll() {
        for (int slot = 0; slot < 4; slot++) if (HELD[slot]) {
            HELD[slot] = false;
            if (Minecraft.getInstance().getConnection() != null) MasteryNetwork.sendAction("release", "", "", HELD_SLOTS[slot]);
        }
    }
    public static void clear() { java.util.Arrays.fill(HELD, false); }
}
