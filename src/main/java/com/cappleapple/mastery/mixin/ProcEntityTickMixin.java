package com.cappleapple.mastery.mixin;

import com.cappleapple.mastery.mechanics.MechanicsRuntime;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

/** Keep secondary origin while a proc projectile/area entity creates delayed effects or descendants. */
@Mixin(ServerLevel.class)
public abstract class ProcEntityTickMixin {
    @WrapMethod(method = "tickNonPassenger")
    private void mastery$procTick(Entity entity, Operation<Void> original) {
        MechanicsRuntime.withProcEntity(entity, () -> original.call(entity));
    }
    @WrapMethod(method = "tickPassenger")
    private void mastery$procPassenger(Entity vehicle, Entity passenger, Operation<Void> original) {
        MechanicsRuntime.withProcEntity(passenger, () -> original.call(vehicle, passenger));
    }
}
