package com.cappleapple.mastery.mixin;

import com.cappleapple.mastery.crafting.CraftingService;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Deque;
import java.util.LinkedList;

/** Quick crafting repeats internally; each fresh result is prepared before merging and committed after transfer. */
@Mixin({CraftingMenu.class,InventoryMenu.class,AbstractFurnaceMenu.class,StonecutterMenu.class,ItemCombinerMenu.class,BrewingStandMenu.class})
public abstract class CraftingQuickMoveMixin {
    @Unique private final Deque<CraftingService.Extraction> mastery$craftingMoves=new LinkedList<>();
    @Inject(method="quickMoveStack",at=@At("HEAD"))
    private void mastery$prepareOutput(Player player,int slotId,CallbackInfoReturnable<ItemStack> cir) {
        mastery$craftingMoves.push(CraftingService.prepareOutput((AbstractContainerMenu)(Object)this,slotId,player));
    }
    @Inject(method="quickMoveStack",at=@At("RETURN"))
    private void mastery$completeOutput(Player player,int slotId,CallbackInfoReturnable<ItemStack> cir) {
        CraftingService.completeOutput(mastery$craftingMoves.pop());
    }
}
