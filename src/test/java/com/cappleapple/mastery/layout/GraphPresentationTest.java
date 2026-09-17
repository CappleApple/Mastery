package com.cappleapple.mastery.layout;

import com.cappleapple.mastery.data.*;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GraphPresentationTest {
    @Test void collapsedSynergyEdgesUseNearestVisibleAncestorsRecursively() {
        var entries=new java.util.HashMap<String,GraphLayout.Entry>();
        for(String tree:java.util.List.of("fire","sword"))entries.put(tree,new GraphLayout.Entry(tree,"",tree,GraphLayout.Kind.TREE,java.util.List.of(),java.util.Set.of(),0,0));
        entries.put("fire_practice",new GraphLayout.Entry("fire_practice","","fire",GraphLayout.Kind.NODE,java.util.List.of(),java.util.Set.of(),1,1));
        entries.put("deep",new GraphLayout.Entry("deep","","fire",GraphLayout.Kind.NODE,java.util.List.of("fire_practice"),java.util.Set.of(),1,1));
        entries.put("sword_practice",new GraphLayout.Entry("sword_practice","","sword",GraphLayout.Kind.NODE,java.util.List.of(),java.util.Set.of(),1,1));
        entries.put("bridge",new GraphLayout.Entry("bridge","","fire",GraphLayout.Kind.SYNERGY,java.util.List.of("deep","sword_practice"),java.util.Set.of("fire","sword"),1,1));
        var graph=GraphLayout.arrange(entries.values(),java.util.Map.of(),java.util.Set.of());
        var visible=GraphPresentation.expandedNodes(entries,java.util.Set.of());
        assertTrue(visible.contains("bridge"));assertFalse(visible.contains("deep"));
        var edges=GraphPresentation.visibleEdges(graph.edges(),entries,visible);
        assertEquals(java.util.Set.of(new GraphLayout.Edge("fire","bridge",true),new GraphLayout.Edge("sword","bridge",true)),java.util.Set.copyOf(edges));
        var all=GraphPresentation.visibleEdges(graph.edges(),entries,entries.keySet());
        assertFalse(all.contains(new GraphLayout.Edge("fire","bridge",true)));
        assertTrue(all.contains(new GraphLayout.Edge("deep","bridge",true)));
        assertEquals(java.util.Set.of("fire_practice"),GraphPresentation.visibleAncestors("deep",entries,java.util.Set.of("fire_practice","fire")));
    }
    @Test void investedAndEligibilityVisibilityUseAuthoritativeHistory() {
        assertFalse(GraphPresentation.visible("invested", 0, 7));
        assertTrue(GraphPresentation.visible("invested", 1, 7));
        assertFalse(GraphPresentation.visible("available", 0, 0));
        assertTrue(GraphPresentation.visible("available", 0, 5));
        assertTrue(GraphPresentation.visible("discovered", 0, 0));
    }
    @Test void reusableAndNodeRankRequirementsRevealBothBridgeOwners() {
        var reference = JsonParser.parseString("{\"ref\":\"test:bridge_requirements\"}").getAsJsonObject();
        var shared = JsonParser.parseString("{\"and\":[{\"type\":\"mastery:node_rank\",\"node\":\"test:foreign\",\"rank\":1}]}").getAsJsonObject();
        NodeDefinition bridge = node("test:bridge", "test:melee", List.of(reference));
        NodeDefinition foreign = node("test:foreign", "test:fire", List.of());
        var definitions = new DefinitionSet(Map.of(), Map.of(), Map.of(bridge.id(), bridge, foreign.id(), foreign),
                Map.of(), Map.of(), Map.of(), Map.of("test:bridge_requirements", shared), Map.of());
        assertEquals(Set.of("test:melee", "test:fire"), GraphPresentation.relatedTrees(definitions, bridge));
    }
    private NodeDefinition node(String id, String tree, List<com.google.gson.JsonObject> requirements) {
        return new NodeDefinition(id, tree, id, "", "", NodeType.SYNERGY, List.of(), 1, 1, 0, 0,
                requirements, List.of(), "discovered", List.of(), false, "", "");
    }
}
