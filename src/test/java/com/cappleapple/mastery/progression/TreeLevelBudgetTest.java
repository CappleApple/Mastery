package com.cappleapple.mastery.progression;
import com.cappleapple.mastery.data.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class TreeLevelBudgetTest {
    private static JsonObject json(String text){return JsonParser.parseString(text).getAsJsonObject();}
    private static DefinitionSet load(String schedule){
        var loaded=DefinitionLoader.load(Map.of("trees",Map.of("test:t",json(schedule)),"nodes",Map.of(
            "test:a",json("{\"tree\":\"test:t\",\"cost\":2,\"max_rank\":3}"),
            "test:b",json("{\"tree\":\"test:t\",\"cost\":1,\"max_rank\":3,\"book_token\":\"test:secret\"}"))));
        assertTrue(loaded.valid(),loaded.errors().toString());return loaded.definitions();
    }
    @Test void capsIncludeEveryRankAndHiddenBranchAndIgnoreLegacyMaximum(){assertEquals(9,load("{\"max_level\":100}").trees().get("test:t").maxLevel());}
    @Test void awardFrequencyAmountMilestonesAndFormulaDetermineFirstFundedLevel(){
        assertEquals(5,load("{\"points_per_award\":2}").trees().get("test:t").maxLevel());
        assertEquals(45,load("{\"point_every\":5}").trees().get("test:t").maxLevel());
        assertEquals(20,load("{\"point_every\":5,\"points_per_award\":2,\"point_milestones\":{\"2\":1}}").trees().get("test:t").maxLevel());
        assertEquals(5,load("{\"point_every\":0,\"point_formula\":\"level*2\"}").trees().get("test:t").maxLevel());
    }
    @Test void xpStopsAtDerivedCapAndNetworkRecomputesIt(){
        var defs=load("{\"points_per_award\":2}");var p=new PlayerProgress();
        var change=ProgressionService.addXp(defs,p,"test:t",1e20,-1);
        assertEquals(5,change.levels());assertEquals(10,p.tree("test:t").points());assertEquals(0,p.tree("test:t").xp());
        assertEquals(5,DefinitionSet.fromJson(defs.toJson()).trees().get("test:t").maxLevel());
        assertFalse(defs.toJson().getAsJsonObject("trees").getAsJsonObject("test:t").has("max_level"));
    }
    @Test void emptyTreeAndExternallyFundedTreeDoNotGrindUnusedLevels(){
        var empty=DefinitionLoader.load(Map.of("trees",Map.of("test:t",new JsonObject())));assertTrue(empty.valid());assertEquals(0,empty.definitions().trees().get("test:t").maxLevel());
        assertEquals(0,load("{\"point_every\":0}").trees().get("test:t").maxLevel());
    }
    @Test void insufficientFiniteAwardsRejectTheReload(){var result=DefinitionLoader.load(Map.of("trees",Map.of("test:t",json("{\"point_every\":0,\"point_milestones\":{\"5\":1}}")),"nodes",Map.of("test:n",json("{\"tree\":\"test:t\",\"max_rank\":2}"))));assertFalse(result.valid());}
}
