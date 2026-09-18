package com.cappleapple.mastery.mechanics;

import com.cappleapple.mastery.data.DefinitionSet;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

import static com.cappleapple.mastery.mechanics.MechanicsRules.*;

/** Schema and cross-reference validation. No live registries are needed by {@link #validate}. */
public final class MechanicsValidation {
    public static final Set<String> EVENTS = Set.of("hit", "kill", "hurt", "death");
    public static final Set<String> ACTIONS = Set.of("keyword", "remove_keyword", "damage", "effect", "spell", "heal", "lightning", "particles");
    private MechanicsValidation() {}

    public static void validate(DefinitionSet definitions, List<String> errors) {
        definitions.triggers().forEach((id, json) -> guarded("triggers/" + id, errors, () -> {
            String path = "triggers/" + id;
            member(json, "event", EVENTS, "", path, errors);
            numeric(json, "chance", 0, 1, false, path, errors);
            numeric(json, "cooldown", 0, 72000, true, path, errors);
            conditions(json, definitions, path, errors);
            actions(json, "actions", true, definitions, path, errors);
        }));
        definitions.keywords().forEach((id, json) -> guarded("keywords/" + id, errors, () -> {
            String path = "keywords/" + id;
            for(String field:List.of("name","description"))if(json.has(field)&&(!json.get(field).isJsonPrimitive()||!json.getAsJsonPrimitive(field).isString()))
                errors.add(path+": "+field+" must be a string");
            numeric(json, "max_stacks", 1, 1024, true, path, errors);
            numeric(json, "duration", 0, 72000, true, path, errors);
            numeric(json, "tick_interval", 1, 72000, true, path, errors);
            numeric(json,"decay_delay",0,72000,true,path,errors);
            numeric(json,"decay_interval",1,72000,true,path,errors);
            numeric(json,"decay_stacks",0,1024,true,path,errors);
            actions(json,"stacks_lost_actions",false,definitions,path,errors);
            actions(json,"all_stacks_lost_actions",false,definitions,path,errors);
            numeric(json, "threshold", 0, 1024, true, path, errors);
            boolField(json, "consume_stacks", path, errors);
            if (number(json, "threshold", 0) > number(json, "max_stacks", 1)) errors.add(path + ": threshold exceeds max_stacks");
            actions(json, "tick_actions", false, definitions, path, errors);
            actions(json, "threshold_actions", false, definitions, path, errors);
            if (json.has("threshold_actions") && !json.getAsJsonArray("threshold_actions").isEmpty() && number(json, "threshold", 0) < 1)
                errors.add(path + ": threshold_actions require a positive threshold");
        }));
        definitions.effects().forEach((id, effect) -> effect(effect, definitions, "effects/" + id, errors));
        definitions.nodes().forEach((id, node) -> {
            for (var effect : node.effects()) effect(effect, definitions, "nodes/" + id + "/effects", errors);
        });
    }

    private static void effect(JsonObject effect, DefinitionSet definitions, String path, List<String> errors) {
        guarded(path, errors, () -> {
            if(text(effect,"type","").equals("mastery:keyword_modifier")) {
                reference(effect,"keyword",definitions.keywords(),path,errors);
                for(String stat:List.of("stacks","damage","duration")) {
                    numeric(effect,stat,-72000,72000,false,path,errors);
                    numeric(effect,stat+"_percent",-100,100,false,path,errors);
                }
            }
            if (text(effect, "type", "").equals("mastery:trigger")) reference(effect, "trigger", definitions.triggers(), path, errors);
        });
    }

