package com.cappleapple.mastery.data;

import com.google.gson.JsonObject;
import java.util.Map;

/** Complete atomic reload snapshot. Invalid candidate snapshots never replace the current one. */
public record DefinitionSet(Map<String, GroupDefinition> groups, Map<String, TreeDefinition> trees,
        Map<String, NodeDefinition> nodes, Map<String, SpellDefinition> spells,
        Map<String, ContextDefinition> contexts, Map<String, XpSourceDefinition> xpSources,
        Map<String, JsonObject> requirements, Map<String, JsonObject> effects, UnlockPresentation unlockDefaults, Map<String,JsonObject> settingOverrides,
        Map<String,JsonObject> elements, Map<String,JsonObject> triggers, Map<String,JsonObject> keywords, Map<String,JsonObject> mobTypes, Map<String,JsonObject> weaponTypes, Map<String,JsonObject> classes) {
    public DefinitionSet(Map<String, GroupDefinition> groups, Map<String, TreeDefinition> trees,
            Map<String, NodeDefinition> nodes, Map<String, SpellDefinition> spells,
            Map<String, ContextDefinition> contexts, Map<String, XpSourceDefinition> xpSources,
            Map<String, JsonObject> requirements, Map<String, JsonObject> effects, UnlockPresentation unlockDefaults, Map<String,JsonObject> settingOverrides,
            Map<String,JsonObject> elements, Map<String,JsonObject> triggers, Map<String,JsonObject> keywords, Map<String,JsonObject> mobTypes, Map<String,JsonObject> weaponTypes) {
        this(groups,trees,nodes,spells,contexts,xpSources,requirements,effects,unlockDefaults,settingOverrides,elements,triggers,keywords,mobTypes,weaponTypes,Map.of());
    }
    public static final DefinitionSet EMPTY = new DefinitionSet(Map.of(), Map.of(), Map.of(), Map.of(),
            Map.of(), Map.of(), Map.of(), Map.of());
    public DefinitionSet(Map<String, GroupDefinition> groups, Map<String, TreeDefinition> trees,
            Map<String, NodeDefinition> nodes, Map<String, SpellDefinition> spells,
            Map<String, ContextDefinition> contexts, Map<String, XpSourceDefinition> xpSources,
            Map<String, JsonObject> requirements, Map<String, JsonObject> effects,UnlockPresentation unlockDefaults,Map<String,JsonObject> settingOverrides) {
        this(groups,trees,nodes,spells,contexts,xpSources,requirements,effects,unlockDefaults,settingOverrides,Map.of(),Map.of(),Map.of(),Map.of(),Map.of());
    }
    public DefinitionSet(Map<String, GroupDefinition> groups, Map<String, TreeDefinition> trees,
            Map<String, NodeDefinition> nodes, Map<String, SpellDefinition> spells,
            Map<String, ContextDefinition> contexts, Map<String, XpSourceDefinition> xpSources,
            Map<String, JsonObject> requirements, Map<String, JsonObject> effects) {
        this(groups,trees,nodes,spells,contexts,xpSources,requirements,effects,UnlockPresentation.DEFAULTS,Map.of());
    }
    public DefinitionSet(Map<String, GroupDefinition> groups, Map<String, TreeDefinition> trees,
            Map<String, NodeDefinition> nodes, Map<String, SpellDefinition> spells,
            Map<String, ContextDefinition> contexts, Map<String, XpSourceDefinition> xpSources,
            Map<String, JsonObject> requirements, Map<String, JsonObject> effects,UnlockPresentation unlockDefaults) {
        this(groups,trees,nodes,spells,contexts,xpSources,requirements,effects,unlockDefaults,Map.of());
    }
    public DefinitionSet {
        unlockDefaults = unlockDefaults == null ? UnlockPresentation.DEFAULTS : unlockDefaults.resolve(UnlockPresentation.DEFAULTS);
        settingOverrides=Map.copyOf(settingOverrides);
        groups = Map.copyOf(groups);
        trees = Map.copyOf(trees);
        nodes = Map.copyOf(nodes);
        spells = Map.copyOf(spells);
        contexts = Map.copyOf(contexts);
        xpSources = Map.copyOf(xpSources);
        requirements = Map.copyOf(requirements);
        effects = Map.copyOf(effects);
        elements = Map.copyOf(elements);
        triggers = Map.copyOf(triggers);
        keywords = Map.copyOf(keywords);
        mobTypes = Map.copyOf(mobTypes);
        weaponTypes = Map.copyOf(weaponTypes);
        classes = Map.copyOf(classes);
    }
    public JsonObject toJson() { return DefinitionLoader.toJson(this); }
    public static DefinitionSet fromJson(JsonObject json) { return DefinitionLoader.fromJson(json); }
}
