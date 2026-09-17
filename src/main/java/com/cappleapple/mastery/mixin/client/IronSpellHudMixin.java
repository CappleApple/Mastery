package com.cappleapple.mastery.mixin.client;
import com.cappleapple.mastery.client.NativeSpellHud;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.redspace.ironsspellbooks.api.magic.SpellSelectionManager;
import io.redspace.ironsspellbooks.gui.overlays.SpellBarOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(value=SpellBarOverlay.class,remap=false)
public abstract class IronSpellHudMixin {
    @ModifyExpressionValue(method="render",at=@At(value="INVOKE",target="Lio/redspace/ironsspellbooks/player/ClientMagicData;getSpellSelectionManager()Lio/redspace/ironsspellbooks/api/magic/SpellSelectionManager;"))
    private SpellSelectionManager mastery$activeSet(SpellSelectionManager original){return NativeSpellHud.view(original);}
}
