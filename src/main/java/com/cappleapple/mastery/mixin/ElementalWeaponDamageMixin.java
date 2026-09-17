package com.cappleapple.mastery.mixin;
import com.cappleapple.mastery.elemental.ElementalDamage;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(LivingEntity.class)
public abstract class ElementalWeaponDamageMixin {
    @WrapMethod(method="hurt")
    private boolean mastery$splitWeaponDamage(DamageSource source,float amount,Operation<Boolean> original) {
        return ElementalDamage.aroundHurt((LivingEntity)(Object)this,source,amount,original);
    }
}
