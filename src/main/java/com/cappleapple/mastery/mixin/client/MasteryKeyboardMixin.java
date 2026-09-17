package com.cappleapple.mastery.mixin.client;

import com.cappleapple.mastery.client.ContextualInput;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cancel before vanilla drop/inventory/swap handling only when the contextual slot is bound. */
@Mixin(KeyboardHandler.class)
public abstract class MasteryKeyboardMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void mastery$contextualKey(long window, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        if (ContextualInput.key(window, key, scanCode, action)) ci.cancel();
    }
}
