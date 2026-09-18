package com.cappleapple.mastery.graph;

import com.cappleapple.mastery.data.*;
import com.cappleapple.mastery.progression.PointFormula;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reload-time validation. No Minecraft objects are needed, so datapacks can be tested offline. */
public final class GraphValidator {
    private static volatile DepthCache depthCache;
    private record DepthCache(DefinitionSet definitions, Map<String, Integer> depths) {}
    private GraphValidator() {}

    /** Immutable snapshots cache dependency depths once; purchasing never reparses point formulas. */
    public static int depthOf(DefinitionSet definitions, String nodeId) {
        DepthCache cached = depthCache;
        if (cached == null || cached.definitions() != definitions) {
            Map<String, Set<String>> graph = new LinkedHashMap<>();
            definitions.nodes().forEach((id, node) -> {
                Set<String> parents = new HashSet<>();
                boolean independent=PromotedTrees.root(definitions,node.tree())!=null;
                node.dependencyLeaves().forEach(dependency -> {
                    NodeDefinition parent=definitions.nodes().get(dependency.node());
                    if(!independent||parent!=null&&parent.tree().equals(node.tree()))parents.add(dependency.node());
                });
                graph.put(id, parents);
            });
            cached = new DepthCache(definitions, Map.copyOf(depths(graph, "node dependencies", new ArrayList<>())));
            depthCache = cached;
        }
        return cached.depths().getOrDefault(nodeId, 0);
    }

    public record ValidationResult(List<String> errors, Map<String, Integer> depths) {
        public ValidationResult { errors = List.copyOf(errors); depths = Map.copyOf(depths); }
        public boolean valid() { return errors.isEmpty(); }
    }

