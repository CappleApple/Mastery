package com.cappleapple.mastery.layout;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PromotedRootLayoutTest {
    @Test void promotedTreeSharesGateAnchorWithoutLosingEdgesOrIndependentRows() {
        var graph=List.of(
            new GraphLayout.Entry("base","","base",GraphLayout.Kind.TREE,List.of(),Set.of(),0,0),
            new GraphLayout.Entry("gate","","base",GraphLayout.Kind.NODE,List.of(),Set.of(),1,1),
            new GraphLayout.Entry("branch","gate","branch",GraphLayout.Kind.TREE,List.of(),Set.of(),0,0),
            new GraphLayout.Entry("child","","branch",GraphLayout.Kind.NODE,List.of("gate"),Set.of(),2,2),
            new GraphLayout.Entry("grandchild","","branch",GraphLayout.Kind.NODE,List.of("child"),Set.of(),3,3));
        var placed=GraphLayout.attachPromotedRoots(GraphLayout.arrange(graph,Map.of(),Set.of()),graph);
        assertEquals(placed.anchors().get("gate"),placed.anchors().get("branch"));
        assertTrue(placed.anchors().get("child").y()>placed.anchors().get("gate").y());
        assertTrue(placed.anchors().get("grandchild").y()>placed.anchors().get("child").y());
        assertTrue(placed.edges().stream().anyMatch(e->e.from().equals("gate")&&e.to().equals("child")));
        var byId=new HashMap<String,GraphLayout.Entry>();graph.forEach(e->byId.put(e.id(),e));
        var visible=GraphPresentation.expandedNodes(byId,Set.of("base","gate","branch","child"));visible.remove("branch");
        assertEquals(Set.of("base","gate","child","grandchild"),visible);
        assertFalse(GraphPresentation.visibleEdges(placed.edges(),byId,visible).stream().anyMatch(e->e.from().equals("branch")||e.to().equals("branch")));
    }
    @Test void synergyRootKeepsPrerequisiteLinesSeparateFromItsChildren() {
        var graph=List.of(
            new GraphLayout.Entry("fire","","fire",GraphLayout.Kind.TREE,List.of(),Set.of(),0,0),
            new GraphLayout.Entry("weapon","","weapon",GraphLayout.Kind.TREE,List.of(),Set.of(),0,0),
            new GraphLayout.Entry("gate","","weapon",GraphLayout.Kind.SYNERGY,List.of("fire","weapon"),Set.of("fire","weapon"),1,1),
            new GraphLayout.Entry("branch","gate","branch",GraphLayout.Kind.TREE,List.of(),Set.of(),0,0),
            new GraphLayout.Entry("child","","branch",GraphLayout.Kind.NODE,List.of("gate"),Set.of(),2,2));
        var edges=GraphLayout.arrange(graph,Map.of(),Set.of()).edges();
        assertTrue(edges.stream().filter(e->e.to().equals("gate")).allMatch(GraphLayout.Edge::synergy));
        assertTrue(edges.contains(new GraphLayout.Edge("gate","child",false)));
        var byId=new HashMap<String,GraphLayout.Entry>();graph.forEach(e->byId.put(e.id(),e));
        assertTrue(GraphPresentation.visibleEdges(edges,byId,Set.of("fire","weapon","gate","child")).contains(new GraphLayout.Edge("gate","child",false)));
    }
    @Test void promotedAttachmentAndRepeatedLayoutAreIdempotent() {
        var graph=fixture();var first=GraphLayout.attachPromotedRoots(GraphLayout.arrange(graph,Map.of(),Set.of()),graph);
        assertPoints(first.anchors(),GraphLayout.attachPromotedRoots(first,graph).anchors());
        var offsets=GraphLayout.relativeOffsets(first.anchors(),GraphLayout.primaryParents(graph));
        var again=GraphLayout.restoreOffsets(GraphLayout.arrange(graph,first.anchors(),Set.of()),graph,offsets,Set.of());
        assertPoints(first.anchors(),again.anchors());
    }
    @Test void draggedAndRotatedNestedRootsSurviveSavedRelativeOffsetReload() {
        var graph=fixture();var initial=GraphLayout.attachPromotedRoots(GraphLayout.arrange(graph,Map.of(),Set.of()),graph);
        var anchors=new HashMap<>(initial.anchors());var manual=new HashSet<>(GraphLayout.translateSubtree("gate",graph,anchors,113,-47));
        graph.stream().filter(GraphLayout.Entry::organizational).forEach(entry->manual.remove(entry.id()));
        var dragged=GraphLayout.attachPromotedRoots(new GraphLayout.Result(Map.copyOf(anchors),initial.depths(),initial.edges()),graph);
        for(String id:List.of("gate","branch","child","inner_gate","inner_branch","inner_child")) {
            assertEquals(initial.anchors().get(id).x()+113,dragged.anchors().get(id).x(),1e-8,id+" drag x");
            assertEquals(initial.anchors().get(id).y()-47,dragged.anchors().get(id).y(),1e-8,id+" drag y");
        }
        assertEquals(initial.anchors().get("unrelated"),dragged.anchors().get("unrelated"));
        anchors=new HashMap<>(dragged.anchors());
        manual.addAll(GraphLayout.rotateSubtree("gate",graph,anchors,"south","northeast"));
        var rotated=GraphLayout.attachPromotedRoots(new GraphLayout.Result(Map.copyOf(anchors),initial.depths(),initial.edges()),graph);
        assertEquals(rotated.anchors().get("gate"),rotated.anchors().get("branch"));
        assertEquals(rotated.anchors().get("inner_gate"),rotated.anchors().get("inner_branch"));
        var offsets=GraphLayout.relativeOffsets(rotated.anchors(),GraphLayout.primaryParents(graph));
        var persistedAbsolute=new HashMap<String,GraphLayout.Point>();
        rotated.anchors().forEach((id,point)->{if(!offsets.containsKey(id))persistedAbsolute.put(id,point);});
        assertFalse(persistedAbsolute.containsKey("branch"),"LayoutPreferences persists generated anchors as relative offsets");
        var allTrees=Set.of("base","branch","inner_branch","unrelated");
        var rebuilt=GraphLayout.restoreOffsets(GraphLayout.arrange(graph,persistedAbsolute,allTrees,Map.of("branch","northeast"),manual),graph,offsets,manual);
        assertPoints(rotated.anchors(),rebuilt.anchors());
        var again=GraphLayout.restoreOffsets(GraphLayout.arrange(graph,rebuilt.anchors(),Set.of(),Map.of("branch","northeast"),manual),graph,offsets,manual);
        assertPoints(rebuilt.anchors(),again.anchors());
    }
    @Test void dragWithoutRotationRestoresChildrenWhoseGeneratedParentIsNotManual() {
        var graph=fixture();var initial=GraphLayout.attachPromotedRoots(GraphLayout.arrange(graph,Map.of(),Set.of()),graph);
        var anchors=new HashMap<>(initial.anchors());var manual=new HashSet<>(GraphLayout.translateSubtree("gate",graph,anchors,-81,209));
        graph.stream().filter(GraphLayout.Entry::organizational).forEach(entry->manual.remove(entry.id()));
        var moved=GraphLayout.attachPromotedRoots(new GraphLayout.Result(Map.copyOf(anchors),initial.depths(),initial.edges()),graph);
        var offsets=GraphLayout.relativeOffsets(moved.anchors(),GraphLayout.primaryParents(graph));
        var saved=new HashMap<String,GraphLayout.Point>();moved.anchors().forEach((id,p)->{if(!offsets.containsKey(id))saved.put(id,p);});
        var restored=GraphLayout.restoreOffsets(GraphLayout.arrange(graph,saved,Set.of(),Map.of(),manual),graph,offsets,manual);
        assertPoints(moved.anchors(),restored.anchors());
    }
    private static List<GraphLayout.Entry> fixture() {
        return List.of(
            new GraphLayout.Entry("base","","base",GraphLayout.Kind.TREE,List.of(),Set.of(),0,0),
            new GraphLayout.Entry("unrelated","","unrelated",GraphLayout.Kind.TREE,List.of(),Set.of(),0,0,"east"),
            new GraphLayout.Entry("gate","","base",GraphLayout.Kind.NODE,List.of(),Set.of(),1,1),
            new GraphLayout.Entry("branch","gate","branch",GraphLayout.Kind.TREE,List.of(),Set.of(),0,0),
            new GraphLayout.Entry("child","","branch",GraphLayout.Kind.NODE,List.of(),Set.of(),2,2),
            new GraphLayout.Entry("inner_gate","","branch",GraphLayout.Kind.NODE,List.of("child"),Set.of(),3,3),
            new GraphLayout.Entry("inner_branch","inner_gate","inner_branch",GraphLayout.Kind.TREE,List.of(),Set.of(),0,0),
            new GraphLayout.Entry("inner_child","","inner_branch",GraphLayout.Kind.NODE,List.of(),Set.of(),4,4));
    }
    private static void assertPoints(Map<String,GraphLayout.Point> expected,Map<String,GraphLayout.Point> actual) {
        assertEquals(expected.keySet(),actual.keySet());
        expected.forEach((id,point)->{assertEquals(point.x(),actual.get(id).x(),1e-7,id+" x");assertEquals(point.y(),actual.get(id).y(),1e-7,id+" y");});
    }

}
