package com.cappleapple.mastery.mixin.client;
import com.cappleapple.mastery.client.NativeSpellHud;
import io.redspace.ironsspellbooks.api.magic.SpellSelectionManager;
import io.redspace.ironsspellbooks.player.ClientMagicData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Addon render callbacks see the same selection as Iron's HUD, only during that render. */
@Mixin(value=ClientMagicData.class,remap=false)
public abstract class IronHudSelectionMixin {
    @Inject(method="getSpellSelectionManager",at=@At("RETURN"),cancellable=true)
    private static void mastery$hudSelection(CallbackInfoReturnable<SpellSelectionManager> callback){callback.setReturnValue(NativeSpellHud.duringRender(callback.getReturnValue()));}
}
