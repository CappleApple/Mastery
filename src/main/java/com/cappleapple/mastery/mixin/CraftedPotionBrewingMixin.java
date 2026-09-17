package com.cappleapple.mastery.mixin;

import com.cappleapple.mastery.crafting.CraftingService;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionBrewing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Custom effects remain authoritative when consumed; later vanilla brewing uses the original recipe identity. */
@Mixin(PotionBrewing.class)
public abstract class CraftedPotionBrewingMixin {
    @Inject(method="hasMix",at=@At("RETURN"),cancellable=true)
    private void mastery$recognizeBase(ItemStack bottle,ItemStack ingredient,CallbackInfoReturnable<Boolean> cir) {
        if(cir.getReturnValue())return;
        ItemStack base=CraftingService.brewingBase(bottle);
        if(base!=bottle)cir.setReturnValue(((PotionBrewing)(Object)this).hasMix(base,ingredient));
    }
    @Inject(method="mix",at=@At("HEAD"),cancellable=true)
    private void mastery$brewFinishedPotion(ItemStack ingredient,ItemStack bottle,CallbackInfoReturnable<ItemStack> cir) {
        ItemStack base=CraftingService.brewingBase(bottle);
        if(base==bottle)return;
        var brewing=(PotionBrewing)(Object)this;
        // Preserve custom recipes' opportunity to match the upgraded bottle before restoring its vanilla identity.
        for(var recipe:brewing.getRecipes()) {
            ItemStack custom=recipe.getOutput(bottle,ingredient);
            if(!custom.isEmpty()){cir.setReturnValue(custom);return;}
        }
        if(brewing.hasMix(base,ingredient))cir.setReturnValue(brewing.mix(ingredient,base));
    }
}
