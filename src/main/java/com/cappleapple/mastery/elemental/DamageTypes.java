package com.cappleapple.mastery.elemental;

import com.cappleapple.mastery.MasteryRuntime;
import com.google.gson.JsonObject;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Reloadable mappings to registered native damage types, mob groups and weapon tags. */
public final class DamageTypes {
    private DamageTypes() {}
    public record Type(String id,JsonObject data) {
        public String field(String key,String fallback) {return text(data,key,fallback);}
        public SchoolType school() {String id=field("school","");return id.isEmpty()?null:SchoolRegistry.REGISTRY.get(ResourceLocation.parse(id));}
        public ResourceKey<DamageType> damageType() {
            String nativeId=field("damage_type","");return nativeId.isEmpty()?school().getDamageType():ResourceKey.create(Registries.DAMAGE_TYPE,ResourceLocation.parse(nativeId));
        }
        public String attribute(String kind) {return field(kind+"_attribute","mastery:"+ResourceLocation.parse(id).getPath()+"_"+switch(kind){case "weapon"->"weapon_damage";case "power"->"damage";default->kind;});}
        public boolean elemental() {return data.has("elemental")?data.get("elemental").getAsBoolean():school()!=null;}
    }
    static String text(JsonObject j,String key,String fallback) {return j.has(key)?j.get(key).getAsString():fallback;}
    public static List<Type> all() {return MasteryRuntime.definitions().elements().entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->new Type(e.getKey(),e.getValue())).toList();}
    public static Type find(String id) {
        var mapped=all().stream().filter(t->t.id().equals(id)||t.field("school","").equals(id)||t.field("damage_type","").equals(id)).findFirst().orElse(null);
        if(mapped!=null)return mapped;
        var location=ResourceLocation.tryParse(id);
        if(location!=null&&SchoolRegistry.REGISTRY.containsKey(location)) {
            var data=new JsonObject();data.addProperty("school",id);return new Type(id,data);
        }
        return null;
    }
    public static Type find(DamageSource source) {return all().stream().filter(t->source.is(t.damageType())).findFirst().orElse(null);}
    public static String weapon(ItemStack item) {
        return MasteryRuntime.definitions().weaponTypes().entrySet().stream()
                .sorted(Comparator.<Map.Entry<String,JsonObject>>comparingInt(e->e.getValue().has("priority")?-e.getValue().get("priority").getAsInt():0).thenComparing(Map.Entry::getKey))
                .filter(e->matchesItem(item,e.getValue())).map(e->text(e.getValue(),"element","")).findFirst().orElse("");
    }
    private static boolean matchesItem(ItemStack stack,JsonObject j) {
        if(j.has("items"))for(var id:j.getAsJsonArray("items"))if(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(id.getAsString()))return true;
        if(j.has("item_tags"))for(var id:j.getAsJsonArray("item_tags"))if(stack.is(TagKey.create(Registries.ITEM,ResourceLocation.parse(id.getAsString()))))return true;
        return false;
    }
    public static boolean mob(LivingEntity entity,JsonObject group) {
        if(group.has("entities"))for(var id:group.getAsJsonArray("entities"))if(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().equals(id.getAsString()))return true;
        if(group.has("entity_tags"))for(var id:group.getAsJsonArray("entity_tags"))if(entity.getType().is(TagKey.create(Registries.ENTITY_TYPE,ResourceLocation.parse(id.getAsString()))))return true;
        return false;
    }
    /** Overlapping groups add their deltas once each; the final multiplier cannot become negative. */
    public static double matchup(Type type,LivingEntity owner,LivingEntity target,boolean healing,double attunement,double potency,double mitigation) {
        if(type==null||!type.data().has("damage_modifiers"))return 1;
        double total=0;
        for(var entry:type.data().getAsJsonObject("damage_modifiers").entrySet()) {
            var group=MasteryRuntime.definitions().mobTypes().get(entry.getKey());
            if(group!=null&&mob(target,group))total+=ElementalRules.matchupDelta(entry.getValue().getAsDouble()*(healing?-1:1),attunement,potency,mitigation);
        }
        return Math.max(0,1+total);
    }
}
