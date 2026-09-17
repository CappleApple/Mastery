package com.cappleapple.mastery.mixin;

import com.cappleapple.mastery.crafting.CraftingService;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Deque;
import java.util.LinkedList;

@Mixin(AbstractContainerMenu.class)
public abstract class CraftingClickMixin {
    @Unique private final Deque<CraftingService.Extraction> mastery$craftingClicks=new LinkedList<>();
    @Inject(method="clicked",at=@At("HEAD"))
    private void mastery$prepareOutput(int slotId,int button,ClickType type,Player player,CallbackInfo ci) {
        mastery$craftingClicks.push(type==ClickType.PICKUP||type==ClickType.QUICK_MOVE||type==ClickType.SWAP||type==ClickType.THROW
                ?CraftingService.prepareOutput((AbstractContainerMenu)(Object)this,slotId,player):null);
    }
    @Inject(method="clicked",at=@At("RETURN"))
    private void mastery$completeOutput(int slotId,int button,ClickType type,Player player,CallbackInfo ci) {
        CraftingService.completeOutput(mastery$craftingClicks.pop());
    }
}
