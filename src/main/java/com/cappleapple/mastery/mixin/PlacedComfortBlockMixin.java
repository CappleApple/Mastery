package com.cappleapple.mastery.mixin;

import com.cappleapple.mastery.crafting.PlacedComfortData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class PlacedComfortBlockMixin {
    @Inject(method="setBlockState",at=@At("RETURN"))
    private void mastery$removeReplacedBonus(BlockPos position,BlockState state,boolean moving,CallbackInfoReturnable<BlockState> cir) {
        BlockState previous=cir.getReturnValue();
        if(previous!=null&&previous.getBlock()!=state.getBlock()&&((LevelChunk)(Object)this).getLevel() instanceof ServerLevel level)
            PlacedComfortData.get(level).remove(position);
    }
}
