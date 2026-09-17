package com.cappleapple.mastery.progression;

import com.cappleapple.mastery.data.*;
import com.cappleapple.mastery.layout.GraphPresentation;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RepeatableNodesTest {
    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
    private static DefinitionSet definitions() {
        var result = DefinitionLoader.load(Map.of("trees", Map.of("test:tree", json("{}")), "nodes", Map.of(
                "test:practice", json("{\"tree\":\"test:tree\",\"max_rank\":10,\"cost\":2}"),
                "test:branch", json("{\"tree\":\"test:tree\",\"max_rank\":4,\"dependencies\":[{\"node\":\"test:practice\",\"rank\":5}]}"),
                "test:secret", json("{\"tree\":\"test:tree\",\"dependencies\":[{\"node\":\"test:branch\",\"rank\":3}]}"))));
        assertTrue(result.valid(), result.errors().toString());
        return result.definitions();
    }
    private static boolean visible(DefinitionSet definitions, PlayerProgress player, String id) {
        var state = player.nodes().get(id);
        return GraphPresentation.visible(definitions.nodes().get(id).visibility(), player.rank(id), state == null ? 0 : state.unlockOrder());
    }
    @Test void repeatedInvestmentRevealsOnlyTheEligibleNextBranch() {
        var definitions = definitions();
        var player = new PlayerProgress();
        ProgressionService.grantPoints(definitions, player, "test:tree", 100);
        ProgressionService.refreshUnlocks(definitions, player, -1, ignored -> true);
        assertTrue(visible(definitions, player, "test:practice"));
        assertFalse(visible(definitions, player, "test:branch"));
        assertFalse(visible(definitions, player, "test:secret"));
        for (int rank = 1; rank <= 4; rank++) {
            assertTrue(ProgressionService.purchase(definitions, player, "test:practice", -1, ignored -> true).success());
            ProgressionService.refreshUnlocks(definitions, player, -1, ignored -> true);
            assertFalse(visible(definitions, player, "test:branch"));
            assertFalse(ProgressionService.purchase(definitions, player, "test:branch", -1, ignored -> true).success());
        }
        player = PlayerProgress.fromJson(player.toJson());
        assertTrue(ProgressionService.purchase(definitions, player, "test:practice", -1, ignored -> true).success());
        ProgressionService.refreshUnlocks(definitions, player, -1, ignored -> true);
        assertTrue(visible(definitions, player, "test:branch"));
        assertFalse(visible(definitions, player, "test:secret"));
        for (int rank = 1; rank <= 3; rank++) {
            assertTrue(ProgressionService.purchase(definitions, player, "test:branch", -1, ignored -> true).success());
            ProgressionService.refreshUnlocks(definitions, player, -1, ignored -> true);
            assertEquals(rank == 3, visible(definitions, player, "test:secret"));
        }
        assertEquals(87, player.tree("test:tree").points());
        for (int rank = 6; rank <= 10; rank++)
            assertTrue(ProgressionService.purchase(definitions, player, "test:practice", -1, ignored -> true).success());
        var saved = player.toJson();
        assertFalse(ProgressionService.purchase(definitions, player, "test:practice", -1, ignored -> true).success());
        assertEquals(saved, player.toJson(), "Exceeding the repeat limit must not spend points or mutate history");
    }
    @Test void revealingASecretDoesNotRequireUnusedPoints() {
        var definitions = definitions();
        var player = new PlayerProgress();
        ProgressionService.grantPoints(definitions, player, "test:tree", 10);
        for (int rank = 0; rank < 5; rank++) ProgressionService.purchase(definitions, player, "test:practice", -1, ignored -> true);
        assertEquals(0, player.tree("test:tree").points());
        ProgressionService.refreshUnlocks(definitions, player, -1, ignored -> true);
        assertTrue(visible(definitions, player, "test:branch"));
        assertFalse(ProgressionService.purchase(definitions, player, "test:branch", -1, ignored -> true).success());
    }
    @Test void allUnlockConditionsStillGateFirstReveal() {
        var source = definitions().toJson();
        var branch = source.getAsJsonObject("nodes").getAsJsonObject("test:branch");
        branch.addProperty("level", 2);
        branch.addProperty("world_tier", 1);
        branch.addProperty("book_token", "test:book");
        branch.add("requirements", JsonParser.parseString("[{\"type\":\"test:external\"}]"));
        var definitions = DefinitionSet.fromJson(source);
        var player = new PlayerProgress();
        ProgressionService.grantPoints(definitions, player, "test:tree", 20);
        ProgressionService.setRank(definitions, player, "test:practice", 5, -1);
        ProgressionService.setLevel(definitions, player, "test:tree", 2, -1);
        ProgressionService.refreshUnlocks(definitions, player, 1, ignored -> true);
        assertFalse(visible(definitions, player, "test:branch"), "A matching book is required");
        player.bookUnlocks().add("test:book");
        ProgressionService.refreshUnlocks(definitions, player, 0, ignored -> true);
        assertFalse(visible(definitions, player, "test:branch"), "World-tier requirements still apply");
        ProgressionService.refreshUnlocks(definitions, player, 1, ignored -> false);
        assertFalse(visible(definitions, player, "test:branch"), "External requirements still apply");
        ProgressionService.refreshUnlocks(definitions, player, 1, ignored -> true);
        assertTrue(visible(definitions, player, "test:branch"));
    }
    @Test void authorRepeatLimitsUseThePositiveIntegerRange() {
        var result = DefinitionLoader.load(Map.of("trees", Map.of("test:tree", json("{\"points_per_award\":2}")), "nodes", Map.of(
                "test:repeat", json("{\"tree\":\"test:tree\",\"max_rank\":2147483647}"),
                "test:child", json("{\"tree\":\"test:tree\",\"dependencies\":[{\"node\":\"test:repeat\",\"rank\":2147483647}]}"))));
        assertTrue(result.valid(), result.errors().toString());
        assertEquals(Integer.MAX_VALUE, result.definitions().nodes().get("test:repeat").maxRank());
        assertEquals("available", result.definitions().nodes().get("test:child").visibility());
    }
}
