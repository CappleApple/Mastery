package com.cappleapple.mastery.mechanics;

import com.cappleapple.mastery.elemental.DamageTypes;
import com.cappleapple.mastery.elemental.ElementalDamage;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.projectile.Projectile;
import java.util.*;

public final class DamageContexts {
    private DamageContexts() {}
    public static DamageContext capture(DamageSource source) {
        var categories=new HashSet<String>();var elements=new HashSet<String>();var types=new HashSet<String>();var tags=new HashSet<String>();
        source.typeHolder().unwrapKey().ifPresent(key->types.add(key.location().toString()));
        source.typeHolder().tags().forEach(tag->tags.add(tag.location().toString()));
        for(var type:DamageTypes.all())if(source.is(type.damageType())) {
            elements.add(type.id());if(type.school()!=null)elements.add(type.field("school",""));
            categories.add(type.elemental()?"elemental":"physical");
        }
        for(var school:SchoolRegistry.REGISTRY)if(source.is(school.getDamageType())) {
            elements.add(SchoolRegistry.REGISTRY.getKey(school).toString());categories.add("magic");categories.add("elemental");
        }
        if(source.is(TagKey.create(Registries.DAMAGE_TYPE,ResourceLocation.parse("mastery:is_magic"))))categories.add("magic");
        var origin=ElementalDamage.originalSource(source);
        if(origin.is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK)||origin.is(net.minecraft.world.damagesource.DamageTypes.MOB_ATTACK)
                ||origin.is(net.minecraft.world.damagesource.DamageTypes.MOB_ATTACK_NO_AGGRO))categories.add("melee");
        if(source.is(DamageTypeTags.IS_PROJECTILE)||source.getDirectEntity() instanceof Projectile) {
            categories.add("ranged");tags.add(DamageTypeTags.IS_PROJECTILE.location().toString());
        }
        if(source==origin&&(categories.contains("melee")||source.is(net.minecraft.world.damagesource.DamageTypes.ARROW)||source.is(net.minecraft.world.damagesource.DamageTypes.TRIDENT)))categories.add("physical");
        String spell="";UUID caster=null;
        if(!MechanicsRuntime.secondary(source)&&origin instanceof io.redspace.ironsspellbooks.damage.SpellDamageSource nativeSource&&origin.getEntity()!=null) {
            spell=nativeSource.spell().getSpellId();caster=origin.getEntity().getUUID();
        }
        return new DamageContext(categories,elements,types,tags,spell,caster);
    }
}
