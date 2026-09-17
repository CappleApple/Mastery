package com.cappleapple.mastery.mixin.integration;

import com.cappleapple.mastery.elemental.ElementalDamage;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.shadowsoffire.apothic_attributes.impl.AttributeEvents;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.spongepowered.asm.mixin.Mixin;

/** A partitioned weapon attack shares the primary Apothic critical roll across every damage portion. */
@Mixin(value=AttributeEvents.class,remap=false)
public abstract class ApothicCriticalStrikeMixin {
    @WrapMethod(method="apothCriticalStrike")
    private void mastery$shareCritical(LivingIncomingDamageEvent event, Operation<Void> original) {
        float before=event.getAmount();
        original.call(event);
        ElementalDamage.recordCritical(event,before);
    }
}
