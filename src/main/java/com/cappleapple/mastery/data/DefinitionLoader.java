package com.cappleapple.mastery.data;

import com.cappleapple.mastery.graph.GraphValidator;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Strict JSON boundary shared by resource reloads and the one-time definition sync. */
public final class DefinitionLoader {
    public static final List<String> KINDS = List.of("groups", "trees", "nodes", "spells", "contexts",
            "xp_sources", "synergies", "requirements", "effects", "settings", "elements", "triggers", "keywords", "mob_types", "weapon_types", "classes");
    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
    private DefinitionLoader() {}

    public record LoadResult(DefinitionSet definitions, List<String> errors) {
        public LoadResult { errors = List.copyOf(errors); }
        public boolean valid() { return errors.isEmpty(); }
    }

    /** Parses every file independently; callers must retain the old snapshot if errors are nonempty. */
    public static LoadResult load(Map<String, Map<String, JsonObject>> byKind) {
        Map<String, GroupDefinition> groups = new LinkedHashMap<>();
        Map<String, TreeDefinition> trees = new LinkedHashMap<>();
        Map<String, NodeDefinition> nodes = new LinkedHashMap<>();
        Map<String, SpellDefinition> spells = new LinkedHashMap<>();
        Map<String, ContextDefinition> contexts = new LinkedHashMap<>();
        Map<String, XpSourceDefinition> xp = new LinkedHashMap<>();
        Map<String, JsonObject> requirements = new LinkedHashMap<>();
        Map<String, JsonObject> effects = new LinkedHashMap<>();
        Map<String, JsonObject> elements = new LinkedHashMap<>(), triggers = new LinkedHashMap<>(), keywords = new LinkedHashMap<>(), mobTypes = new LinkedHashMap<>(), weaponTypes = new LinkedHashMap<>();
        Map<String, JsonObject> classes = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        Map<String,JsonObject> overrides = new LinkedHashMap<>();
        UnlockPresentation[] defaults = {UnlockPresentation.DEFAULTS};
        byKind.forEach((kind, resources) -> resources.forEach((id, json) -> {
            try {
                if (bool(json, "disabled", false)) return;
                Object definition = parse(kind, id, json);
                if(List.of("settings","trees","nodes","synergies","spells").contains(kind))
                    overrides.put((kind.equals("synergies")?"nodes":kind)+"/"+id,SettingsResolver.extract(json));
                switch (kind) {
                    case "settings" -> defaults[0] = (UnlockPresentation) definition;
                    case "groups" -> groups.put(id, (GroupDefinition) definition);
                    case "trees" -> trees.put(id, (TreeDefinition) definition);
                    case "nodes", "synergies" -> {
                        if (nodes.putIfAbsent(id, (NodeDefinition) definition) != null)
                            errors.add(kind + "/" + id + ": duplicate node ID across nodes and synergies");
                    }
                    case "spells" -> spells.put(id, (SpellDefinition) definition);
                    case "contexts" -> contexts.put(id, (ContextDefinition) definition);
                    case "xp_sources" -> xp.put(id, (XpSourceDefinition) definition);
                    case "requirements" -> requirements.put(id, (JsonObject) definition);
                    case "effects" -> effects.put(id, (JsonObject) definition);
                    case "elements" -> elements.put(id, (JsonObject) definition);
                    case "triggers" -> triggers.put(id, (JsonObject) definition);
                    case "keywords" -> keywords.put(id, (JsonObject) definition);
                    case "mob_types" -> mobTypes.put(id, (JsonObject) definition);
                    case "weapon_types" -> weaponTypes.put(id, (JsonObject) definition);
                    case "classes" -> classes.put(id, (JsonObject) definition);
                    default -> throw new JsonParseException("unknown definition directory " + kind);
                }
            } catch (RuntimeException ex) {
                errors.add(kind + "/" + id + ": " + ex.getMessage());
            }
        }));
        PromotedTrees.expand(trees,nodes,overrides,errors);
        DefinitionSet budgetDefinitions=new DefinitionSet(groups,trees,nodes,spells,contexts,xp,requirements,effects,defaults[0],overrides);
        trees.replaceAll((id,tree)->{
            try{return tree.withMaxLevel(com.cappleapple.mastery.progression.TreeLevelBudget.maximum(tree,budgetDefinitions));}
            catch(RuntimeException error){errors.add("trees/"+id+": "+error.getMessage());return tree.withMaxLevel(0);}
        });
        DefinitionSet result = new DefinitionSet(groups, trees, nodes, spells, contexts, xp, requirements, effects, defaults[0],overrides,elements,triggers,keywords,mobTypes,weaponTypes,classes);
        errors.addAll(GraphValidator.validate(result).errors());
        try {SettingsResolver.validate(SettingsResolver.resolved(result,"","",""),"");}catch(RuntimeException ex){errors.add("settings/mastery:defaults: "+ex.getMessage());}
        trees.keySet().forEach(id->{try{SettingsResolver.validate(SettingsResolver.forNode(result,id),"");}catch(RuntimeException ex){errors.add("trees/"+id+": "+ex.getMessage());}});
        nodes.values().forEach(n->{try{SettingsResolver.validate(SettingsResolver.forNode(result,n.id()),n.spell());}catch(RuntimeException ex){errors.add("nodes/"+n.id()+": "+ex.getMessage());}});
        spells.values().forEach(spell->{try{SettingsResolver.validate(SettingsResolver.resolved(result,spell.tree(),spell.id(),""),spell.id());}catch(RuntimeException ex){errors.add("spells/"+spell.id()+": "+ex.getMessage());}});
        return new LoadResult(result, errors);
    }

