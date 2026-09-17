package com.cappleapple.mastery.mixin.client;
import com.cappleapple.mastery.spells.NativeProjectileScale;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.redspace.ironsspellbooks.entity.spells.fireball.FireballRenderer;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(value=FireballRenderer.class,remap=false)
public abstract class IronFireballRendererMixin {
    @ModifyExpressionValue(method="render(Lnet/minecraft/world/entity/projectile/Projectile;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at=@At(value="FIELD",target="Lio/redspace/ironsspellbooks/entity/spells/fireball/FireballRenderer;scale:F"))
    private float mastery$size(float original,Projectile entity){return entity instanceof NativeProjectileScale scaled?original*scaled.mastery$getScale():original;}
}
