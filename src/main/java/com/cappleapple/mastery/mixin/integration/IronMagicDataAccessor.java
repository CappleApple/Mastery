package com.cappleapple.mastery.mixin.integration;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value=MagicData.class,remap=false)
public interface IronMagicDataAccessor {
    @Accessor("castDurationRemaining") void mastery$remaining(int ticks);
}
