package com.cappleapple.mastery.data;

import com.google.gson.JsonObject;
import java.util.List;

/** Immutable mechanics definition. IDs, dependencies and chronology drive client layout. */
public record NodeDefinition(String id, String tree, String name, String description, String icon,
        NodeType type, List<Dependency> dependencies, int maxRank, int cost, int level, int worldTier,
        List<JsonObject> requirements, List<JsonObject> effects, String visibility,
        List<String> exclusions, boolean toggleable, String spell, String modifier, String bookToken, JsonObject rootTree, String authoredTree) {
    public NodeDefinition(String id, String tree, String name, String description, String icon,
            NodeType type, List<Dependency> dependencies, int maxRank, int cost, int level, int worldTier,
            List<JsonObject> requirements, List<JsonObject> effects, String visibility,
            List<String> exclusions, boolean toggleable, String spell, String modifier, String bookToken) {
        this(id, tree, name, description, icon, type, dependencies, maxRank, cost, level, worldTier,
                requirements, effects, visibility, exclusions, toggleable, spell, modifier, bookToken, null, tree);
    }
    public NodeDefinition withTree(String owner) {
        return new NodeDefinition(id,owner,name,description,icon,type,dependencies,maxRank,cost,level,worldTier,
                requirements,effects,visibility,exclusions,toggleable,spell,modifier,bookToken,rootTree,authoredTree);
    }
    public NodeDefinition(String id, String tree, String name, String description, String icon,
            NodeType type, List<Dependency> dependencies, int maxRank, int cost, int level, int worldTier,
            List<JsonObject> requirements, List<JsonObject> effects, String visibility,
            List<String> exclusions, boolean toggleable, String spell, String modifier) {
        this(id, tree, name, description, icon, type, dependencies, maxRank, cost, level, worldTier,
                requirements, effects, visibility, exclusions, toggleable, spell, modifier, "");
    }
    public boolean treeModifier(){return modifier.equals("mastery:tree");}
    public boolean spellModifier(){return !modifier.isBlank()&&!treeModifier();}
    public List<Dependency> dependencyLeaves(){return dependencies.stream().flatMap(d->d.leaves().stream()).toList();}
    public boolean dependenciesMet(java.util.function.Predicate<Dependency> condition){return dependencies.stream().allMatch(d->d.test(condition));}
    public NodeDefinition {
        if(type == NodeType.MODIFIER && (modifier == null || modifier.isBlank())) modifier = "mastery:native_spell";
        rootTree = rootTree == null ? null : rootTree.deepCopy();
        authoredTree = authoredTree == null || authoredTree.isBlank() ? tree : authoredTree;
        dependencies = List.copyOf(dependencies);
        requirements = requirements.stream().map(JsonObject::deepCopy).toList();
        effects = effects.stream().map(JsonObject::deepCopy).toList();
        exclusions = List.copyOf(exclusions);
    }
}
