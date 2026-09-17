package com.cappleapple.mastery.progression;
import com.cappleapple.mastery.data.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class GroupedDependenciesTest {
    private static JsonObject json(String s){return JsonParser.parseString(s).getAsJsonObject();}
    private static DefinitionSet fixture(){
        var result=DefinitionLoader.load(Map.of("trees",Map.of("test:t",new JsonObject()),"nodes",Map.of(
          "test:x",json("{\"tree\":\"test:t\",\"max_rank\":3}"),
          "test:y",json("{\"tree\":\"test:t\",\"max_rank\":3,\"book_token\":\"test:book\"}"),
          "test:z",json("{\"tree\":\"test:t\",\"max_rank\":3}"),
          "test:child",json("{\"tree\":\"test:t\",\"dependencies\":[{\"node\":\"test:x\",\"rank\":2},{\"or\":[{\"node\":\"test:y\",\"rank\":2},{\"node\":\"test:z\",\"rank\":3}]}]}"))));
        assertTrue(result.valid(),result.errors().toString());return DefinitionSet.fromJson(result.definitions().toJson());
    }
    @Test void eitherAlternativeMeetsGroupButMandatoryNodeStillRequired(){
        var defs=fixture();var p=new PlayerProgress();ProgressionService.grantPoints(defs,p,"test:t",20);
        p.node("test:z").rank(3);
        assertFalse(ProgressionService.purchase(defs,p,"test:child",-1,j->true).success());
        p.node("test:x").rank(2);p.node("test:z").rank(2);
        assertFalse(ProgressionService.purchase(defs,p,"test:child",-1,j->true).success());
        p.node("test:z").rank(3);assertTrue(ProgressionService.purchase(defs,p,"test:child",-1,j->true).success());
    }
    @Test void unusedBookGatedAlternativeDoesNotHideTheOpenRoute(){
        var defs=fixture();var p=new PlayerProgress();ProgressionService.grantPoints(defs,p,"test:t",20);p.node("test:x").rank(2);p.node("test:y").rank(2);
        assertFalse(ProgressionService.purchaseBlockReason(defs,p,"test:child",-1,j->true,false).isEmpty());
        p.bookUnlocks().add("test:book");assertTrue(ProgressionService.purchaseBlockReason(defs,p,"test:child",-1,j->true,false).isEmpty());
        assertEquals(Set.of("test:x","test:y","test:z"),new HashSet<>(defs.nodes().get("test:child").dependencyLeaves().stream().map(Dependency::node).toList()));
    }
    @Test void nestedAndOrRetainsGrouping(){
        var group=Dependency.group(false,List.of(Dependency.group(true,List.of(new Dependency("test:x",1),new Dependency("test:y",2))),new Dependency("test:z",3)));
        assertFalse(group.test(d->d.node().equals("test:x")));assertTrue(group.test(d->d.node().equals("test:z")));
        assertEquals("((test:x rank 1 AND test:y rank 2) OR test:z rank 3)",group.describe(id->id));
    }
    @Test void emptyAmbiguousAndCyclicGroupsAreRejected(){
        for(String condition:List.of("{\"or\":[]}","{\"and\":[\"test:n\"],\"or\":[\"test:n\"]}","{\"or\":[\"test:n\"]}")){
            var node=json("{\"tree\":\"test:t\"}");node.add("dependencies",JsonParser.parseString("["+condition+"]"));
            assertFalse(DefinitionLoader.load(Map.of("trees",Map.of("test:t",new JsonObject()),"nodes",Map.of("test:n",node))).valid());
        }
    }
}
