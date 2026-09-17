package com.cappleapple.mastery.graph;

import com.cappleapple.mastery.data.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GraphValidatorTest {
    private static TreeDefinition tree(String id) {
        return new TreeDefinition(id, id, "", "", "", 100, 100, 20, 5, Map.of(), "", List.of());
    }
    private static NodeDefinition node(String id, String tree, List<Dependency> dependencies) {
        return new NodeDefinition(id, tree, id, "", "", NodeType.PASSIVE, dependencies, 3, 1, 0, 0,
                List.of(), List.of(), "discovered", List.of(), false, "", "");
    }
    private static DefinitionSet definitions(Map<String, TreeDefinition> trees, Map<String, NodeDefinition> nodes) {
        return new DefinitionSet(Map.of(), trees, nodes, Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }

    @Test void missingTreesAndParentsProduceResourceSpecificErrors() {
        DefinitionSet definitions = definitions(Map.of(), Map.of("test:a", node("test:a", "test:gone", List.of(new Dependency("test:missing", 1)))));
        var result = GraphValidator.validate(definitions);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(value -> value.contains("nodes/test:a: missing tree test:gone")));
        assertTrue(result.errors().stream().anyMatch(value -> value.contains("missing dependency node test:missing")));
    }

    @Test void rejectsDependencyCyclesAndImpossibleRanks() {
        DefinitionSet definitions = definitions(Map.of("test:tree", tree("test:tree")), Map.of(
                "test:a", node("test:a", "test:tree", List.of(new Dependency("test:b", 4))),
                "test:b", node("test:b", "test:tree", List.of(new Dependency("test:a", 1)))));
        var result = GraphValidator.validate(definitions);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(value -> value.contains("impossible rank 4")));
        assertTrue(result.errors().stream().anyMatch(value -> value.contains("cycle")));
    }

    @Test void rejectsOrganizationalCycles() {
        DefinitionSet definitions = new DefinitionSet(Map.of(
                "test:a", new GroupDefinition("test:a", "A", "", "", "test:b"),
                "test:b", new GroupDefinition("test:b", "B", "", "", "test:a")),
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        assertTrue(GraphValidator.validate(definitions).errors().stream().anyMatch(value -> value.contains("organizational hierarchy: cycle")));
    }

    @Test void validMultiTreeSynergyAndDepthAreRecognized() {
        NodeDefinition synergy = new NodeDefinition("test:synergy", "test:fire", "S", "", "", NodeType.SYNERGY,
                List.of(new Dependency("test:a", 2), new Dependency("test:b", 1)), 1, 1, 0, 0,
                List.of(), List.of(), "discovered", List.of(), false, "", "");
        DefinitionSet definitions = definitions(Map.of("test:fire", tree("test:fire"), "test:bow", tree("test:bow")), Map.of(
                "test:a", node("test:a", "test:fire", List.of()),
                "test:b", node("test:b", "test:bow", List.of()),
                synergy.id(), synergy));
        var result = GraphValidator.validate(definitions);
        assertTrue(result.valid(), result.errors().toString());
        assertEquals(1, result.depths().get(synergy.id()));
    }

    @Test void namespacedRequirementsCheckCrossTreeBoundsAndMissingReferences() {
        JsonObject requirement = JsonParser.parseString("{\"type\":\"mastery:tree_level\",\"tree\":\"test:bow\",\"level\":101}").getAsJsonObject();
        JsonObject missingRef = JsonParser.parseString("{\"ref\":\"test:missing\"}").getAsJsonObject();
        NodeDefinition node = new NodeDefinition("test:a", "test:fire", "A", "", "", NodeType.PASSIVE,
                List.of(), 1, 1, 0, 0, List.of(requirement, missingRef), List.of(), "always", List.of(), false, "", "");
        DefinitionSet definitions = definitions(Map.of("test:fire", tree("test:fire"), "test:bow", tree("test:bow")), Map.of(node.id(), node));
        var result = GraphValidator.validate(definitions);
        assertTrue(result.errors().stream().anyMatch(value -> value.contains("impossible level for test:bow")));
        assertTrue(result.errors().stream().anyMatch(value -> value.contains("missing requirement reference test:missing")));
    }

    @Test void namedRequirementCyclesAreRejected() {
        JsonObject a = JsonParser.parseString("{\"ref\":\"test:b\"}").getAsJsonObject();
        JsonObject b = JsonParser.parseString("{\"ref\":\"test:a\"}").getAsJsonObject();
        DefinitionSet definitions = new DefinitionSet(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of("test:a", a, "test:b", b), Map.of());
        assertTrue(GraphValidator.validate(definitions).errors().stream().anyMatch(value -> value.contains("requirements references: cycle")));
    }

    @Test void dependencyTraversalHandlesThousandsOfNodesWithoutRecursion() {
        Map<String, NodeDefinition> nodes = new LinkedHashMap<>();
        for (int index = 0; index < 3000; index++) {
            String id = "test:node_" + index;
            nodes.put(id, node(id, "test:tree", index == 0 ? List.of() : List.of(new Dependency("test:node_" + (index - 1), 1))));
        }
        DefinitionSet definitions = definitions(Map.of("test:tree", tree("test:tree")), nodes);
        var result = GraphValidator.validate(definitions);
        assertTrue(result.valid(), result.errors().toString());
        assertEquals(2999, result.depths().get("test:node_2999"));
        assertEquals(2999, GraphValidator.depthOf(definitions, "test:node_2999"));
    }

    @Test void invalidFormulaAndDuplicateTierCapsFailReload() {
        TreeDefinition tree = new TreeDefinition("test:t", "T", "", "", "", 100, 10, 0, 0, Map.of(),
                "100 - level", List.of(new TierCap(0, 10, 1, 1, 1, 1), new TierCap(0, 20, 2, 2, 2, 2)));
        var result = GraphValidator.validate(definitions(Map.of(tree.id(), tree), Map.of()));
        assertTrue(result.errors().stream().anyMatch(value -> value.contains("point_formula")));
        assertTrue(result.errors().stream().anyMatch(value -> value.contains("unique and nonnegative")));
    }
}
