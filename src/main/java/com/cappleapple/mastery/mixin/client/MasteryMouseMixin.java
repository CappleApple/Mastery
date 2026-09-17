package com.cappleapple.mastery.mixin.client;

import com.cappleapple.mastery.client.ContextualInput;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MasteryMouseMixin {
    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void mastery$contextualMouse(long window, int button, int action, int modifiers, CallbackInfo ci) {
        if (ContextualInput.mouse(window, button, action)) ci.cancel();
    }
}
