package com.cappleapple.mastery.spells;

import com.cappleapple.mastery.api.SpellEquipmentRegistry;
import com.cappleapple.mastery.data.DefinitionSet;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.bettercombat.logic.WeaponRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Datapack rules and registered adapters share a priority ordering; highest match wins. */
public final class ContextResolver {
    private ContextResolver() {}
    public static String resolve(Player player,DefinitionSet definitions) { return resolve(player,definitions,id -> true); }
    public static String resolve(Player player,DefinitionSet definitions,java.util.function.Predicate<String> allowed) {
        record Candidate(int priority,String id) {}
        List<Candidate> candidates = new ArrayList<>();
        definitions.contexts().values().forEach(def -> {
            if (!def.id().equals("mastery:two_handed") && !(def.condition().has("provider_only")&&def.condition().get("provider_only").getAsBoolean()) && matches(player,def.condition())) candidates.add(new Candidate(def.priority(),def.id()));
        });
        SpellEquipmentRegistry.CONTEXTS.forEach((priority,providers) -> providers.forEach(provider ->
            provider.context(player).filter(definitions.contexts()::containsKey).ifPresent(id -> candidates.add(new Candidate(priority,id)))));
        return candidates.stream().sorted(Comparator.comparingInt(Candidate::priority).reversed().thenComparing(Candidate::id))
                .map(Candidate::id).filter(id -> !id.equals("mastery:two_handed") || twoHanded(player))
                .filter(allowed).findFirst().orElse("");
    }
    private static boolean twoHanded(Player player) {
        var attributes=WeaponRegistry.getAttributes(player.getMainHandItem());
        return attributes!=null&&attributes.isTwoHanded();
    }
    public static boolean matches(Player player,JsonObject rule) {
        if (rule.has("and")) for (JsonElement entry:rule.getAsJsonArray("and")) if (!matches(player,entry.getAsJsonObject())) return false;
        if (rule.has("or")) {
            boolean any=false;
            for (JsonElement entry:rule.getAsJsonArray("or")) any |= matches(player,entry.getAsJsonObject());
            if (!any) return false;
        }
        if (rule.has("not") && matches(player,rule.getAsJsonObject("not"))) return false;
        if (rule.has("blocking") && rule.get("blocking").getAsBoolean()!=player.isBlocking()) return false;
        if (rule.has("empty") && rule.get("empty").getAsBoolean()!=player.getMainHandItem().isEmpty()) return false;
        if (rule.has("dual_wield") && rule.get("dual_wield").getAsBoolean()!=(weapon(player.getMainHandItem())&&weapon(player.getOffhandItem()))) return false;
        return item(player.getMainHandItem(),rule,"item","item_tag") && item(player.getOffhandItem(),rule,"offhand","offhand_tag");
    }
    private static boolean weapon(ItemStack stack) {
        return stack.is(net.minecraft.tags.ItemTags.SWORDS) || stack.is(net.minecraft.tags.ItemTags.AXES)
                || stack.is(TagKey.create(Registries.ITEM,ResourceLocation.fromNamespaceAndPath("mastery","melee_weapons")));
    }
    private static boolean item(ItemStack stack,JsonObject rule,String exact,String tag) {
        if (rule.has(exact) && !BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(rule.get(exact).getAsString())) return false;
        if (rule.has(tag)) {
            ResourceLocation id=ResourceLocation.tryParse(rule.get(tag).getAsString());
            if (id==null || !stack.is(TagKey.create(Registries.ITEM,id))) return false;
        }
        return true;
    }
}
