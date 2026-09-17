package com.cappleapple.mastery.costs;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class CostPlannerTest {
    private static CostDefinition parse(String json){return CostDefinition.parse(JsonParser.parseString(json).getAsJsonObject());}
    @Test void nestedAlternativesBacktrackAfterLaterAndDemand() {
        var cost=parse("""
            {"and":[{"or":[{"type":"points","tree":"mastery:fire","amount":2},{"type":"points","tree":"mastery:ice","amount":2}]},{"type":"points","tree":"mastery:fire","amount":2}]}
            """);
        var plan=CostPlanner.plan(cost,"mastery:fire",0,0,new CostPlanner.Budget(Map.of("mastery:fire",2,"mastery:ice",2),0,List.of())).orElseThrow();
        assertEquals(Map.of("mastery:fire",2,"mastery:ice",2),plan.points());
    }
    @Test void repeatedCostsCannotDoubleSpendOneBalance() {
        var cost=parse("{\"and\":[{\"type\":\"experience\",\"amount\":7},{\"type\":\"experience\",\"amount\":7}]}");
        assertTrue(CostPlanner.plan(cost,"mastery:fire",0,0,new CostPlanner.Budget(Map.of(),10,List.of())).isEmpty());
    }
    @Test void tagAllocationLeavesExactIngredientAvailable() {
        var cost=parse("{\"and\":[{\"type\":\"item\",\"item_tag\":\"minecraft:planks\",\"amount\":1},{\"type\":\"item\",\"item\":\"minecraft:oak_planks\",\"amount\":1}]}");
        var stacks=List.of(new CostPlanner.Stack(0,"minecraft:oak_planks",Set.of("minecraft:planks"),1),new CostPlanner.Stack(1,"minecraft:birch_planks",Set.of("minecraft:planks"),1));
        var plan=CostPlanner.plan(cost,"mastery:fire",0,0,new CostPlanner.Budget(Map.of(),0,stacks)).orElseThrow();
        assertEquals(Map.of(0,1,1,1),plan.slots());
        assertTrue(CostPlanner.plan(cost,"mastery:fire",0,0,new CostPlanner.Budget(Map.of(),0,List.of(stacks.getFirst()))).isEmpty());
    }
    @Test void depthCostsUseExactDecimalCeilingAndLeafOverride() {
        assertEquals(13,CostDefinition.amount((CostDefinition.Leaf)parse("{\"type\":\"experience\",\"amount\":10}"),3,.1));
        assertEquals(2,CostDefinition.amount((CostDefinition.Leaf)parse("{\"type\":\"points\",\"amount\":1}"),1,.1));
        assertEquals(10,CostDefinition.amount((CostDefinition.Leaf)parse("{\"type\":\"experience\",\"amount\":10,\"depth_percent\":0}"),3,.1));
    }
    @Test void malformedGroupsAndSelectorsFailClosed() {
        assertThrows(IllegalArgumentException.class,()->parse("{\"and\":[],\"or\":[]}"));
        assertThrows(IllegalArgumentException.class,()->parse("{\"type\":\"item\",\"item\":\"minecraft:diamond\",\"item_tag\":\"c:gems\",\"amount\":1}"));
        assertThrows(ArithmeticException.class,()->parse("{\"type\":\"points\",\"amount\":0.5}"));
    }
    @Test void vanillaExperienceUsesLevelAndProgressRatherThanBookkeepingTotal() {
        assertEquals(352,ExperiencePoints.atLevel(16));assertEquals(394,ExperiencePoints.atLevel(17));
        assertEquals(1507,ExperiencePoints.atLevel(31));assertEquals(1628,ExperiencePoints.atLevel(32));
        assertEquals(359,ExperiencePoints.total(16,7f/42f));
        for(int level:new int[]{0,1,16,17,30,31,32,100,10000})assertEquals(level,ExperiencePoints.levelAt(ExperiencePoints.atLevel(level)));
    }
}
