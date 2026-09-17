package com.cappleapple.mastery.api.spell;

import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Register modifiers during common setup. This API does not register or execute spells. */
public final class SpellModifierRegistry {
    private static final Map<String,SpellModifier> MODIFIERS=new LinkedHashMap<>();
    private SpellModifierRegistry() {}
    public static synchronized void register(String id,SpellModifier modifier) {
        if(ResourceLocation.tryParse(id)==null) throw new IllegalArgumentException("Invalid modifier ID: "+id);
        if(MODIFIERS.putIfAbsent(id,Objects.requireNonNull(modifier))!=null) throw new IllegalArgumentException("Duplicate modifier: "+id);
    }
    public static SpellModifier get(String id) { return MODIFIERS.get(id); }
}