    private static void actions(JsonObject parent, String field, boolean required, DefinitionSet definitions, String path, List<String> errors) {
        if (!parent.has(field)) { if (required) errors.add(path + ": missing " + field); return; }
        var values = array(parent, field, 64, path, errors);
        if (values == null) return;
        if (required && values.isEmpty()) errors.add(path + ": actions must not be empty");
        for (int i = 0; i < values.size(); i++) {
            String itemPath = path + "/" + field + "/" + i;
            JsonElement value = values.get(i);
            if (!value.isJsonObject()) { errors.add(itemPath + ": expected an action object"); continue; }
            guarded(itemPath, errors, () -> {
                var action = value.getAsJsonObject();
                member(action, "type", ACTIONS, "", itemPath, errors);
                member(action, "target", Set.of("self", "target", "nearby", "aim"), "target", itemPath, errors);
                member(action, "center", Set.of("self", "target"), "target", itemPath, errors);
                numeric(action, "radius", 0.1, 64, false, itemPath, errors);
                numeric(action, "limit", 1, 64, true, itemPath, errors);
                numeric(action, "chance", 0, 1, false, itemPath, errors);
                for (String flag : List.of("include_allies", "include_players", "per_rank", "per_stack")) boolField(action, flag, itemPath, errors);
                conditions(action, definitions, itemPath, errors);
                if (action.has("element") && !text(action,"element","").isBlank()) identifier(action, "element", itemPath, errors);
                if (action.has("school") && !text(action,"school","").isBlank()) identifier(action, "school", itemPath, errors);
                switch (text(action, "type", "")) {
                    case "keyword", "remove_keyword" -> {
                        reference(action, "keyword", definitions.keywords(), itemPath, errors);
                        numeric(action, "stacks", text(action, "type", "").equals("keyword") ? 1 : 0, 1024, true, itemPath, errors);
                        numeric(action, "duration", 1, 72000, true, itemPath, errors);
                    }
                    case "damage", "lightning" -> {
                        numeric(action, "amount", 0, 1000000, false, itemPath, errors);
                        numeric(action, "damage_fraction", 0, 100, false, itemPath, errors);
                        if (action.has("school") && !text(action,"school","").isBlank()) identifier(action, "school", itemPath, errors);
                    }
                    case "heal" -> numeric(action, "amount", 0, 1000000, false, itemPath, errors);
                    case "effect" -> {
                        identifier(action, "effect", itemPath, errors);
                        numeric(action, "duration", 1, 72000, true, itemPath, errors);
                        numeric(action, "amplifier", 0, 255, true, itemPath, errors);
                    }
                    case "spell" -> {
                        identifier(action, "spell", itemPath, errors);
                        numeric(action, "level", 1, 100, true, itemPath, errors);
                    }
                    case "particles" -> {
                        identifier(action, "particle", itemPath, errors);
                        numeric(action, "count", 1, 256, true, itemPath, errors);
                        numeric(action, "spread", 0, 16, false, itemPath, errors);
                        numeric(action, "speed", 0, 10, false, itemPath, errors);
                    }
                }
            });
        }
    }

    private static void conditions(JsonObject parent, DefinitionSet definitions, String path, List<String> errors) {
        if (!parent.has("conditions")) return;
        var values = array(parent, "conditions", 32, path, errors);
        if (values == null) return;
        for (int i = 0; i < values.size(); i++) {
            String conditionPath = path + "/conditions/" + i;
            if (!values.get(i).isJsonObject()) { errors.add(conditionPath + ": expected a condition object"); continue; }
            var condition = values.get(i).getAsJsonObject();
            guarded(conditionPath, errors, () -> {
                member(condition, "type", Set.of("health", "keyword", "damage"), "", conditionPath, errors);
                member(condition, "target", Set.of("self", "target"), "target", conditionPath, errors);
                if(text(condition,"type","").equals("damage")) {
                    int entries=0;
                    for(String field:DamageContext.FIELDS)if(condition.has(field)) {
                        var filters=array(condition,field,64,conditionPath,errors);if(filters==null)continue;
                        entries+=filters.size();
                        for(var filter:filters) {
                            if(!filter.isJsonPrimitive()||!filter.getAsJsonPrimitive().isString()) {errors.add(conditionPath+": "+field+" requires strings");continue;}
                            String id=filter.getAsString();
                            if(field.equals("categories")) {if(!DamageContext.CATEGORIES.contains(id))errors.add(conditionPath+": unknown damage category "+id);}
                            else if(!id.contains(":")||ResourceLocation.tryParse(id)==null)errors.add(conditionPath+": "+field+" requires namespaced IDs");
                        }
                    }
                    if(entries==0)errors.add(conditionPath+": damage condition requires at least one filter");
                } else if (text(condition, "type", "").equals("health")) {
                    member(condition, "unit", Set.of("points", "fraction"), "points", conditionPath, errors);
                    double maximum = text(condition, "unit", "points").equals("fraction") ? 1 : 1000000;
                    numeric(condition, "min", 0, maximum, false, conditionPath, errors);
                    numeric(condition, "max", 0, maximum, false, conditionPath, errors);
                } else {
                    reference(condition, "keyword", definitions.keywords(), conditionPath, errors);
                    numeric(condition, "min", 0, 1024, true, conditionPath, errors);
                    numeric(condition, "max", 0, 1024, true, conditionPath, errors);
                }
                if (number(condition, "min", 0) > number(condition, "max", Double.MAX_VALUE)) errors.add(conditionPath + ": min exceeds max");
            });
        }
    }

    /** Registry checks run only after common setup, when Iron's and Minecraft registries exist. */
    public static void validateRuntime(DefinitionSet definitions, List<String> errors) {
        definitions.settingOverrides().forEach((id,settings)->{
            if(settings.has("damage_filter")&&settings.get("damage_filter").isJsonObject()) {
                var filter=settings.getAsJsonObject("damage_filter").deepCopy();filter.addProperty("type","damage");
                var list=new JsonArray();list.add(filter);var wrapper=new JsonObject();wrapper.add("conditions",list);
                runtimeConditions(definitions,wrapper,id+"/damage_filter",errors);
            }
        });
        definitions.triggers().forEach((id, value) -> {runtimeConditions(definitions,value,"triggers/"+id,errors);runtimeActions(definitions, value, "actions", "triggers/" + id, errors);});
        definitions.keywords().forEach((id, value) -> {
            runtimeActions(definitions,value,"stacks_lost_actions","keywords/"+id,errors);
            runtimeActions(definitions,value,"all_stacks_lost_actions","keywords/"+id,errors);
            runtimeActions(definitions, value, "tick_actions", "keywords/" + id, errors);
            runtimeActions(definitions, value, "threshold_actions", "keywords/" + id, errors);
        });
    }

