package com.cappleapple.mastery.mixin.integration;
import com.cappleapple.mastery.spells.NativeProjectileScale;
import io.redspace.ironsspellbooks.entity.spells.AbstractMagicProjectile;
import net.minecraft.network.syncher.*;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=AbstractMagicProjectile.class,remap=false)
public abstract class IronProjectileScaleMixin implements NativeProjectileScale {
    @Unique private static final EntityDataAccessor<Float> mastery$scale=SynchedEntityData.defineId(AbstractMagicProjectile.class,EntityDataSerializers.FLOAT);
    @Inject(method="defineSynchedData",at=@At("TAIL"))
    private void mastery$define(SynchedEntityData.Builder builder,CallbackInfo ci){builder.define(mastery$scale,1F);}
    public float mastery$getScale(){return ((AbstractMagicProjectile)(Object)this).getEntityData().get(mastery$scale);}
    public void mastery$setScale(float value){((AbstractMagicProjectile)(Object)this).getEntityData().set(mastery$scale,Math.clamp(value,1,16));}
    @Inject(method="addAdditionalSaveData",at=@At("TAIL"))
    private void mastery$save(CompoundTag tag,CallbackInfo ci){if(mastery$getScale()!=1)tag.putFloat("MasteryChargeScale",mastery$getScale());}
    @Inject(method="readAdditionalSaveData",at=@At("TAIL"))
    private void mastery$load(CompoundTag tag,CallbackInfo ci){if(tag.contains("MasteryChargeScale"))mastery$setScale(tag.getFloat("MasteryChargeScale"));}
}
