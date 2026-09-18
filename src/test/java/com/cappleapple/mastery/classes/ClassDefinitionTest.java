package com.cappleapple.mastery.classes;

import com.cappleapple.mastery.data.*;
import com.cappleapple.mastery.progression.PlayerProgress;
import com.google.gson.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClassDefinitionTest {
    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
    private static DefinitionSet definitions() {
        return DefinitionLoader.load(Map.of("trees", Map.of("test:tree", json("{}")), "nodes", Map.of(
                "test:parent", json("{\"tree\":\"test:tree\",\"max_rank\":3}"),
                "test:child", json("{\"tree\":\"test:tree\",\"level\":2,\"dependencies\":[{\"node\":\"test:parent\",\"rank\":2}]}")))).definitions();
    }
    @Test void strictSchemaRejectsFractionalRanksMalformedComponentsAndUnknownOperations() {
        for (String value : List.of("{\"starting_skills\":{\"test:node\":0.5}}", "{\"starting_points\":{\"test:tree\":-1}}",
                "{\"starting_inventory\":[{\"id\":\"minecraft:stone\",\"count\":0}]}" ,
                "{\"starting_inventory\":[{\"id\":\"minecraft:stone\",\"components\":[]}]}" ,
                "{\"attributes\":[{\"attribute\":\"minecraft:generic.attack_damage\",\"amount\":1,\"operation\":\"multiply\"}]}"))
            assertThrows(IllegalArgumentException.class, () -> ClassDefinition.parse("test:class", json(value)), value);
    }
    @Test void classSelectionAndReceiptSurviveSaveCopyAndProgressionReset() {
        var state = new PlayerProgress();
        state.selectedClass("test:mage"); state.classRewardsGranted(true);
        var anchor = new ClassSelectionState("adventure", "minecraft:overworld", 2, 75, -2, 15, -5);
        state.classSelection(anchor); state.tree("test:tree").points(3); state.pendingClassItems().add(json("{\"id\":\"minecraft:bread\",\"count\":5}"));
        var copy = state.copy(); assertEquals(state.toJson(), copy.toJson()); assertEquals(anchor, copy.classSelection());
        copy.clear(); assertEquals("test:mage", copy.selectedClass()); assertTrue(copy.classRewardsGranted());
        assertEquals(anchor, copy.classSelection()); assertTrue(copy.trees().isEmpty()); assertEquals(1, copy.pendingClassItems().size());
    }
    @Test void savedChosenClassImpliesReceiptAndInvalidAnchorsAreIgnored() {
        var saved = json("{\"selected_class\":\"test:mage\",\"class_selection_state\":{\"game_mode\":\"invalid\"}}");
        var state = PlayerProgress.fromJson(saved);
        assertTrue(state.classRewardsGranted()); assertNull(state.classSelection());
        assertThrows(IllegalArgumentException.class, () -> new ClassSelectionState("creative", "minecraft:overworld", Double.NaN, 0, 0, 0, 0));
    }
    @Test void starterRewardsPreserveExistingProgressAndDoNotAwardLevelMilestonePoints() {
        var definition = ClassDefinition.parse("test:mage", json("{\"starting_points\":{\"test:tree\":3},\"starting_skills\":{\"test:parent\":2,\"test:child\":1}}"));
        var original = new PlayerProgress(); original.tree("test:tree").points(4);
        var next = ClassRewards.prepare(definitions(), original, definition);
        assertEquals(4, original.tree("test:tree").points()); assertEquals(0, original.rank("test:parent"));
        assertEquals(7, next.tree("test:tree").points()); assertEquals(2, next.rank("test:parent"));
        assertEquals(1, next.rank("test:child")); assertEquals(2, next.tree("test:tree").level());
        assertEquals(2, next.tree("test:tree").highestLevel()); assertTrue(next.classRewardsGranted());
        assertThrows(IllegalArgumentException.class, () -> ClassRewards.prepare(definitions(), next, definition));
    }
    @Test void failedPreflightCannotDebitOrPartiallyGrantAnything() {
        var original = new PlayerProgress(); original.tree("test:tree").points(Integer.MAX_VALUE);
        var before = original.toJson();
        var overflow = ClassDefinition.parse("test:mage", json("{\"starting_points\":{\"test:tree\":1}}"));
        assertThrows(ArithmeticException.class, () -> ClassRewards.prepare(definitions(), original, overflow));
        var unmet = ClassDefinition.parse("test:mage", json("{\"starting_skills\":{\"test:child\":1}}"));
        assertThrows(IllegalArgumentException.class, () -> ClassRewards.prepare(definitions(), original, unmet));
        assertEquals(before, original.toJson());
    }
    @Test void reloadRejectsMissingReferencesAndUnmetStarterPrerequisites() {
        var json = definitions().toJson();
        json.getAsJsonObject("classes").add("test:mage", json("{\"starting_points\":{\"test:missing\":1},\"starting_skills\":{\"test:child\":1}}"));
        var failure = assertThrows(JsonParseException.class, () -> DefinitionSet.fromJson(json));
        assertTrue(failure.getMessage().contains("unknown starting_points tree"));
        assertTrue(failure.getMessage().contains("prerequisite"));
    }
}
