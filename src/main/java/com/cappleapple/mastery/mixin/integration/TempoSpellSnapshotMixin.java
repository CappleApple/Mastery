package com.cappleapple.mastery.mixin.integration;

import com.cappleapple.mastery.spells.SpellService;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Existing cooldowns retain their cast level; the displayed charge capacity follows the prepared spell. */
@Pseudo
@Mixin(targets="com.cappleapple.temponottime.network.SyncCooldownStatePayload",remap=false)
public abstract class TempoSpellSnapshotMixin {
    @ModifyVariable(method="createSpellState",at=@At("HEAD"),argsOnly=true,require=0)
    private static int mastery$currentPreparedLevel(int level,ServerPlayer player,String id,int originalLevel) {
        return SpellService.preparedSpells(player).stream().filter(spell->spell.getSpell().getSpellId().equals(id))
                .mapToInt(spell->spell.getLevel()).findFirst().orElse(level);
    }
}
