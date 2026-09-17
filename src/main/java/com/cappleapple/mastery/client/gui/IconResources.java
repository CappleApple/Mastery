package com.cappleapple.mastery.client.gui;

import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/** Resolves the same item, raw PNG, or native spell icon on maps and Skill Book covers. */
public final class IconResources {
    private IconResources() {}
    public static ResourceLocation texture(String value) {
        ResourceLocation id=ResourceLocation.tryParse(value);
        if(id==null||BuiltInRegistries.ITEM.containsKey(id))return null;
        if(SpellRegistry.REGISTRY.containsKey(id))return SpellRegistry.getSpell(value).getSpellIconResource();
        return id.getPath().endsWith(".png")&&Minecraft.getInstance().getResourceManager().getResource(id).isPresent()?id:null;
    }
}
