package com.cappleapple.mastery.mixin.client;
import com.cappleapple.mastery.client.NativeSpellHud;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.redspace.ironsspellbooks.gui.overlays.SpellBarOverlay;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value=SpellBarOverlay.class,remap=false)
public abstract class IronSpellHudMixin {
    @WrapMethod(method="render")
    private void mastery$renderWithActiveSet(GuiGraphics graphics,DeltaTracker delta,Operation<Void> original){
        NativeSpellHud.beginRender();
        try{original.call(graphics,delta);}finally{NativeSpellHud.endRender();}
    }
}
