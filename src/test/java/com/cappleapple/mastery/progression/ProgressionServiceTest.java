package com.cappleapple.mastery.progression;

import com.cappleapple.mastery.data.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProgressionServiceTest {
    private static TreeDefinition tree(String id) {
        return new TreeDefinition(id, id, "", "minecraft:book", "", 100, 10, 0, 5, Map.of(), "", List.of());
    }
    private static NodeDefinition node(String id, String tree, List<Dependency> dependencies) {
        return new NodeDefinition(id, tree, id, "", "minecraft:book", NodeType.PASSIVE,
                dependencies, 3, 1, 0, 0, List.of(), List.of(), "discovered", List.of(), false, "", "");
    }
    private static DefinitionSet definitions(Map<String, TreeDefinition> trees, Map<String, NodeDefinition> nodes) {
        return new DefinitionSet(Map.of(), trees, nodes, Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }

    @Test void disabledInvestmentStillUnlocksChildrenAndRankPurchasesPreserveTheSwitch() {
        var defs=definitions(Map.of("test:fire",tree("test:fire")),Map.of(
                "test:a",node("test:a","test:fire",List.of()),
                "test:b",node("test:b","test:fire",List.of(new Dependency("test:a",2)))));
        var player=new PlayerProgress();ProgressionService.grantPoints(defs,player,"test:fire",10);
        assertTrue(ProgressionService.purchase(defs,player,"test:a",-1,j->true).success());
        assertTrue(player.node("test:a").toggled(),"First purchase starts enabled");
        player.node("test:a").toggled(false);
        assertTrue(ProgressionService.purchase(defs,player,"test:a",-1,j->true).success());
        assertFalse(player.node("test:a").toggled());
        assertTrue(ProgressionService.purchase(defs,player,"test:b",-1,j->true).success());
        ProgressionService.reconcile(defs,player);
        assertFalse(PlayerProgress.fromJson(player.toJson()).node("test:a").toggled());
        assertEquals(1,player.rank("test:b"));
    }

    @Test void administrativeFirstRankEnablesWithoutResettingExistingChoice() {
        var defs=definitions(Map.of("test:fire",tree("test:fire")),Map.of("test:a",node("test:a","test:fire",List.of())));
        var player=new PlayerProgress();player.node("test:a").toggled(false);
        assertTrue(ProgressionService.setRank(defs,player,"test:a",1,-1).success());assertTrue(player.node("test:a").toggled());
        player.node("test:a").toggled(false);ProgressionService.setRank(defs,player,"test:a",2,-1);assertFalse(player.node("test:a").toggled());
    }

    @Test void proficiencyAndPointsStayIndependent() {
        DefinitionSet definitions = definitions(Map.of("test:fire", tree("test:fire"), "test:bow", tree("test:bow")), Map.of());
        PlayerProgress player = new PlayerProgress();
        var result = ProgressionService.addXp(definitions, player, "test:fire", 57, -1);
        assertTrue(result.success());
        assertEquals(5, player.tree("test:fire").level());
        assertEquals(7, player.tree("test:fire").xp());
        assertEquals(1, player.tree("test:fire").points());
        assertEquals(0, player.tree("test:bow").points());
        assertEquals(0, player.tree("test:bow").level());
    }

    @Test void xpAloneDoesNotRevealTreeBeforeItsFirstPoint() {
        DefinitionSet definitions = definitions(Map.of("test:fire", tree("test:fire")), Map.of());
        PlayerProgress player = new PlayerProgress();
        ProgressionService.addXp(definitions, player, "test:fire", 49, -1);
        assertFalse(player.tree("test:fire").discovered());
        ProgressionService.addXp(definitions, player, "test:fire", 1, -1);
        assertTrue(player.tree("test:fire").discovered());
    }

    @Test void classPointGrantRevealsTreePermanentlyAfterSpending() {
        DefinitionSet definitions = definitions(Map.of("test:fire", tree("test:fire")),
                Map.of("test:a", node("test:a", "test:fire", List.of())));
        PlayerProgress player = new PlayerProgress();
        assertTrue(ProgressionService.grantPoints(definitions, player, "test:fire", 1).success());
        assertTrue(player.tree("test:fire").discovered());
        assertTrue(ProgressionService.purchase(definitions, player, "test:a", -1, value -> true).success());
        assertEquals(0, player.tree("test:fire").points());
        assertTrue(PlayerProgress.fromJson(player.toJson()).tree("test:fire").discovered());
    }

    @Test void milestoneFormulaAndPeriodicPointsCombine() {
        TreeDefinition tree = new TreeDefinition("test:fire", "Fire", "", "", "", 100, 10, 0, 5,
                Map.of(3, 2, 8, 4), "floor(level / 4)", List.of());
        DefinitionSet definitions = definitions(Map.of(tree.id(), tree), Map.of());
        PlayerProgress player = new PlayerProgress();
        assertTrue(ProgressionService.addXp(definitions, player, tree.id(), 100, -1).success());
        assertEquals(10, player.tree(tree.id()).points()); // 2 periodic + 6 milestones + 2 formula.
    }

    @Test void decreasingThenRestoringLevelCannotFarmPoints() {
        DefinitionSet definitions = definitions(Map.of("test:fire", tree("test:fire")), Map.of());
        PlayerProgress player = new PlayerProgress();
        ProgressionService.setLevel(definitions, player, "test:fire", 10, -1);
        ProgressionService.setLevel(definitions, player, "test:fire", 0, -1);
        ProgressionService.setLevel(definitions, player, "test:fire", 10, -1);
        assertEquals(2, player.tree("test:fire").points());
        assertEquals(10, player.tree("test:fire").highestLevel());
    }

    @Test void worldTierCapsDiscardOverflowWithoutDestroyingEarnedProgress() {
        TreeDefinition tree = new TreeDefinition("test:fire", "Fire", "", "", "", 100, 10, 0, 5, Map.of(), "",
                List.of(new TierCap(0, 5, 1, 0, 1, 2), new TierCap(1, 10, 2, 1, 2, 4)));
        DefinitionSet definitions = definitions(Map.of(tree.id(), tree), Map.of());
        PlayerProgress player = new PlayerProgress();
        ProgressionService.addXp(definitions, player, tree.id(), 1000, 0);
        assertEquals(5, player.tree(tree.id()).level());
        assertEquals(0, player.tree(tree.id()).xp());
        ProgressionService.addXp(definitions, player, tree.id(), 10, 1);
        assertEquals(6, player.tree(tree.id()).level());
        ProgressionService.addXp(definitions, player, tree.id(), 100, 0);
        assertEquals(6, player.tree(tree.id()).level());
        assertEquals(1110, player.tree(tree.id()).lifetimeXp());
    }

    @Test void pointOverflowRejectsWholeXpTransaction() {
        DefinitionSet definitions = definitions(Map.of("test:fire", tree("test:fire")), Map.of());
        PlayerProgress player = new PlayerProgress();
        player.tree("test:fire").points(Integer.MAX_VALUE);
        JsonObject before = player.toJson();
        assertFalse(ProgressionService.addXp(definitions, player, "test:fire", 50, -1).success());
        assertEquals(before, player.toJson());
    }

    @Test void invalidXpCannotModifyOrCreateState() {
        DefinitionSet definitions = definitions(Map.of("test:fire", tree("test:fire")), Map.of());
        PlayerProgress player = new PlayerProgress();
        for (double value : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertFalse(ProgressionService.addXp(definitions, player, "test:fire", value, -1).success());
            assertTrue(player.trees().isEmpty());
        }
    }

    @Test void multiParentCrossTreePurchasesRequireAllParents() {
        DefinitionSet definitions = definitions(Map.of("test:fire", tree("test:fire"), "test:bow", tree("test:bow")), Map.of(
                "test:f", node("test:f", "test:fire", List.of()),
                "test:b", node("test:b", "test:bow", List.of()),
                "test:s", node("test:s", "test:fire", List.of(new Dependency("test:f", 2), new Dependency("test:b", 1)))));
        PlayerProgress player = new PlayerProgress();
        ProgressionService.grantPoints(definitions, player, "test:fire", 5);
        ProgressionService.grantPoints(definitions, player, "test:bow", 1);
        ProgressionService.purchase(definitions, player, "test:f", -1, value -> true);
        ProgressionService.purchase(definitions, player, "test:b", -1, value -> true);
        assertFalse(ProgressionService.purchase(definitions, player, "test:s", -1, value -> true).success());
        ProgressionService.purchase(definitions, player, "test:f", -1, value -> true);
        assertTrue(ProgressionService.purchase(definitions, player, "test:s", -1, value -> true).success());
        assertEquals(2, player.tree("test:fire").points());
        assertEquals(0, player.tree("test:bow").points());
    }

    @Test void rejectedRequirementLeavesPointsAndChronologyUntouched() {
        JsonObject requirement = JsonParser.parseString("{\"type\":\"test:custom\"}").getAsJsonObject();
        NodeDefinition node = new NodeDefinition("test:a", "test:fire", "A", "", "", NodeType.PASSIVE,
                List.of(), 1, 2, 0, 0, List.of(requirement), List.of(), "always", List.of(), false, "", "");
        DefinitionSet definitions = definitions(Map.of("test:fire", tree("test:fire")), Map.of(node.id(), node));
        PlayerProgress player = new PlayerProgress();
        ProgressionService.grantPoints(definitions, player, "test:fire", 3);
        JsonObject before = player.toJson();
        assertFalse(ProgressionService.purchase(definitions, player, node.id(), -1, value -> false).success());
        assertEquals(before, player.toJson());
    }

    @Test void exclusionsApplyInBothDirections() {
        NodeDefinition excluding = new NodeDefinition("test:a", "test:fire", "A", "", "", NodeType.PASSIVE,
                List.of(), 1, 1, 0, 0, List.of(), List.of(), "always", List.of("test:b"), false, "", "");
        DefinitionSet definitions = definitions(Map.of("test:fire", tree("test:fire")), Map.of(
                "test:a", excluding, "test:b", node("test:b", "test:fire", List.of())));
        PlayerProgress player = new PlayerProgress();
        ProgressionService.grantPoints(definitions, player, "test:fire", 3);
        assertTrue(ProgressionService.purchase(definitions, player, "test:a", -1, value -> true).success());
        assertFalse(ProgressionService.purchase(definitions, player, "test:b", -1, value -> true).success());
    }

    @Test void rankAndDepthCapsAreIndependent() {
        TreeDefinition tree = new TreeDefinition("test:fire", "Fire", "", "", "", 100, 10, 0, 5, Map.of(), "",
                List.of(new TierCap(0, 100, 1, 0, 1, 2), new TierCap(1, 100, 2, 1, 2, 4)));
        DefinitionSet definitions = definitions(Map.of(tree.id(), tree), Map.of("test:a", node("test:a", tree.id(), List.of()),
                "test:b", node("test:b", tree.id(), List.of(new Dependency("test:a", 1)))));
        PlayerProgress player = new PlayerProgress();
        ProgressionService.grantPoints(definitions, player, tree.id(), 10);
        assertTrue(ProgressionService.purchase(definitions, player, "test:a", 0, value -> true).success());
        assertFalse(ProgressionService.purchase(definitions, player, "test:a", 0, value -> true).success());
        assertFalse(ProgressionService.purchase(definitions, player, "test:b", 0, value -> true).success());
        assertTrue(ProgressionService.purchase(definitions, player, "test:b", 1, value -> true).success());
        assertTrue(ProgressionService.purchase(definitions, player, "test:a", 1, value -> true).success());
    }

    @Test void purchaseChronologySurvivesSavingAndRankUpgrades() {
        DefinitionSet definitions = definitions(Map.of("test:fire", tree("test:fire")), Map.of(
                "test:a", node("test:a", "test:fire", List.of()), "test:b", node("test:b", "test:fire", List.of())));
        PlayerProgress player = new PlayerProgress();
        ProgressionService.grantPoints(definitions, player, "test:fire", 4);
        ProgressionService.purchase(definitions, player, "test:b", -1, value -> true);
        long first = player.node("test:b").purchaseOrder();
        ProgressionService.purchase(definitions, player, "test:a", -1, value -> true);
        player = PlayerProgress.fromJson(player.toJson());
        ProgressionService.purchase(definitions, player, "test:b", -1, value -> true);
        assertEquals(first, player.node("test:b").purchaseOrder());
        assertTrue(first < player.node("test:a").purchaseOrder());
        assertTrue(player.nextSequence() > player.node("test:a").purchaseOrder());
    }

    @Test void saveLoadPreservesAllPersistentMechanics() {
        PlayerProgress player = new PlayerProgress();
        player.tree("test:fire").xp(17.5);
        player.tree("test:fire").level(8);
        player.tree("test:fire").points(2);
        player.tree("test:fire").lifetimeXp(900);
        player.tree("test:fire").highestLevel(9);
        player.node("test:a").rank(2);
        player.node("test:a").purchaseOrder(player.nextSequence());
        player.node("test:a").unlockOrder(player.nextSequence());
        player.node("test:a").toggled(false);
        player.loadout("test:wand").set(2, "test:fireball");
        player.modifiers("test:fireball").add("test:charged");
        player.usageGrants().add("test:first_fire");
        assertEquals(player.toJson(), PlayerProgress.fromJson(player.toJson()).toJson());
        PlayerProgress copied = player.copy();
        copied.loadout("test:wand").set(2, "");
        assertEquals("test:fireball", player.loadout("test:wand").get(2));
    }

    @Test void explicitResetHidesTheTreeAndClearsPurchasedNodes() {
        DefinitionSet definitions = definitions(Map.of("test:fire", tree("test:fire")),
                Map.of("test:a", node("test:a", "test:fire", List.of())));
        PlayerProgress player = new PlayerProgress();
        ProgressionService.grantPoints(definitions, player, "test:fire", 1);
        ProgressionService.purchase(definitions, player, "test:a", -1, value -> true);
        assertTrue(ProgressionService.resetTree(definitions, player, "test:fire").success());
        assertFalse(player.tree("test:fire").discovered());
        assertEquals(0, player.rank("test:a"));
    }

    @Test void legacyPurchasedNodeMigratesDiscovery() {
        DefinitionSet definitions = definitions(Map.of("test:fire", tree("test:fire")),
                Map.of("test:a", node("test:a", "test:fire", List.of())));
        PlayerProgress player = PlayerProgress.fromJson(JsonParser.parseString(
                "{\"trees\":{\"test:fire\":{\"level\":5,\"points\":0}},\"nodes\":{\"test:a\":{\"rank\":1}}}").getAsJsonObject());
        ProgressionService.reconcile(definitions, player);
        assertTrue(player.tree("test:fire").discovered());
        assertEquals(5, player.tree("test:fire").highestLevel());
    }

    @Test void formulaLanguageIsBoundedAndRejectsInvalidResults() {
        assertEquals(4, PointFormula.total("max(0, floor(level / 3)) + min(2, level % 2)", 9));
        assertEquals(4, PointFormula.total("floor(pow(level, 0.5))", 16));
        assertThrows(IllegalArgumentException.class, () -> PointFormula.evaluate("java.lang.Runtime()", 10));
        assertThrows(IllegalArgumentException.class, () -> PointFormula.evaluate("1 / 0", 10));
        assertThrows(IllegalArgumentException.class, () -> PointFormula.evaluate("(".repeat(80) + "1" + ")".repeat(80), 1));
    }
}
