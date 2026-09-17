package com.cappleapple.mastery.mixin.client;
import com.cappleapple.mastery.client.ChargeHud;
import io.redspace.ironsspellbooks.gui.overlays.CastBarOverlay;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=CastBarOverlay.class,remap=false)
public abstract class IronCastBarMixin {
    @Inject(method="render",at=@At("HEAD"),cancellable=true)
    private void mastery$chargeSegments(GuiGraphics graphics,DeltaTracker delta,CallbackInfo callback){
        if(ChargeHud.render(graphics))callback.cancel();
    }
}
