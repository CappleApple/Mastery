package com.cappleapple.mastery.elemental;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.List;

/** Fractions use 0.2 for twenty percent; registered attributes stay stable across data reloads. */
public final class ElementalAttributes {
    public static final List<String> SCHOOLS=List.of("fire","ice","lightning","holy","ender","blood","evocation","nature","eldritch","slashing","piercing","blunt");
    public static final DeferredRegister<Attribute> TYPES=DeferredRegister.create(Registries.ATTRIBUTE,"mastery");
    static {
        register("elemental_damage",-1,100);
        register("proc_chance",-1,1);
        for(String school:SCHOOLS) {
            register(school+"_weapon_damage",0,100);
            register(school+"_conversion",0,1);
            register(school+"_damage",-1,100);
            register(school+"_attunement",0,10);
            register(school+"_potency",0,10);
            register(school+"_mitigation",0,1);
        }
    }
    private ElementalAttributes() {}
    private static void register(String id,double min,double max) {
        TYPES.register(id,()->new RangedAttribute("attribute.mastery."+id,0,min,max).setSyncable(true));
    }
    public static void attach(EntityAttributeModificationEvent event) {
        for(var type:event.getTypes())for(var attribute:TYPES.getEntries())if(!event.has(type,attribute))event.add(type,attribute);
    }
}