    /** Reads a single resource. Errors include the field name; the caller adds the resource path. */
    public static Object parse(String kind, String id, JsonObject json) {
        if (!id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) throw new JsonParseException("invalid resource ID " + id);
        json=json.deepCopy();
        for(String field:SettingsResolver.FIELDS)if(json.has(field)&&json.get(field).isJsonPrimitive()&&json.get(field).getAsString().equals("default"))json.remove(field);
        JsonObject resolvedSettings=SettingsResolver.merge(SettingsResolver.builtins(kind.equals("spells")?id:""),SettingsResolver.extract(json));
        if(List.of("settings","trees","nodes","synergies","spells").contains(kind))try{SettingsResolver.validate(resolvedSettings,kind.equals("spells")?id:"");}
        catch(RuntimeException ex){throw new JsonParseException("Invalid theme, unlock, charge, or modifier_slots settings: "+ex.getMessage(),ex);}
        return switch (kind) {
            case "groups" -> new GroupDefinition(id, string(json, "name", id), string(json, "description", ""),
                    string(json, "icon", "minecraft:book"), string(json, "parent", ""));
            case "settings" -> {
                if (!id.equals("mastery:defaults")) throw new JsonParseException("settings must use mastery:defaults");
                yield UnlockPresentation.parse(object(json,"unlock",new JsonObject())).resolve(UnlockPresentation.DEFAULTS);
            }
            case "trees" -> tree(id, json);
            case "nodes", "synergies" -> node(id, json, kind.equals("synergies"));
            case "spells" -> spell(id, json);
            case "contexts" -> new ContextDefinition(id, string(json, "name", id), integer(json, "priority", 0),
                    object(json, "condition", new JsonObject()));
            case "xp_sources" -> new XpSourceDefinition(id, requiredString(json, "tree"), requiredString(json, "event"),
                    number(json, "amount", 1), string(json, "scale", "none"), object(json, "condition", new JsonObject()),
                    integer(json, "points", 0), bool(json, "once", false));
            case "requirements", "effects", "elements", "triggers", "keywords", "mob_types", "weapon_types", "classes" -> json.deepCopy();
            default -> throw new JsonParseException("unknown definition directory " + kind);
        };
    }

    private static TreeDefinition tree(String id, JsonObject json) {
        Map<Integer, Integer> milestones = new LinkedHashMap<>();
        if (json.has("point_milestones")) {
            JsonElement element = json.get("point_milestones");
            if (element.isJsonArray()) {
                for (JsonElement value : element.getAsJsonArray()) milestones.merge(exactInt(value, "point_milestones"), 1, Integer::sum);
            } else if (element.isJsonObject()) {
                for (var entry : element.getAsJsonObject().entrySet()) {
                    try { milestones.put(Integer.parseInt(entry.getKey()), exactInt(entry.getValue(), "point_milestones." + entry.getKey())); }
                    catch (NumberFormatException ex) { throw new JsonParseException("point_milestones keys must be levels"); }
                }
            } else throw new JsonParseException("point_milestones must be an array or object");
        }
        List<TierCap> caps = new ArrayList<>();
        for (JsonElement entry : array(json, "tier_caps")) {
            JsonObject cap = asObject(entry, "tier_caps entry");
            caps.add(new TierCap(integer(cap, "tier", 0), integer(cap, "max_level", -1),
                    integer(cap, "max_rank", -1), integer(cap, "max_depth", -1),
                    integer(cap, "modifier_slots", -1), integer(cap, "active_capacity", -1)));
        }
        return new TreeDefinition(id, string(json, "name", id), string(json, "description", ""),
                string(json, "icon", "minecraft:book"), string(json, "parent", ""), integer(json, "max_level", 0),
                number(json, "xp_base", 100), number(json, "xp_growth", 20), integer(json, "point_every", 1),
                milestones, string(json, "point_formula", ""), caps, string(json, "section", "south"), TreeTheme.parse(SettingsResolver.merge(TreeTheme.DEFAULT.toJson(),object(json, "theme", new JsonObject()))), UnlockPresentation.parse(object(json,"unlock",new JsonObject())),integer(json,"points_per_award",1),string(json,"xp_attribute",""));
    }

