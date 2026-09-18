package com.cappleapple.mastery.progression;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.Set;

/** Registered multiplier attributes; custom datapack trees can reference an existing attribute. */
public final class ExperienceAttributes {
    public static final Set<String> BUNDLED_TREES = Set.of("alchemy", "blood", "bow", "crossbow", "dual_wield",
            "eldritch", "ender", "engineering", "evocation", "fire", "flame_blade", "holy", "ice", "lightning",
            "mining", "mobility", "nature", "one_handed", "shields", "smithing", "survival", "two_handed");
    public static final DeferredRegister<Attribute> TYPES = DeferredRegister.create(Registries.ATTRIBUTE, "mastery");
    static {
        register("experience_gain");
        BUNDLED_TREES.stream().sorted().forEach(tree -> register(tree + "_experience_gain"));
    }
    private ExperienceAttributes() {}
    private static void register(String id) {
        TYPES.register(id, () -> new RangedAttribute("attribute.mastery." + id, 1, 0, 100).setSyncable(true));
    }
    public static void attach(EntityAttributeModificationEvent event) {
        for (var type : event.getTypes()) for (var attribute : TYPES.getEntries())
            if (!event.has(type, attribute)) event.add(type, attribute);
    }
    public static String defaultAttribute(String tree) {
        if (!tree.startsWith("mastery:")) return "";
        String path = tree.substring("mastery:".length());
        return BUNDLED_TREES.contains(path) ? "mastery:" + path + "_experience_gain" : "";
    }
}
