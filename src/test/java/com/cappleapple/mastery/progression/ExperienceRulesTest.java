package com.cappleapple.mastery.progression;

import com.cappleapple.mastery.data.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ExperienceRulesTest {
    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
    @Test void globalTreeAndAdditiveRankBonusesComposeOnce() {
        assertEquals(51, ExperienceRules.scale(10, 2, 1.5, 0.7), 1e-9);
        assertEquals(10, ExperienceRules.scale(10, 1, 1, 0));
        assertEquals(12.5, ExperienceRules.scale(10, 1.25, 1, 0));
    }
    @Test void negativeAndExcessiveFactorsCannotCreateNegativeOrInfiniteXp() {
        assertEquals(0, ExperienceRules.scale(10, -2, 1, 0));
        assertEquals(0, ExperienceRules.scale(10, 1, 1, -1.5));
        assertEquals(0, ExperienceRules.scale(Double.MAX_VALUE, 0, 1, 0));
        assertEquals(ExperienceRules.MAX_AWARD, ExperienceRules.scale(Double.MAX_VALUE, 100, 100, 100));
        assertEquals(ExperienceRules.MAX_AWARD, ExperienceRules.scale(1e12, 2, 2, 0));
        assertEquals(10, ExperienceRules.scale(10, Double.NaN, Double.POSITIVE_INFINITY, Double.NaN));
    }
    @Test void invalidAwardsRemainInvalidForTransactionalRejection() {
        assertEquals(-1, ExperienceRules.scale(-1, 1, 1, 0));
        assertTrue(Double.isNaN(ExperienceRules.scale(Double.NaN, 1, 1, 0)));
        assertEquals(Double.POSITIVE_INFINITY, ExperienceRules.scale(Double.POSITIVE_INFINITY, 1, 1, 0));
    }
    @Test void declarativeEffectsMatchAnyOrAnExactTreeAndScaleWithRank() {
        assertEquals(.6, ExperienceRules.effectAmount(json("{\"type\":\"mastery:experience_gain\",\"amount\":0.2}"), "test:custom", 3), 1e-9);
        var effect = json("{\"type\":\"mastery:experience_gain\",\"amount\":0.25,\"tree\":\"test:custom\"}");
        assertEquals(.5, ExperienceRules.effectAmount(effect, "test:custom", 2));
        assertEquals(0, ExperienceRules.effectAmount(effect, "test:other", 2));
        assertEquals(0, ExperienceRules.effectAmount(effect, "test:custom", 0));
    }
    @Test void malformedBonusAndUnknownTreeAreRejectedByLoader() {
        for (String effect : List.of("{\"type\":\"mastery:experience_gain\"}",
                "{\"type\":\"mastery:experience_gain\",\"amount\":\"0.2\"}",
                "{\"type\":\"mastery:experience_gain\",\"amount\":101}",
                "{\"type\":\"mastery:experience_gain\",\"amount\":0.2,\"tree\":\"test:missing\"}")) {
            var result = DefinitionLoader.load(Map.of("trees", Map.of("test:custom", json("{}")),
                    "effects", Map.of("test:bonus", json(effect))));
            assertFalse(result.valid(), effect);
        }
    }
    @Test void customAttributeAndReusableTreeBonusSurviveSnapshotRoundTrip() {
        var result = DefinitionLoader.load(Map.of("trees", Map.of("test:custom", json("{\"xp_attribute\":\"mastery:fire_experience_gain\"}")),
                "effects", Map.of("test:bonus", json("{\"type\":\"mastery:experience_gain\",\"amount\":0.2,\"tree\":\"test:custom\"}"))));
        assertTrue(result.valid(), result.errors().toString());
        var copy = DefinitionSet.fromJson(result.definitions().toJson());
        assertEquals("mastery:fire_experience_gain", copy.trees().get("test:custom").xpAttribute());
        assertEquals(result.definitions().effects(), copy.effects());
    }
    @Test void malformedCustomAttributeIsRejected() {
        var result = DefinitionLoader.load(Map.of("trees", Map.of("test:custom", json("{\"xp_attribute\":\"not an identifier\"}"))));
        assertFalse(result.valid());
    }
}