    public static ValidationResult validate(DefinitionSet definitions) {
        List<String> errors = new ArrayList<>();
        com.cappleapple.mastery.elemental.ElementalValidation.validate(definitions,errors);
        com.cappleapple.mastery.mechanics.MechanicsValidation.validate(definitions,errors);
        com.cappleapple.mastery.classes.ClassValidation.validate(definitions,errors);
        com.cappleapple.mastery.progression.ExperienceRules.validate(definitions,errors);
        Map<String, Set<String>> hierarchy = new LinkedHashMap<>();
        definitions.groups().forEach((id, group) -> hierarchy.put(id, parent(group.parent())));
        definitions.trees().forEach((id, tree) -> {
            if (hierarchy.putIfAbsent(id, parent(tree.parent())) != null) errors.add("trees/" + id + ": ID also belongs to an organizational group");
            validateTree(tree, errors);
        });
        hierarchy.forEach((id, parents) -> parents.forEach(parent -> {
            if (!hierarchy.containsKey(parent)) errors.add(id + ": missing organizational parent " + parent);
        }));
        depths(hierarchy, "organizational hierarchy", errors);

        Map<String, Set<String>> dependencies = new LinkedHashMap<>();
        for (NodeDefinition node : definitions.nodes().values()) {
            String path = "nodes/" + node.id();
            TreeDefinition tree = definitions.trees().get(node.tree());
            if (tree == null) errors.add(path + ": missing tree " + node.tree());
            if (node.maxRank() < 1) errors.add(path + ": max_rank must be a positive integer");
            if (!node.bookToken().isEmpty() && !node.bookToken().matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))
                errors.add(path + ": book_token must be a resource ID");
            if (node.cost() < 0) errors.add(path + ": cost must not be negative");
            if (node.level() < 0 || tree != null && node.level() > tree.maxLevel()) errors.add(path + ": level exceeds the tree's possible levels");
            if (node.worldTier() < 0) errors.add(path + ": world_tier must not be negative");
            if (!Set.of("always", "discovered", "available", "invested", "hidden").contains(node.visibility()))
                errors.add(path + ": visibility must be always, discovered, available, invested or hidden");
            Set<String> parents = new HashSet<>();
            for (Dependency dependency : node.dependencyLeaves()) {
                parents.add(dependency.node());
                NodeDefinition target = definitions.nodes().get(dependency.node());
                if (target == null) errors.add(path + ": missing dependency node " + dependency.node());
                else if (dependency.rank() < 1 || dependency.rank() > target.maxRank())
                    errors.add(path + ": impossible rank " + dependency.rank() + " for " + dependency.node() + " (max " + target.maxRank() + ")");

            }
            if(!node.dependenciesMet(leaf->!node.exclusions().contains(leaf.node())))errors.add(path+": excludes a required dependency");
            dependencies.put(node.id(), parents);
            for (String exclusion : node.exclusions()) {
                if (!definitions.nodes().containsKey(exclusion)) errors.add(path + ": missing exclusion node " + exclusion);
                if (exclusion.equals(node.id())) errors.add(path + ": node cannot exclude itself");
                NodeDefinition target = definitions.nodes().get(exclusion);
                if (target != null && !target.dependenciesMet(dependency -> !dependency.node().equals(node.id())))
                    errors.add(path + ": exclusion " + exclusion + " depends on this node");
            }
            if (!node.spell().isEmpty() && !definitions.spells().containsKey(node.spell())) errors.add(path + ": missing spell " + node.spell());
            for (JsonObject requirement : node.requirements()) validateReferences(requirement, definitions, errors, path + "/requirements", false, 0);
            for (JsonObject effect : node.effects()) validateReferences(effect, definitions, errors, path + "/effects", true, 0);
            if (node.type() == NodeType.SYNERGY) {
                Set<String> related = new HashSet<>();
                related.add(node.tree());
                for (Dependency dependency : node.dependencyLeaves()) {
                    NodeDefinition target = definitions.nodes().get(dependency.node());
                    if (target != null) related.add(target.tree());
                }
                for (JsonObject requirement : node.requirements()) collectTrees(requirement, definitions, related, new HashSet<>());
                if (related.size() < 2) errors.add(path + ": synergy must reference at least two different trees");
            }
        }
        Map<String, Integer> depths = depths(dependencies, "node dependencies", errors);
        validateNamedReferences(definitions.requirements(), "requirements", errors);
        validateNamedReferences(definitions.effects(), "effects", errors);
        definitions.requirements().forEach((id, requirement) -> validateReferences(requirement, definitions, errors, "requirements/" + id, false, 0));
        definitions.effects().forEach((id, effect) -> validateReferences(effect, definitions, errors, "effects/" + id, true, 0));
        for (SpellDefinition spell : definitions.spells().values()) {
            String path = "spells/" + spell.id();
            if (!definitions.trees().containsKey(spell.tree())) errors.add(path + ": missing tree " + spell.tree());
            for (String context : spell.contexts()) if (!definitions.contexts().containsKey(context)) errors.add(path + ": missing context " + context);
            if (spell.modifierSlots() < 0) errors.add(path + ": modifier_slots cannot be negative");
            if (spell.level() < 1) errors.add(path + ": level must be positive");
        }
        for (XpSourceDefinition source : definitions.xpSources().values()) {
            if (!definitions.trees().containsKey(source.tree())) errors.add("xp_sources/" + source.id() + ": missing tree " + source.tree());
            if (!Double.isFinite(source.amount()) || source.amount() < 0 || source.points() < 0) errors.add("xp_sources/" + source.id() + ": XP and points must be nonnegative");
        }
        com.cappleapple.mastery.costs.CostResolver.validate(definitions,errors);
        return new ValidationResult(errors, depths);
    }

    private static void validateTree(TreeDefinition tree, List<String> errors) {
        String path = "trees/" + tree.id();
        if (!Set.of("north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest").contains(tree.section())) errors.add(path + ": section must be north, northeast, east, southeast, south, southwest, west or northwest");
        if (tree.maxLevel() < 0) errors.add(path + ": Calculated maximum level cannot be negative");
        if (!Double.isFinite(tree.xpBase()) || tree.xpBase() <= 0 || !Double.isFinite(tree.xpGrowth()) || tree.xpGrowth() < 0
                || !Double.isFinite(tree.xpForLevel(tree.maxLevel()))) errors.add(path + ": XP curve must be positive and finite, with nonnegative growth");
        if (tree.pointEvery() < 0) errors.add(path + ": point_every cannot be negative; use 0 to disable periodic grants");
        if(tree.pointsPerAward()<0)errors.add(path+": points_per_award cannot be negative");
        for (var milestone : tree.pointMilestones().entrySet()) {
            if (milestone.getKey() < 1 || milestone.getValue() < 0)
                errors.add(path + ": invalid point milestone " + milestone.getKey());
        }
        if (!tree.pointFormula().isEmpty() && tree.maxLevel() > 0 && tree.maxLevel() <= 100000) {
            try {
                int previous = PointFormula.total(tree.pointFormula(), 0);
                if (previous != 0) errors.add(path + ": point_formula must give zero cumulative points at level 0");
                for (int level = 1; level <= tree.maxLevel(); level++) {
                    int total = PointFormula.total(tree.pointFormula(), level);
                    if (total < previous) throw new IllegalArgumentException("point_formula decreases at level " + level);
                    previous = total;
                }
            } catch (IllegalArgumentException ex) { errors.add(path + ": " + ex.getMessage()); }
        }
        Set<Integer> tiers = new HashSet<>();
        for (TierCap cap : tree.tierCaps()) {
            if (cap.tier() < 0 || !tiers.add(cap.tier())) errors.add(path + ": tier_caps tiers must be unique and nonnegative");
            if (cap.maxLevel() < -1 || cap.maxRank() < -1 || cap.maxDepth() < -1 || cap.modifierSlots() < -1 || cap.activeCapacity() < -1)
                errors.add(path + ": tier cap limits must be nonnegative or -1 for unlimited");
        }
    }

    private static Set<String> parent(String id) { return id == null || id.isEmpty() ? Set.of() : Set.of(id); }

    /** Kahn traversal avoids Java stack limits even with thousands of dependency nodes. */
    private static Map<String, Integer> depths(Map<String, Set<String>> graph, String name, List<String> errors) {
        Map<String, Integer> incoming = new HashMap<>();
        Map<String, List<String>> children = new HashMap<>();
        Map<String, Integer> result = new HashMap<>();
        ArrayDeque<String> ready = new ArrayDeque<>();
        graph.forEach((id, parents) -> {
            int present = 0;
            for (String parent : parents) {
                if (graph.containsKey(parent)) {
                    present++;
                    children.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(id);
                }
            }
            incoming.put(id, present);
            result.put(id, 0);
            if (present == 0) ready.add(id);
        });
        int visited = 0;
        while (!ready.isEmpty()) {
            String id = ready.removeFirst();
            visited++;
            for (String child : children.getOrDefault(id, List.of())) {
                result.merge(child, result.get(id) + 1, Math::max);
                if (incoming.merge(child, -1, Integer::sum) == 0) ready.addLast(child);
            }
        }
        if (visited != graph.size()) {
            List<String> blocked = incoming.entrySet().stream().filter(entry -> entry.getValue() > 0).map(Map.Entry::getKey).sorted().limit(12).toList();
            errors.add(name + ": cycle involving or blocking " + String.join(", ", blocked));
        }
        return result;
    }

    private static void validateReferences(JsonElement value, DefinitionSet definitions, List<String> errors, String path, boolean effects, int depth) {
        if (depth > 64) { errors.add(path + ": JSON nesting exceeds 64 levels"); return; }
        if (value.isJsonArray()) {
            for (JsonElement entry : value.getAsJsonArray()) validateReferences(entry, definitions, errors, path, effects, depth + 1);
            return;
        }
        if (!value.isJsonObject()) return;
        JsonObject object = value.getAsJsonObject();
        if(effects)com.cappleapple.mastery.crafting.CraftingRules.validate(object,errors,path);
        try {
            if (object.has("ref")) {
                String ref = object.get("ref").getAsString();
                if (!(effects ? definitions.effects() : definitions.requirements()).containsKey(ref)) errors.add(path + ": missing " + (effects ? "effect" : "requirement") + " reference " + ref);
            }
            String type = object.has("type") ? object.get("type").getAsString() : "";
            String localType = type.contains(":") ? type.substring(type.indexOf(':') + 1) : type;
            if (!effects && Set.of("tree_level", "level", "proficiency").contains(localType) && object.has("tree")) {
                String id = object.get("tree").getAsString();
                TreeDefinition tree = definitions.trees().get(id);
                if (tree == null) errors.add(path + ": missing required tree " + id);
                else if (object.has("level") && (object.get("level").getAsInt() < 0 || object.get("level").getAsInt() > tree.maxLevel())) errors.add(path + ": impossible level for " + id);
            }
            if (!effects && Set.of("node_rank", "node").contains(localType) && object.has("node")) {
                String id = object.get("node").getAsString();
                NodeDefinition node = definitions.nodes().get(id);
                if (node == null) errors.add(path + ": missing required node " + id);
                else if (object.has("rank") && (object.get("rank").getAsInt() < 1 || object.get("rank").getAsInt() > node.maxRank())) errors.add(path + ": impossible rank for " + id);
            }
            if (effects && localType.equals("unlock_spell") && object.has("spell") && !definitions.spells().containsKey(object.get("spell").getAsString()))
                errors.add(path + ": missing unlocked spell " + object.get("spell").getAsString());
        } catch (RuntimeException ex) { errors.add(path + ": malformed reference: " + ex.getMessage()); }
        for (var entry : object.entrySet()) if (!entry.getKey().equals("parameters")) validateReferences(entry.getValue(), definitions, errors, path, effects, depth + 1);
    }

    private static void validateNamedReferences(Map<String, JsonObject> named, String kind, List<String> errors) {
        Map<String, Set<String>> references = new LinkedHashMap<>();
        named.forEach((id, value) -> {
            Set<String> found = new HashSet<>();
            collectRefs(value, found, 0);
            references.put(id, found);
        });
        depths(references, kind + " references", errors);
    }

    private static void collectRefs(JsonElement value, Set<String> refs, int depth) {
        if (depth > 64) return;
        if (value.isJsonArray()) for (JsonElement child : value.getAsJsonArray()) collectRefs(child, refs, depth + 1);
        if (value.isJsonObject()) for (var entry : value.getAsJsonObject().entrySet()) {
            if (entry.getKey().equals("ref") && entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isString()) refs.add(entry.getValue().getAsString());
            else collectRefs(entry.getValue(), refs, depth + 1);
        }
    }

    private static void collectTrees(JsonElement value, DefinitionSet definitions, Set<String> result, Set<String> seen) {
        if (value.isJsonArray()) for (JsonElement child : value.getAsJsonArray()) collectTrees(child, definitions, result, seen);
        if (!value.isJsonObject()) return;
        for (var entry : value.getAsJsonObject().entrySet()) {
            if (entry.getKey().equals("tree") && entry.getValue().isJsonPrimitive()) result.add(entry.getValue().getAsString());
            else if (entry.getKey().equals("node") && entry.getValue().isJsonPrimitive()) {
                NodeDefinition node = definitions.nodes().get(entry.getValue().getAsString());
                if (node != null) result.add(node.tree());
            } else if (entry.getKey().equals("ref") && entry.getValue().isJsonPrimitive()) {
                String id = entry.getValue().getAsString();
                if (seen.add(id) && definitions.requirements().containsKey(id)) collectTrees(definitions.requirements().get(id), definitions, result, seen);
            } else collectTrees(entry.getValue(), definitions, result, seen);
        }
    }
}
