package com.cappleapple.mastery.crafting;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.brewing.PotionBrewEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import java.util.ArrayList;
import static com.cappleapple.mastery.crafting.CraftingRules.*;

public final class CraftingEvents {
    private static final ThreadLocal<java.util.List<net.minecraft.world.item.ItemStack>> BREW_INPUTS=new ThreadLocal<>();
    private CraftingEvents() {}
    /** Fallback for other mods that fire crafting events before handing out their result. */
    @SubscribeEvent public static void crafted(PlayerEvent.ItemCraftedEvent event) {
        if(event.getEntity() instanceof ServerPlayer player)CraftingService.apply(player,event.getCrafting());
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void brewing(PotionBrewEvent.Pre event) {
        var before=new java.util.ArrayList<net.minecraft.world.item.ItemStack>();
        for(int i=0;i<Math.min(3,event.getLength());i++)before.add(event.getItem(i).copy());
        BREW_INPUTS.set(before);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void brewed(PotionBrewEvent.Post event) {
        var before=BREW_INPUTS.get();BREW_INPUTS.remove();
        if(before==null)return;
        for(int i=0;i<Math.min(before.size(),event.getLength());i++)if(!event.getItem(i).isEmpty()
                &&!net.minecraft.world.item.ItemStack.isSameItemSameComponents(before.get(i),event.getItem(i)))
            CraftingService.update(event.getItem(i),tag->tag.putBoolean(CraftingService.BREWED,true));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void placed(BlockEvent.EntityPlaceEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer player)||event.isCanceled())return;
        // One contribution per placed item, including multiblock items such as beds.
        var state=event.getPlacedBlock();var position=event.getPos().immutable();
        var bonuses=new ArrayList<PlacedComfortData.Bonus>();
        var item=state.getBlock().asItem().getDefaultInstance();
        for(var active:CraftingService.activeEffects(player)) {
            var effect=active.json();if(!text(effect,"type","").equals("mastery:placed_comfort"))continue;
            if(!CraftingService.matches(item,effect))continue;
            String block=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            if(effect.has("block")&&!block.equals(text(effect,"block","")))continue;
            if(effect.has("block_tag")&&!state.is(TagKey.create(Registries.BLOCK,ResourceLocation.parse(text(effect,"block_tag","")))))continue;
            if(!CraftingService.roll(player,effect))continue;
            double amount=number(effect,"amount",0)*active.rank();if(amount<=0)continue;
            bonuses.add(new PlacedComfortData.Bonus(position,block,active.id(),text(effect,"comfort_type","mastery_crafted"),amount,number(effect,"radius",8)));
        }
        PlacedComfortData.get(player.serverLevel()).put(position,bonuses);
    }
}
