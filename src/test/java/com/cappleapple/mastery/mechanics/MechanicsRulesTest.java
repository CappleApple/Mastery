package com.cappleapple.mastery.mechanics;

import com.cappleapple.mastery.data.DefinitionLoader;
import com.cappleapple.mastery.data.DefinitionSet;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MechanicsRulesTest {
    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }

    @Test void chanceUsesPercentagePointsAndClamps() {
        assertEquals(.45, MechanicsRules.chance(.25, .20), .000001);
        assertEquals(1, MechanicsRules.chance(.9, .4));
        assertEquals(0, MechanicsRules.chance(.2, -.3));
        assertEquals(0, MechanicsRules.chance(Double.NaN, .2));
    }

    @Test void healthBoundsAreInclusiveAndUnitsDiffer() {
        var fraction = json("{\"unit\":\"fraction\",\"max\":0.25}");
        assertTrue(MechanicsRules.health(5, 20, fraction));
        assertFalse(MechanicsRules.health(5.01, 20, fraction));
        var points = json("{\"unit\":\"points\",\"min\":5,\"max\":10}");
        assertTrue(MechanicsRules.health(5, 100, points));
        assertTrue(MechanicsRules.health(10, 100, points));
        assertFalse(MechanicsRules.health(4, 100, points));
    }

    @Test void keywordThresholdRequiresACrossingAndStackAdditionCannotOverflow() {
        assertTrue(MechanicsRules.crossed(4, 5, 5));
        assertFalse(MechanicsRules.crossed(5, 6, 5));
        assertFalse(MechanicsRules.crossed(0, 1, 0));
        assertEquals(1024, MechanicsRules.stacks(1020, Integer.MAX_VALUE, 1024));
        assertEquals(0, MechanicsRules.stacks(5, -10, 10));
    }

    @Test void definitionSnapshotRoundTripKeepsInteractions() {
        var loaded = DefinitionLoader.load(Map.of(
                "keywords", Map.of("test:scorch", json("{\"max_stacks\":5,\"threshold\":5,\"threshold_actions\":[{\"type\":\"remove_keyword\",\"keyword\":\"test:scorch\"}]}")),
                "triggers", Map.of("test:ignite", json("{\"event\":\"hit\",\"actions\":[{\"type\":\"keyword\",\"keyword\":\"test:scorch\"}]}"))));
        assertTrue(loaded.valid(), loaded.errors().toString());
        var copy = DefinitionSet.fromJson(loaded.definitions().toJson());
        assertEquals(loaded.definitions().triggers(), copy.triggers());
        assertEquals(loaded.definitions().keywords(), copy.keywords());
    }

    @Test void impossibleThresholdsAndDanglingReferencesAreRejected() {
        var loaded = DefinitionLoader.load(Map.of(
                "keywords", Map.of("test:scorch", json("{\"max_stacks\":3,\"threshold\":4}")),
                "triggers", Map.of("test:ignite", json("{\"event\":\"hit\",\"actions\":[{\"type\":\"keyword\",\"keyword\":\"test:missing\"}]}"))));
        assertFalse(loaded.valid());
        assertTrue(loaded.errors().stream().anyMatch(e -> e.contains("threshold exceeds")));
        assertTrue(loaded.errors().stream().anyMatch(e -> e.contains("missing keyword")));
    }

    @Test void malformedNumbersSelectorsAndFractionBoundsRejectTheWholeCandidate() {
        var loaded = DefinitionLoader.load(Map.of("triggers", Map.of("test:bad", json("""
                {"event":"hit", "chance":2, "cooldown":0.5,
                 "conditions":[{"type":"health","target":"self","unit":"fraction","max":25}],
                 "actions":[{"type":"heal","target":"everywhere","amount":"lots"}]}
                """))));
        assertFalse(loaded.valid());
        for (String expected : List.of("chance", "cooldown", "max", "target", "amount"))
            assertTrue(loaded.errors().stream().anyMatch(e -> e.contains(expected)), expected + " not diagnosed: " + loaded.errors());
    }

    @Test void triggerGrantMustReferenceAnExistingDefinition() {
        var loaded = DefinitionLoader.load(Map.of("effects", Map.of("test:grant", json("{\"type\":\"mastery:trigger\",\"trigger\":\"test:missing\"}"))));
        assertFalse(loaded.valid());
        assertTrue(loaded.errors().stream().anyMatch(e -> e.contains("missing trigger")));
    }
}
