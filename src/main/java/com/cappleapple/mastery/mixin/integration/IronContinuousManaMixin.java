package com.cappleapple.mastery.mixin.integration;

import com.cappleapple.mastery.spells.SpellModifiers;
import com.cappleapple.mastery.spells.SpellService;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The verified 3.16.3 native channel tick checks the same adjusted cost before continuing a cast. */
@Mixin(value=MagicManager.class,remap=false)
public abstract class IronContinuousManaMixin {
    @ModifyExpressionValue(method="lambda$tick$0",at=@At(value="INVOKE",target="Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getManaCost(I)I"))
    private int mastery$nativeChannelCost(int original,boolean regenerateMana,Player player) {
        if(!(player instanceof ServerPlayer serverPlayer)) return original;
        return SpellModifiers.scale(original,SpellService.modifiers(serverPlayer,
                MagicData.getPlayerMagicData(player).getCastingSpellId()).manaMultiplier());
    }
}
