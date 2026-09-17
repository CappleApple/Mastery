package com.cappleapple.mastery.costs;

import com.cappleapple.mastery.data.*;
import com.cappleapple.mastery.progression.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class CostResolutionTest {
    private static JsonObject json(String text){return JsonParser.parseString(text).getAsJsonObject();}
    @Test void crossTreeCurrencyBudgetIncludesMaximumOrAlternativeAndDepth() {
        var loaded=DefinitionLoader.load(Map.of("trees",Map.of("test:a",json("{}"),"test:b",json("{}")),"nodes",Map.of(
                "test:root",json("{\"tree\":\"test:a\",\"cost\":0}"),
                "test:child",json("""
                    {"tree":"test:a","max_rank":2,"dependencies":[{"node":"test:root"}],"cost_depth_percent":0.5,"costs":{"or":[{"type":"points","tree":"test:b","amount":2},{"type":"points","tree":"test:b","amount":3}]}}
                    """))));
        assertTrue(loaded.valid(),loaded.errors().toString());
        assertEquals(10,loaded.definitions().trees().get("test:b").maxLevel());
        assertEquals(2,loaded.definitions().trees().get("test:a").maxLevel());
        assertEquals(10,DefinitionSet.fromJson(loaded.definitions().toJson()).trees().get("test:b").maxLevel());
    }
    @Test void inheritedExpressionReplacementDoesNotMergeDifferentLogicalGroups() {
        var parent=json("{\"costs\":{\"and\":[{\"type\":\"points\",\"amount\":1}]},\"cost_depth_percent\":0.2}");
        var child=json("{\"costs\":{\"or\":[{\"type\":\"experience\",\"amount\":20},{\"type\":\"points\",\"amount\":2}]}}");
        var merged=SettingsResolver.merge(parent,child);
        assertFalse(merged.getAsJsonObject("costs").has("and"));assertEquals(.2,merged.get("cost_depth_percent").getAsDouble());
        assertInstanceOf(CostDefinition.Group.class,CostDefinition.parse(merged.getAsJsonObject("costs")));
    }
    @Test void purePointPurchasesDebitAllCurrenciesAndRespectFailureAtomicity() {
        var loaded=DefinitionLoader.load(Map.of("trees",Map.of("test:a",json("{}"),"test:b",json("{}")),"nodes",Map.of("test:n",json("{\"tree\":\"test:a\",\"costs\":{\"and\":[{\"type\":\"points\",\"amount\":2},{\"type\":\"points\",\"tree\":\"test:b\",\"amount\":3}]}}"))));
        assertTrue(loaded.valid(),loaded.errors().toString());var defs=loaded.definitions();var progress=new PlayerProgress();progress.tree("test:a").points(2);progress.tree("test:b").points(2);
        assertFalse(ProgressionService.purchase(defs,progress,"test:n",-1,e->true).success());assertEquals(2,progress.tree("test:a").points());
        progress.tree("test:b").points(3);assertTrue(ProgressionService.purchase(defs,progress,"test:n",-1,e->true).success());
        assertEquals(0,progress.tree("test:a").points());assertEquals(0,progress.tree("test:b").points());assertEquals(1,progress.rank("test:n"));
    }
    @Test void chronologyExhaustionCannotDebitAResource() {
        var loaded=DefinitionLoader.load(Map.of("trees",Map.of("test:a",json("{}")),"nodes",Map.of("test:n",json("{\"tree\":\"test:a\"}"))));
        var progress=new PlayerProgress();progress.tree("test:a").points(10);var saved=progress.toJson();saved.addProperty("sequence",Long.MAX_VALUE);progress=PlayerProgress.fromJson(saved);
        assertFalse(ProgressionService.purchase(loaded.definitions(),progress,"test:n",-1,e->true).success());assertEquals(10,progress.tree("test:a").points());
    }
}
