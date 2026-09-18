package com.cappleapple.mastery.mixin.integration;

import com.cappleapple.mastery.spells.ChargeService;
import io.redspace.ironsspellbooks.api.spells.*;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="com.cappleapple.temponottime.casting.CooldownManager",remap=false)
public abstract class TempoHeldChargesMixin {
    @ModifyArgs(method="commitCast",at=@At(value="INVOKE",target="Lcom/cappleapple/temponottime/data/PlayerCooldownData;add(Ljava/lang/String;IDDZZZ)Lcom/cappleapple/temponottime/data/CooldownInstance;"),require=0)
    private void mastery$splitDebt(Args args,ServerPlayer player,AbstractSpell spell,int level,double mana,CastSource source) {
        int units=ChargeService.beginTempoCommit(player,spell.getSpellId());
        if(units>1)args.set(2,(double)args.get(2)/units);
    }
    @Inject(method="commitCast",at=@At("RETURN"),require=0)
    private void mastery$finishDebt(ServerPlayer player,AbstractSpell spell,int level,double mana,CastSource source,CallbackInfo callback) {
        ChargeService.finishTempoCommit(player,spell.getSpellId());
    }
}
