package com.cappleapple.mastery.classes;

import java.util.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Shared, side-effect-free starter equipment plan for selection previews and server grants. */
public final class ClassEquipment {
    public static final List<EquipmentSlot> ARMOR=List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET);
    public record Plan(List<ItemStack> inventory,Map<EquipmentSlot,ItemStack> armor,List<ItemStack> overflow) {}
    private ClassEquipment() {}
    public static Plan prepare(Player player,List<ItemStack> grants,boolean equipArmor) {
        var inventory=new ArrayList<ItemStack>();player.getInventory().items.forEach(stack->inventory.add(stack.copy()));
        var armor=new EnumMap<EquipmentSlot,ItemStack>(EquipmentSlot.class);
        ARMOR.forEach(slot->armor.put(slot,player.getItemBySlot(slot).copy()));
        var equipped=EnumSet.noneOf(EquipmentSlot.class);var incoming=new ArrayList<ItemStack>();
        for(var grant:grants) {
            var stack=grant.copy();var slot=player.getEquipmentSlotForItem(stack);
            if(equipArmor&&ARMOR.contains(slot)&&equipped.add(slot)) {
                var displaced=armor.get(slot);if(!displaced.isEmpty())incoming.add(displaced.copy());
                armor.put(slot,stack.split(1));
            }
            if(!stack.isEmpty())incoming.add(stack);
        }
        var overflow=new ArrayList<ItemStack>();
        for(var stack:incoming) {
            for(var current:inventory)if(!stack.isEmpty()&&!current.isEmpty()&&ItemStack.isSameItemSameComponents(current,stack)) {
                int amount=Math.min(stack.getCount(),Math.max(0,current.getMaxStackSize()-current.getCount()));current.grow(amount);stack.shrink(amount);
            }
            for(int slot=0;slot<inventory.size()&&!stack.isEmpty();slot++)if(inventory.get(slot).isEmpty())inventory.set(slot,stack.split(Math.min(stack.getCount(),stack.getMaxStackSize())));
            if(!stack.isEmpty())overflow.add(stack);
        }
        return new Plan(List.copyOf(inventory),Map.copyOf(armor),List.copyOf(overflow));
    }
}
