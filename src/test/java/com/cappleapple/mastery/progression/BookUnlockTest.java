package com.cappleapple.mastery.progression;

import com.cappleapple.mastery.data.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BookUnlockTest {
    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
    private static DefinitionSet definitions() {
        return DefinitionLoader.load(Map.of(
                "trees", Map.of("test:tree", json("{}")),
                "nodes", Map.of(
                        "test:branch", json("{\"tree\":\"test:tree\",\"book_token\":\"test:secrets\"}"),
                        "test:child", json("{\"tree\":\"test:tree\",\"dependencies\":[\"test:branch\"]}"))))
                .definitions();
    }

    @Test void bookGateAppliesToTheEntireDependencyBranchAndNeverGrantsRanksOrPoints() {
        var definitions = definitions();
        var player = new PlayerProgress();
        ProgressionService.grantPoints(definitions, player, "test:tree", 3);
        assertFalse(ProgressionService.bookUnlocked(definitions, player, "test:branch"));
        assertFalse(ProgressionService.bookUnlocked(definitions, player, "test:child"));
        assertFalse(ProgressionService.purchase(definitions, player, "test:branch", -1, ignored -> true).success());
        assertTrue(player.bookUnlocks().add("test:secrets"));
        assertFalse(player.bookUnlocks().add("test:secrets"), "The same book token is learned only once");
        assertTrue(ProgressionService.bookUnlocked(definitions, player, "test:child"));
        assertEquals(0, player.rank("test:branch"));
        assertEquals(3, player.tree("test:tree").points());
        assertTrue(ProgressionService.purchase(definitions, player, "test:branch", -1, ignored -> true).success());
        assertTrue(ProgressionService.purchase(definitions, player, "test:child", -1, ignored -> true).success());
    }

    @Test void bookKnowledgePersistsThroughCopiesReloadAndTreeResetButAllResetClearsIt() {
        var definitions = definitions();
        var player = new PlayerProgress();
        player.bookUnlocks().add("test:secrets");
        var copy = player.copy();
        ProgressionService.reconcile(DefinitionSet.EMPTY, copy);
        ProgressionService.resetTree(definitions, copy, "test:tree");
        assertTrue(copy.bookUnlocks().contains("test:secrets"));
        assertTrue(ProgressionService.bookUnlocked(definitions, copy, "test:child"));
        assertFalse(copy.tree("test:tree").discovered(), "Learning a book does not reveal an undiscovered tree");
        ProgressionService.resetAll(copy);
        assertTrue(copy.bookUnlocks().isEmpty());
    }
}