    private static Dependency dependency(JsonElement value,int depth) {
        if(depth>32)throw new JsonParseException("Dependency groups exceed 32 nesting levels");
        if(value.isJsonPrimitive()&&value.getAsJsonPrimitive().isString())return new Dependency(value.getAsString(),1);
        var json=asObject(value,"dependency");
        boolean all=json.has("and")&&!array(json,"and").isEmpty(),any=json.has("or")&&!array(json,"or").isEmpty();
        if(json.has("node")&&!string(json,"node","").isBlank()) {
            if(all||any)throw new JsonParseException("Use a node/rank condition or an AND/OR group, not both");
            return new Dependency(requiredString(json,"node"),integer(json,"rank",1));
        }
        if(all==any)throw new JsonParseException("Dependency group needs one nonempty and or or array");
        var children=new ArrayList<Dependency>();for(var entry:array(json,all?"and":"or"))children.add(dependency(entry,depth+1));
        return Dependency.group(all,children);
    }

    private static NodeDefinition node(String id, JsonObject json, boolean synergy) {
        if (json.has("ability")) throw new JsonParseException("ability was removed; spell must reference an existing native spell ID");
        List<Dependency> dependencies = new ArrayList<>();
        for(JsonElement entry:array(json,"dependencies"))dependencies.add(dependency(entry,0));
        NodeType type;
        try { type = NodeType.valueOf(string(json, "type", synergy ? "synergy" : "passive").toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { throw new JsonParseException("unknown node type"); }
        return new NodeDefinition(id, requiredString(json, "tree"), string(json, "name", id),
                string(json, "description", ""), string(json, "icon", "minecraft:book"), type, dependencies,
                integer(json, "max_rank", 1), integer(json, "cost", 1), integer(json, "level", 0),
                integer(json, "world_tier", 0), objects(json, "requirements"), objects(json, "effects"),
                string(json, "visibility", "available"), strings(json, "exclusions"), bool(json, "toggleable", false),
                string(json, "spell", ""), string(json, "modifier", ""), string(json, "book_token", ""),PromotedTrees.parse(json),requiredString(json,"tree"));
    }

    private static SpellDefinition spell(String id, JsonObject json) {
        String nativeId = requiredString(json, "spell");
        if (!nativeId.equals(id)) throw new JsonParseException("spell must match the native registry ID in the resource path: " + id);
        for (String field : List.of("behavior", "cooldown", "parameters"))
            if (json.has(field)) throw new JsonParseException(field + " is owned by Iron's Spells and cannot be defined by Mastery");
        return new SpellDefinition(id, requiredString(json, "tree"), string(json, "name", id),
                string(json, "description", ""), string(json, "icon", "minecraft:book"), strings(json, "contexts"),
                json.has("modifier_slots") && json.get("modifier_slots").isJsonPrimitive() && json.getAsJsonPrimitive("modifier_slots").isNumber() ? integer(json,"modifier_slots",2):2, integer(json, "level", 1), ChargeDefinition.parse(id,SettingsResolver.merge(SettingsResolver.builtins(id),SettingsResolver.extract(json)).getAsJsonObject("charge")));
    }

    public static JsonObject toJson(DefinitionSet definitions) {
        JsonObject json = new JsonObject();
        json.add("groups", GSON.toJsonTree(definitions.groups()));
        json.add("trees", GSON.toJsonTree(definitions.trees()));
        json.add("nodes", GSON.toJsonTree(definitions.nodes()));
        definitions.nodes().forEach((id,node)->{var dependencies=new JsonArray();node.dependencies().forEach(d->dependencies.add(d.toJson()));json.getAsJsonObject("nodes").getAsJsonObject(id).add("dependencies",dependencies);});
        JsonObject spells = new JsonObject();
        definitions.spells().forEach((id, definition) -> {
            JsonObject value = GSON.toJsonTree(definition).getAsJsonObject();
            value.addProperty("spell", id);
            spells.add(id, value);
        });
        json.add("spells", spells);
        json.add("contexts", GSON.toJsonTree(definitions.contexts()));
        json.add("xp_sources", GSON.toJsonTree(definitions.xpSources()));
        json.add("requirements", GSON.toJsonTree(definitions.requirements()));
        json.add("effects", GSON.toJsonTree(definitions.effects()));
        json.add("elements", GSON.toJsonTree(definitions.elements()));
        json.add("triggers", GSON.toJsonTree(definitions.triggers()));
        json.add("keywords", GSON.toJsonTree(definitions.keywords()));
        json.add("mob_types", GSON.toJsonTree(definitions.mobTypes()));
        json.add("weapon_types", GSON.toJsonTree(definitions.weaponTypes()));
        json.add("classes", GSON.toJsonTree(definitions.classes()));
        JsonObject settings=new JsonObject(), defaults=new JsonObject();
        defaults.add("unlock",definitions.unlockDefaults().toJson());settings.add("mastery:defaults",defaults);json.add("settings",settings);
        definitions.settingOverrides().forEach((key,values)->{
            int slash=key.indexOf('/');String kind=key.substring(0,slash),id=key.substring(slash+1);
            if(json.has(kind)&&json.getAsJsonObject(kind).has(id)) {
                JsonObject value=json.getAsJsonObject(kind).getAsJsonObject(id);
                SettingsResolver.FIELDS.forEach(value::remove);values.entrySet().forEach(entry->value.add(entry.getKey(),entry.getValue().deepCopy()));
            }
        });
        json.getAsJsonObject("trees").entrySet().forEach(entry->entry.getValue().getAsJsonObject().remove("max_level"));
        PromotedTrees.prepareJson(definitions,json);
        return json;
    }

    /** Definition packets use the same validation as datapack resources. */
    public static DefinitionSet fromJson(JsonObject json) {
        Map<String, Map<String, JsonObject>> resources = new LinkedHashMap<>();
        for (String kind : KINDS) {
            Map<String, JsonObject> values = new LinkedHashMap<>();
            for (var entry : object(json, kind, new JsonObject()).entrySet()) values.put(entry.getKey(), asObject(entry.getValue(), kind + "." + entry.getKey()));
            resources.put(kind, values);
        }
        LoadResult result = load(resources);
        if (!result.valid()) throw new JsonParseException(String.join("; ", result.errors()));
        return result.definitions();
    }

    private static String requiredString(JsonObject json, String field) {
        String value = string(json, field, "");
        if (value.isBlank()) throw new JsonParseException(field + " is required");
        return value;
    }

    private static String string(JsonObject json, String field, String fallback) {
        if (!json.has(field)) return fallback;
        JsonElement value = json.get(field);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new JsonParseException(field + " must be a string");
        return value.getAsString();
    }

    private static int integer(JsonObject json, String field, int fallback) {
        return json.has(field) ? exactInt(json.get(field), field) : fallback;
    }

    private static int exactInt(JsonElement element, String field) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) throw new JsonParseException(field + " must be an integer");
        try { return element.getAsBigDecimal().intValueExact(); }
        catch (ArithmeticException | NumberFormatException ex) { throw new JsonParseException(field + " must be a 32-bit integer"); }
    }