    private static void runtimeConditions(DefinitionSet definitions,JsonObject parent,String path,List<String> errors) {
        if(!parent.has("conditions")||!parent.get("conditions").isJsonArray())return;
        for(var raw:parent.getAsJsonArray("conditions"))if(raw.isJsonObject())guarded(path,errors,()->{
            var condition=raw.getAsJsonObject();if(!text(condition,"type","").equals("damage"))return;
            var server=net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            if(server!=null&&condition.has("damage_types"))for(var value:condition.getAsJsonArray("damage_types"))
                if(!server.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).containsKey(ResourceLocation.parse(value.getAsString())))errors.add(path+": unknown native damage type "+value.getAsString());
            if(condition.has("elements"))for(var value:condition.getAsJsonArray("elements")) {
                String id=value.getAsString();if(!definitions.elements().containsKey(id)&&!io.redspace.ironsspellbooks.api.registry.SchoolRegistry.REGISTRY.containsKey(ResourceLocation.parse(id)))
                    errors.add(path+": unknown damage element or school "+id);
            }
        });
    }
    private static void runtimeActions(DefinitionSet definitions, JsonObject parent, String field, String path, List<String> errors) {
        if (!parent.has(field) || !parent.get(field).isJsonArray()) return;
        for (var value : parent.getAsJsonArray(field)) {
            if (!value.isJsonObject()) continue;
            var action = value.getAsJsonObject();
            runtimeConditions(definitions,action,path,errors);
            guarded(path, errors, () -> {
                switch (text(action, "type", "")) {
                    case "spell" -> {
                        var spell = io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell(text(action, "spell", ""));
                        if (spell == io.redspace.ironsspellbooks.api.registry.SpellRegistry.none()) errors.add(path + ": unknown native spell " + action.get("spell"));
                    }
                    case "effect" -> {
                        if (!net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.containsKey(ResourceLocation.parse(text(action, "effect", "")))) errors.add(path + ": unknown mob effect " + action.get("effect"));
                    }
                    case "particles" -> {
                        var id = ResourceLocation.parse(text(action, "particle", ""));
                        if (!(net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.get(id) instanceof net.minecraft.core.particles.SimpleParticleType))
                            errors.add(path + ": particle must be a registered simple particle (no extra parameters): " + id);
                    }
                }
                for (String fieldName : List.of("element", "school")) if (action.has(fieldName) && !text(action,fieldName,"").isBlank()) {
                    String id = text(action, fieldName, "");
                    if (!definitions.elements().containsKey(id) && !io.redspace.ironsspellbooks.api.registry.SchoolRegistry.REGISTRY.containsKey(ResourceLocation.parse(id)))
                        errors.add(path + ": unknown damage type or native school " + id);
                }
            });
        }
    }

    private static void member(JsonObject json, String field, Set<String> choices, String fallback, String path, List<String> errors) {
        if (!choices.contains(text(json, field, fallback))) errors.add(path + ": " + field + " must be one of " + new TreeSet<>(choices));
    }

    private static void numeric(JsonObject json, String field, double min, double max, boolean integral, String path, List<String> errors) {
        if (!json.has(field)) return;
        var value = json.get(field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) { errors.add(path + ": " + field + " must be a number"); return; }
        double amount = value.getAsDouble();
        if (!Double.isFinite(amount) || amount < min || amount > max || integral && amount != Math.rint(amount))
            errors.add(path + ": " + field + " must be " + (integral ? "an integer" : "a finite number") + " between " + min + " and " + max);
    }

    private static void boolField(JsonObject json, String field, String path, List<String> errors) {
        if (json.has(field) && (!json.get(field).isJsonPrimitive() || !json.getAsJsonPrimitive(field).isBoolean())) errors.add(path + ": " + field + " must be a boolean");
    }

    private static JsonArray array(JsonObject json, String field, int maximum, String path, List<String> errors) {
        if (!json.get(field).isJsonArray()) { errors.add(path + ": " + field + " must be an array"); return null; }
        var result = json.getAsJsonArray(field);
        if (result.size() > maximum) errors.add(path + ": " + field + " exceeds " + maximum + " entries");
        return result;
    }

    private static void identifier(JsonObject json, String field, String path, List<String> errors) {
        String value = text(json, field, "");
        if (!value.contains(":") || ResourceLocation.tryParse(value) == null) errors.add(path + ": " + field + " requires a namespaced ID");
    }

    private static void reference(JsonObject json, String field, Map<String, ?> known, String path, List<String> errors) {
        identifier(json, field, path, errors);
        if (!known.containsKey(text(json, field, ""))) errors.add(path + ": missing " + field + " " + text(json, field, ""));
    }

    private static void guarded(String path, List<String> errors, Runnable check) {
        try { check.run(); } catch (RuntimeException ex) { errors.add(path + ": malformed mechanics data: " + ex.getMessage()); }
    }
}
