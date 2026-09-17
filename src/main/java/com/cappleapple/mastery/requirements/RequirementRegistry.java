package com.cappleapple.mastery.requirements;

import com.cappleapple.mastery.MasteryRuntime;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiPredicate;

/** Extensible, fail-closed server predicates. Handlers should not mutate state. */
public final class RequirementRegistry {
    private static final Map<String, BiPredicate<RequirementContext, JsonObject>> TYPES = new HashMap<>();
    static {
        register("mastery:tree_level", (c, j) -> effectiveLevel(c.player(),string(j,"tree","")) >= number(j,"level",1));
        register("mastery:node_rank", (c, j) -> com.cappleapple.mastery.progression.ProgressionService.cappedRank(MasteryRuntime.definitions(),MasteryRuntime.progress(c.player()),string(j,"node",""),MasteryRuntime.worldTier(c.player())) >= number(j,"rank",1));
        register("mastery:world_tier", (c, j) -> MasteryRuntime.worldTier(c.player()) < 0 || MasteryRuntime.worldTier(c.player()) >= number(j,"tier",0));
        register("mastery:advancement", (c, j) -> {
            var advancement = c.player().server.getAdvancements().get(ResourceLocation.parse(string(j,"id","")));
            return advancement != null && c.player().getAdvancements().getOrStartProgress(advancement).isDone();
        });
        register("mastery:book_unlocked", (c, j) -> MasteryRuntime.progress(c.player()).bookUnlocks().contains(string(j,"token","")));
        register("mastery:condition", (c, j) -> testFields(c, j));
    }
    private RequirementRegistry() {}
    private static int effectiveLevel(net.minecraft.server.level.ServerPlayer p,String id) {
        var tree=MasteryRuntime.definitions().trees().get(id);
        return tree==null?0:Math.min(MasteryRuntime.progress(p).tree(id).level(),tree.levelCap(MasteryRuntime.worldTier(p)));
    }
    /** Register during common setup. Duplicate IDs are rejected. */
    public static void register(String id, BiPredicate<RequirementContext,JsonObject> predicate) {
        ResourceLocation.parse(id);
        if (TYPES.putIfAbsent(id,predicate) != null) throw new IllegalArgumentException("Duplicate requirement: " + id);
    }
    public static boolean registered(String id) { return TYPES.containsKey(id); }
    public static boolean test(RequirementContext context, JsonObject definition) { return test(context,definition,0); }
    private static boolean test(RequirementContext c, JsonObject j, int depth) {
        if (depth > 32) return false;
        try {
            if (j.has("ref")) {
                var ref = MasteryRuntime.definitions().requirements().get(j.get("ref").getAsString());
                return ref != null && test(c,ref,depth+1);
            }
            if (j.has("and")) for (var child : j.getAsJsonArray("and")) if (!test(c,child.getAsJsonObject(),depth+1)) return false;
            if (j.has("or")) {
                boolean any = false;
                for (var child : j.getAsJsonArray("or")) if (test(c,child.getAsJsonObject(),depth+1)) { any = true; break; }
                if (!any) return false;
            }
            if (j.has("not") && test(c,j.getAsJsonObject("not"),depth+1)) return false;
            if (j.has("type")) {
                var predicate = TYPES.get(j.get("type").getAsString());
                return predicate != null && predicate.test(c,j);
            }
            return testFields(c,j);
        } catch (RuntimeException exception) { return false; }
    }
    private static boolean testFields(RequirementContext c, JsonObject j) {
        for (var entry : j.entrySet()) {
            String key = entry.getKey(); JsonElement value = entry.getValue();
            if (java.util.Set.of("and","or","not","type","ref").contains(key)) continue;
            String actual = string(c.event(),key,"");
            switch (key) {
                case "dimension" -> actual = c.player().level().dimension().location().toString();
                case "biome" -> actual = c.player().level().getBiome(c.player().blockPosition()).unwrapKey().map(k->k.location().toString()).orElse("");
                case "item" -> { if (actual.isEmpty()) actual = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(c.player().getMainHandItem().getItem()).toString(); }
                case "item_tag", "entity_tag", "block_tag", "damage_tag", "biome_tag" -> { if (!tag(c,key,value.getAsString())) return false; continue; }
                case "min_distance", "min_damage", "min_amount", "max_distance", "max_damage", "max_amount" -> {
                    String field = key.substring(4); double amount = number(c.event(),field,0);
                    if (key.startsWith("min_") ? amount < value.getAsDouble() : amount > value.getAsDouble()) return false;
                    continue;
                }
                case "blocking" -> actual = Boolean.toString(c.player().isBlocking());
            }
            if (value.isJsonArray()) { boolean found=false; for(var v:value.getAsJsonArray()) if(v.getAsString().equals(actual)) found=true; if(!found)return false; }
            else if (!value.getAsString().equals(actual)) return false;
        }
        return true;
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static boolean tag(RequirementContext c, String field, String id) {
        var registry = switch(field) {
            case "item_tag" -> Registries.ITEM;
            case "entity_tag" -> Registries.ENTITY_TYPE;
            case "block_tag" -> Registries.BLOCK;
            case "damage_tag" -> Registries.DAMAGE_TYPE;
            default -> Registries.BIOME;
        };
        String name = switch(field) { case "damage_tag" -> "damage_type"; default -> field.replace("_tag",""); };
        String raw = string(c.event(), name, "");
        if (field.equals("item_tag") && raw.isEmpty()) return c.player().getMainHandItem().is(TagKey.create(Registries.ITEM, ResourceLocation.parse(id)));
        if (field.equals("biome_tag")) return c.player().level().getBiome(c.player().blockPosition()).is(TagKey.create(Registries.BIOME,ResourceLocation.parse(id)));
        var r = c.player().registryAccess().registryOrThrow((ResourceKey)registry);
        return r.getHolder(ResourceLocation.parse(raw)).map(h -> ((net.minecraft.core.Holder)h).is(TagKey.create((ResourceKey)registry,ResourceLocation.parse(id)))).orElse(false).equals(true);
    }
    public static String string(JsonObject j,String key,String fallback) { return j.has(key) && j.get(key).isJsonPrimitive() ? j.get(key).getAsString() : fallback; }
    public static double number(JsonObject j,String key,double fallback) { return j.has(key) ? j.get(key).getAsDouble() : fallback; }
}
