package com.cappleapple.mastery.integration;

import com.cappleapple.mastery.api.SpellEquipmentRegistry;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import top.theillusivec4.curios.api.CuriosApi;

/** Reads native capacity only; neither occupancy nor container data is changed. */
final class CuriosIntegration {
    static void register() {
        SpellEquipmentRegistry.registerCapacity("mastery:native_curios_slots",player -> CuriosApi.getCuriosInventory(player).map(handler -> {
            int capacity=0;
            for(var entry:handler.getCurios().entrySet()) {
                var stacks=entry.getValue().getStacks();
                for(int slot=0;slot<stacks.getSlots();slot++) {
                    var stack=stacks.getStackInSlot(slot);
                    if(handler.isSlotActive(entry.getKey(),slot)&&ISpellContainer.isSpellContainer(stack)) {
                        var container=ISpellContainer.get(stack);
                        if(container.isSpellWheel()) capacity+=Math.max(0,container.getMaxSpellCount());
                    }
                }
            }
            return capacity;
        }).orElse(0));
    }
}
