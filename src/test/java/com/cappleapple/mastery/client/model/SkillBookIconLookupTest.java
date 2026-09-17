package com.cappleapple.mastery.client.model;

import com.cappleapple.mastery.data.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkillBookIconLookupTest {
    private static NodeDefinition node(String id,String token,String icon,String... dependencies) {
        return new NodeDefinition(id,"test:tree",id,"",icon,NodeType.PASSIVE,
                Arrays.stream(dependencies).map(value -> new Dependency(value,1)).toList(),1,1,0,0,
                List.of(),List.of(),"available",List.of(),false,"","",token);
    }
    private static DefinitionSet definitions(NodeDefinition... nodes) {
        Map<String,NodeDefinition> byId=new HashMap<>(); for(var node:nodes)byId.put(node.id(),node);
        return new DefinitionSet(Map.of(),Map.of(),byId,Map.of(),Map.of(),Map.of(),Map.of(),Map.of());
    }
    @Test void matchingIsIndependentOfLearnedProgressAndAcceptsNodeIdDefault() {
        var explicit=node("test:root","test:unlock","minecraft:diamond");
        var defaulted=node("test:default","","test:textures/gui/rune.png");
        var definitions=definitions(explicit,defaulted);
        assertEquals(explicit,SkillBookIconLookup.find(definitions,"test:unlock").orElseThrow());
        assertEquals(defaulted,SkillBookIconLookup.find(definitions,"test:default").orElseThrow());
        assertTrue(SkillBookIconLookup.find(definitions,"test:root").isEmpty());
    }
    @Test void sharedTokenPrefersDependencyRootBeforeAlphabeticallyEarlierDescendant() {
        var root=node("test:z_root","test:unlock","minecraft:diamond","test:foundation");
        var intermediary=node("test:middle","","minecraft:book","test:z_root");
        var descendant=node("test:a_descendant","test:unlock","minecraft:stick","test:middle");
        var foundation=node("test:foundation","","minecraft:book");
        assertEquals(root,SkillBookIconLookup.find(definitions(descendant,intermediary,root,foundation),"test:unlock").orElseThrow());
    }
    @Test void unrelatedSharedRootsUseStableIdsAndFreshDefinitionsChangeIcon() {
        var a=node("test:a","test:unlock","minecraft:diamond");
        var b=node("test:b","test:unlock","minecraft:stick");
        assertEquals(a,SkillBookIconLookup.find(definitions(b,a),"test:unlock").orElseThrow());
        var edited=node("test:a","test:unlock","test:textures/gui/new.png");
        assertEquals(edited.icon(),SkillBookIconLookup.find(definitions(edited,b),"test:unlock").orElseThrow().icon());
    }
    @Test void invalidOrUnknownTokensHaveNoOverlay() {
        var definitions=definitions(node("test:a","test:unlock","minecraft:diamond"));
        for(String token:List.of("","missing_namespace","invalid token","test:missing"))
            assertTrue(SkillBookIconLookup.find(definitions,token).isEmpty(),token);
        assertTrue(SkillBookIconLookup.find(definitions,null).isEmpty());
        assertTrue(SkillBookIconLookup.find(DefinitionSet.EMPTY,"test:unlock").isEmpty());
    }
}
