package com.cappleapple.mastery.items;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Skill books are consumed to learn branch tokens; they are never equipment or spell containers. */
public final class MasteryItems {
    public static final DeferredRegister.Items TYPES = DeferredRegister.createItems("mastery");
    public static final DeferredItem<SkillBookItem> SKILL_BOOK = TYPES.register("skill_book",
            () -> new SkillBookItem(new Item.Properties().stacksTo(16)));
    private MasteryItems() {}
}
