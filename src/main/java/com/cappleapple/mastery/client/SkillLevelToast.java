package com.cappleapple.mastery.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/** One notification per skill increase, with sound when its toast actually appears. */
public final class SkillLevelToast extends SystemToast {
    private boolean sounded;
    public SkillLevelToast(String name, int level) {
        super(new SystemToastId(), Component.translatable("toast.mastery.skill_level_up", name),
                Component.translatable("toast.mastery.skill_level", level));
    }
    @Override public Visibility render(GuiGraphics graphics, ToastComponent toasts, long elapsed) {
        if (!sounded) {
            sounded = true;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.0F, 0.65F));
        }
        return super.render(graphics, toasts, elapsed);
    }
}
