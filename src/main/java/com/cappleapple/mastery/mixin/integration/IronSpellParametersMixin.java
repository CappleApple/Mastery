package com.cappleapple.mastery.mixin.integration;

import com.cappleapple.mastery.spells.SpellModifiers;
import com.cappleapple.mastery.spells.SpellService;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Iron 3.16.3 has no player-aware mana-admission or cast-time event. Keep admission and native costs consistent. */
@Mixin(value=AbstractSpell.class,remap=false)
public abstract class IronSpellParametersMixin {
    @ModifyExpressionValue(method="canBeCastedBy",at=@At(value="INVOKE",target="Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getManaCost(I)I"))
    private int mastery$nativeManaAdmission(int original,int level,CastSource source,MagicData magic,Player player) {
        return player instanceof ServerPlayer serverPlayer
                ?SpellModifiers.scale(original,SpellService.modifiers(serverPlayer,((AbstractSpell)(Object)this).getSpellId()).manaMultiplier()):original;
    }
    @Inject(method="getEffectiveCastTime",at=@At("RETURN"),cancellable=true)
    private void mastery$nativeCastTime(int level,LivingEntity entity,CallbackInfoReturnable<Integer> callback) {
        if(entity instanceof ServerPlayer player) callback.setReturnValue(com.cappleapple.mastery.spells.ChargeService.duration(player,((AbstractSpell)(Object)this).getSpellId(),
                SpellModifiers.scale(callback.getReturnValue(),SpellService.modifiers(player,((AbstractSpell)(Object)this).getSpellId()).castTimeMultiplier())));
    }
}
