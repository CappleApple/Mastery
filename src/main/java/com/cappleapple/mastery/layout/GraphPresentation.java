package com.cappleapple.mastery.layout;

import com.cappleapple.mastery.data.*;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.*;

/** Resolves bridge references and presentation visibility without client or server objects. */
public final class GraphPresentation {
    private GraphPresentation() {}

    public static boolean visible(String rule, int rank, long firstUnlockOrder) {
        return switch (rule) {
            case "invested", "hidden" -> rank > 0;
            case "available" -> rank > 0 || firstUnlockOrder > 0;
            default -> true;
        };
    }

    /** Hidden prerequisite endpoints are replaced by their nearest visible ancestors, with duplicates removed. */
    public static List<GraphLayout.Edge> visibleEdges(List<GraphLayout.Edge> edges,Map<String,GraphLayout.Entry> entries,Set<String> visible) {
        Set<GraphLayout.Edge> result=new LinkedHashSet<>();
        for(var edge:edges) {
            if(!visible.contains(edge.to()))continue;
            for(String endpoint:visibleAncestors(edge.from(),entries,visible))if(!endpoint.equals(edge.to()))
                result.add(new GraphLayout.Edge(endpoint,edge.to(),edge.synergy()));
        }
        return List.copyOf(result);
    }
    /** Keep outgoing connections until both animated endpoints finish leaving the graph. */
    public static List<GraphLayout.Edge> transitionEdges(List<GraphLayout.Edge> previous,List<GraphLayout.Edge> next,Set<String> visible,Set<String> animated) {
        var result=new LinkedHashSet<>(next);
        for(var edge:previous)if((!visible.contains(edge.from())||!visible.contains(edge.to()))
                &&animated.contains(edge.from())&&animated.contains(edge.to()))result.add(edge);
        return List.copyOf(result);
    }
    public static Set<String> visibleAncestors(String id,Map<String,GraphLayout.Entry> entries,Set<String> visible) {
        Set<String> result=new TreeSet<>(),seen=new HashSet<>();ArrayDeque<String> pending=new ArrayDeque<>();pending.add(id);
        while(!pending.isEmpty()) {
            String next=pending.remove();if(!seen.add(next))continue;
            if(visible.contains(next)){result.add(next);continue;}
            var entry=entries.get(next);if(entry==null)continue;
            if(entry.organizational()){if(!entry.parent().isBlank())pending.add(entry.parent());}
            else if(entry.dependencies().isEmpty())pending.add(entry.tree());
            else pending.addAll(entry.dependencies());
        }
        return result;
    }
    public static Set<String> expandedNodes(Map<String,GraphLayout.Entry> entries,Set<String> expanded) {
        Set<String> result=new LinkedHashSet<>();
        entries.values().stream().filter(e->e.organizational()&&e.parent().isBlank()||e.kind()==GraphLayout.Kind.SYNERGY).forEach(e->result.add(e.id()));
        boolean changed;
        do {
            changed=false;
            for(var entry:entries.values())if(!result.contains(entry.id())) {
                var parents=entry.organizational()?List.of(entry.parent()):entry.dependencies().isEmpty()?List.of(entry.tree()):entry.dependencies();
                if(parents.stream().anyMatch(p->result.contains(p)&&expanded.contains(p))){result.add(entry.id());changed=true;}
            }
        }while(changed);
        return result;
    }

    public static Set<String> relatedTrees(DefinitionSet definitions, NodeDefinition node) {
        Set<String> trees = new TreeSet<>();
        trees.add(node.tree());
        for (Dependency dependency : node.dependencyLeaves()) {
            NodeDefinition parent = definitions.nodes().get(dependency.node());
            if (parent != null) trees.add(parent.tree());
        }
        node.requirements().forEach(r -> collect(definitions, r, trees, new HashSet<>(), 0));
        return Set.copyOf(trees);
    }
    private static void collect(DefinitionSet definitions, JsonElement value, Set<String> trees, Set<String> references, int depth) {
        if (depth > 32) return;
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            if (object.has("tree") && object.get("tree").isJsonPrimitive()) trees.add(object.get("tree").getAsString());
            if (object.has("node") && object.get("node").isJsonPrimitive()) {
                NodeDefinition related = definitions.nodes().get(object.get("node").getAsString());
                if (related != null) trees.add(related.tree());
            }
            if (object.has("ref") && object.get("ref").isJsonPrimitive()) {
                String id = object.get("ref").getAsString();
                JsonObject target = definitions.requirements().get(id);
                if (target != null && references.add(id)) collect(definitions, target, trees, references, depth + 1);
            }
            object.entrySet().forEach(e -> collect(definitions, e.getValue(), trees, references, depth + 1));
        } else if (value.isJsonArray()) value.getAsJsonArray().forEach(e -> collect(definitions, e, trees, references, depth + 1));
    }
}
