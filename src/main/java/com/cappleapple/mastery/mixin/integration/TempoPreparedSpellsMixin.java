package com.cappleapple.mastery.mixin.integration;
import com.cappleapple.mastery.MasteryRuntime;
import com.cappleapple.mastery.spells.SpellService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.*;

/** Tempo normally notices equipment changes; skill loadout changes need the same snapshot refresh. */
@Pseudo
@Mixin(targets="com.cappleapple.temponottime.casting.CooldownManager",remap=false)
public abstract class TempoPreparedSpellsMixin {
    @Unique private final Map<ServerPlayer,String> mastery$prepared=new WeakHashMap<>();
    @Shadow public abstract void sync(ServerPlayer player);
    @Inject(method="onPlayerTick",at=@At("TAIL"),require=0)
    private void mastery$refreshPrepared(PlayerTickEvent.Post event,CallbackInfo callback){
        if(!(event.getEntity() instanceof ServerPlayer player)||player.tickCount%5!=0)return;
        String context=SpellService.context(player);
        String signature=context+SpellService.preparedSpells(player).stream()
                .map(spell->spell.getSpell().getSpellId()+"="+spell.getLevel()+":"+SpellService.modifiers(player,spell.getSpell().getSpellId()).extraCharges()).toList();
        if(!signature.equals(mastery$prepared.put(player,signature)))sync(player);
    }
    @Inject(method="selectedSpells",at=@At("RETURN"),cancellable=true,require=0)
    private void mastery$preparedLevels(ServerPlayer player,org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<List<io.redspace.ironsspellbooks.api.spells.SpellData>> callback) {
        var result=new LinkedHashMap<String,io.redspace.ironsspellbooks.api.spells.SpellData>();
        callback.getReturnValue().forEach(spell->result.put(spell.getSpell().getSpellId(),spell));
        SpellService.preparedSpells(player).forEach(spell->result.put(spell.getSpell().getSpellId(),spell));
        callback.setReturnValue(List.copyOf(result.values()));
    }
}
