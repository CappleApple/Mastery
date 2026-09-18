package com.cappleapple.mastery.data;

import com.cappleapple.mastery.costs.CostResolver;
import com.cappleapple.mastery.graph.GraphValidator;
import com.cappleapple.mastery.progression.*;
import com.google.gson.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PromotedTreesTest {
    private static JsonObject json(String text){return JsonParser.parseString(text).getAsJsonObject();}
    private static DefinitionLoader.LoadResult load(Map<String,JsonObject> nodes) {
        return DefinitionLoader.load(Map.of("trees",Map.of("test:base",json("{}")),"nodes",nodes));
    }
    private static DefinitionSet fixture() {
        var result=load(Map.of(
            "test:prerequisite",json("{\"tree\":\"test:base\",\"max_rank\":2}"),
            "test:root",json("{\"tree\":\"test:base\",\"dependencies\":[{\"node\":\"test:prerequisite\",\"rank\":2}],\"root_tree\":{\"id\":\"test:branch\",\"xp_base\":10,\"xp_growth\":0}}"),
            "test:child",json("{\"tree\":\"test:base\",\"dependencies\":[\"test:root\"],\"max_rank\":3}"),
            "test:grandchild",json("{\"tree\":\"test:base\",\"dependencies\":[\"test:child\"]}")));
        assertTrue(result.valid(),result.errors().toString());return result.definitions();
    }
    @Test void generatedCurrencyPreservesRootIdentityAndMovesOnlyDescendants() {
        var d=fixture();assertEquals("test:base",d.nodes().get("test:root").tree());
        assertEquals("test:branch",d.nodes().get("test:child").tree());
        assertEquals("test:branch",d.nodes().get("test:grandchild").tree());
        assertEquals("test:base",d.nodes().get("test:prerequisite").tree());
        assertEquals(3,d.trees().get("test:base").maxLevel());assertEquals(4,d.trees().get("test:branch").maxLevel());
        assertEquals(0,GraphValidator.depthOf(d,"test:child"));assertEquals(1,GraphValidator.depthOf(d,"test:grandchild"));
        assertEquals("test:base",CostResolver.forNode(d,"test:root").owningTree());
        assertEquals("test:branch",CostResolver.forNode(d,"test:child").owningTree());
    }
    @Test void authoredRoundTripNeverMaterializesGeneratedTreesOrLosesPrerequisites() {
        var d=fixture();var encoded=d.toJson();
        assertFalse(encoded.getAsJsonObject("trees").has("test:branch"));
        assertEquals("test:base",encoded.getAsJsonObject("nodes").getAsJsonObject("test:child").get("tree").getAsString());
        assertFalse(encoded.toString().contains("authored_tree"));
        var copy=DefinitionSet.fromJson(encoded);assertEquals(encoded,copy.toJson());
        assertEquals(d.nodes().get("test:root").dependencies(),copy.nodes().get("test:root").dependencies());
    }
    @Test void rootUsesOldPointsThenEnablesIndependentXpAndPurchases() {
        var d=fixture();var player=new PlayerProgress();
        ProgressionService.grantPoints(d,player,"test:base",3);
        assertFalse(ProgressionService.addXp(d,player,"test:branch",10,-1).success());
        assertFalse(ProgressionService.purchase(d,player,"test:root",-1,r->true).success());
        assertTrue(ProgressionService.purchase(d,player,"test:prerequisite",-1,r->true).success());
        assertTrue(ProgressionService.purchase(d,player,"test:prerequisite",-1,r->true).success());
        assertTrue(ProgressionService.purchase(d,player,"test:root",-1,r->true).success());
        assertEquals(0,player.tree("test:base").points());assertTrue(player.tree("test:branch").discovered());
        assertTrue(ProgressionService.addXp(d,player,"test:branch",10,-1).success());
        assertEquals(1,player.tree("test:branch").points());
        assertTrue(ProgressionService.purchase(d,player,"test:child",-1,r->true).success());
        assertEquals(0,player.tree("test:branch").points());assertEquals(1,player.rank("test:child"));
    }
    @Test void resetPrerequisiteDisablesRootAndItsStoredCurrencyWithoutErasingHistory() {
        var d=fixture();var p=new PlayerProgress();
        ProgressionService.setRank(d,p,"test:prerequisite",2,-1);ProgressionService.setRank(d,p,"test:root",1,-1);
        ProgressionService.grantPoints(d,p,"test:branch",5);ProgressionService.setRank(d,p,"test:child",1,-1);
        assertTrue(PromotedTrees.unlocked(d,p,"test:branch",-1));
        ProgressionService.setRank(d,p,"test:prerequisite",1,-1);
        assertFalse(PromotedTrees.unlocked(d,p,"test:branch",-1));assertFalse(ProgressionService.addXp(d,p,"test:branch",10,-1).success());
        assertEquals(0,ProgressionService.cappedRank(d,p,"test:child",-1));
        assertEquals(0,CostResolver.pointsBudget(d,p,-1).points().getOrDefault("test:branch",0));
        assertEquals(5,p.tree("test:branch").points());assertEquals(1,p.rank("test:child"));
    }
    @Test void nestedPromotionGivesNearestRootOwnership() {
        var result=load(Map.of(
            "test:a",json("{\"tree\":\"test:base\",\"root_tree\":true}"),
            "test:b",json("{\"tree\":\"test:base\",\"dependencies\":[\"test:a\"],\"root_tree\":{}}"),
            "test:c",json("{\"tree\":\"test:base\",\"dependencies\":[\"test:b\",\"test:a\"]}")));
        assertTrue(result.valid(),result.errors().toString());
        assertEquals("test:a_tree",result.definitions().nodes().get("test:b").tree());
        assertEquals("test:b_tree",result.definitions().nodes().get("test:c").tree());
    }
    @Test void ambiguousSiblingBranchesNeedExplicitOwnership() {
        var nodes=new LinkedHashMap<String,JsonObject>();
        nodes.put("test:a",json("{\"tree\":\"test:base\",\"root_tree\":true}"));
        nodes.put("test:b",json("{\"tree\":\"test:base\",\"root_tree\":true}"));
        nodes.put("test:c",json("{\"tree\":\"test:base\",\"dependencies\":[\"test:a\",\"test:b\"]}"));
        assertTrue(load(nodes).errors().stream().anyMatch(e->e.contains("cross promoted branches")));
        nodes.get("test:c").addProperty("tree","test:a_tree");assertTrue(load(nodes).valid(),load(nodes).errors().toString());
    }
    @Test void generatedIdsCannotOverwriteNodesOrTrees() {
        for(String id:List.of("test:base","test:root")) {
            var result=load(Map.of("test:root",json("{\"tree\":\"test:base\",\"root_tree\":{\"id\":\""+id+"\"}}")));
            assertTrue(result.errors().stream().anyMatch(e->e.contains("conflicts")));
        }
    }
    @Test void disabledPromotionAndMalformedValuesAreHandledExplicitly() {
        assertEquals(1,load(Map.of("test:root",json("{\"tree\":\"test:base\",\"root_tree\":{\"enabled\":false}}"))).definitions().trees().size());
        assertFalse(load(Map.of("test:root",json("{\"tree\":\"test:base\",\"root_tree\":7}"))).valid());
    }
    @Test void rootCannotDemandItsOwnLockedCurrencyWithoutAnAlternativeRoute() {
        var root=json("{\"tree\":\"test:base\",\"root_tree\":true,\"costs\":{\"type\":\"points\",\"tree\":\"test:root_tree\",\"amount\":1}}");
        assertTrue(load(Map.of("test:root",root)).errors().stream().anyMatch(e->e.contains("own locked point currency")));
        root.add("costs",json("{\"or\":[{\"type\":\"points\",\"tree\":\"test:root_tree\",\"amount\":1},{\"type\":\"experience\",\"amount\":10}]}"));
        assertTrue(load(Map.of("test:root",root)).valid());
    }
    @Test void retainedTreeLevelRequirementRemainsAnActivationGate() {
        var encoded=fixture().toJson();encoded.getAsJsonObject("nodes").getAsJsonObject("test:root").add("requirements",JsonParser.parseString("[{\"type\":\"mastery:tree_level\",\"tree\":\"test:base\",\"level\":2}]"));
        var d=DefinitionSet.fromJson(encoded);var p=new PlayerProgress();
        ProgressionService.setRank(d,p,"test:prerequisite",2,-1);ProgressionService.setRank(d,p,"test:root",1,-1);
        assertFalse(PromotedTrees.unlocked(d,p,"test:branch",-1));
        ProgressionService.setLevel(d,p,"test:base",2,-1);assertTrue(PromotedTrees.unlocked(d,p,"test:branch",-1));
        ProgressionService.setLevel(d,p,"test:base",1,-1);assertFalse(PromotedTrees.unlocked(d,p,"test:branch",-1));
    }
    @Test void runtimeRootPrerequisiteOrDoesNotRequireBothAlternativeWorldConditions() {
        var result=load(Map.of(
            "test:a",json("{\"tree\":\"test:base\",\"requirements\":[{\"type\":\"mastery:condition\",\"dimension\":\"minecraft:the_nether\"}]}"),
            "test:b",json("{\"tree\":\"test:base\"}"),
            "test:root",json("{\"tree\":\"test:base\",\"root_tree\":true,\"dependencies\":[{\"or\":[\"test:a\",\"test:b\"]}]}")));
        assertTrue(result.valid(),result.errors().toString());var d=result.definitions();var p=new PlayerProgress();
        for(String id:List.of("test:a","test:b","test:root"))ProgressionService.setRank(d,p,id,1,-1);
        assertTrue(PromotedTrees.unlocked(d,p,"test:root_tree",-1,requirement->false));
        ProgressionService.setRank(d,p,"test:b",0,-1);
        assertFalse(PromotedTrees.unlocked(d,p,"test:root_tree",-1,requirement->false));
    }

    @Test void negatedKnownRequirementsAreNotTreatedAsUnknownWorldPredicates() {
        var encoded=fixture().toJson();encoded.getAsJsonObject("nodes").getAsJsonObject("test:root").add("requirements",JsonParser.parseString("[{\"not\":{\"type\":\"mastery:tree_level\",\"tree\":\"test:base\",\"level\":2}}]"));
        var d=DefinitionSet.fromJson(encoded);var p=new PlayerProgress();
        ProgressionService.setRank(d,p,"test:prerequisite",2,-1);ProgressionService.setRank(d,p,"test:root",1,-1);
        assertTrue(PromotedTrees.unlocked(d,p,"test:branch",-1));
        ProgressionService.setLevel(d,p,"test:base",2,-1);assertFalse(PromotedTrees.unlocked(d,p,"test:branch",-1));
        for(String malformed:List.of("7","\"not a resource\""))assertFalse(load(Map.of("test:root",json("{\"tree\":\"test:base\",\"root_tree\":{\"id\":"+malformed+"}}"))).valid());
    }

}