    private static double number(JsonObject json, String field, double fallback) {
        return json.has(field) ? finiteNumber(json.get(field), field) : fallback;
    }

    private static double finiteNumber(JsonElement element, String field) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) throw new JsonParseException(field + " must be a number");
        double value = element.getAsDouble();
        if (!Double.isFinite(value)) throw new JsonParseException(field + " must be finite");
        return value;
    }

    private static boolean bool(JsonObject json, String field, boolean fallback) {
        if (!json.has(field)) return fallback;
        JsonElement element = json.get(field);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) throw new JsonParseException(field + " must be a boolean");
        return element.getAsBoolean();
    }

    private static JsonObject object(JsonObject json, String field, JsonObject fallback) {
        return json.has(field) ? asObject(json.get(field), field).deepCopy() : fallback;
    }

    private static JsonObject asObject(JsonElement element, String field) {
        if (!element.isJsonObject()) throw new JsonParseException(field + " must be an object");
        return element.getAsJsonObject();
    }

    private static JsonArray array(JsonObject json, String field) {
        if (!json.has(field)) return new JsonArray();
        if (!json.get(field).isJsonArray()) throw new JsonParseException(field + " must be an array");
        return json.getAsJsonArray(field);
    }

    private static List<String> strings(JsonObject json, String field) {
        List<String> values = new ArrayList<>();
        for (JsonElement entry : array(json, field)) {
            if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) throw new JsonParseException(field + " entries must be strings");
            values.add(entry.getAsString());
        }
        return values;
    }

    private static List<JsonObject> objects(JsonObject json, String field) {
        List<JsonObject> values = new ArrayList<>();
        for (JsonElement entry : array(json, field)) {
            if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) {
                JsonObject reference = new JsonObject();
                reference.addProperty("ref", entry.getAsString());
                values.add(reference);
            } else values.add(asObject(entry, field + " entry").deepCopy());
        }
        return values;
    }
}
