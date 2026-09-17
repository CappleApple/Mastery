package com.cappleapple.mastery.integration;

import com.google.gson.JsonObject;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import net.minecraft.world.damagesource.DamageSource;

/** Classifies damage through Iron's registered schools, including schools registered by addons. */
public final class SchoolDamage {
    private SchoolDamage() {}
    public static void addContext(DamageSource source, JsonObject context) {
        for (var school : SchoolRegistry.REGISTRY) {
            if (source.is(school.getDamageType())) {
                context.addProperty("school", school.getId().toString());
                break;
            }
        }
        if (source instanceof SpellDamageSource spellSource)
            context.addProperty("spell", spellSource.spell().getSpellId());
    }
}
