package com.cappleapple.mastery.layout;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GraphLayoutTest {
    private GraphLayout.Entry tree(String id) {
        return new GraphLayout.Entry(id, "", id, GraphLayout.Kind.TREE, List.of(), Set.of(), 0, 0);
    }
    private GraphLayout.Entry node(String id, String tree, List<String> deps, long order) {
        return new GraphLayout.Entry(id, "", tree, GraphLayout.Kind.NODE, deps, Set.of(), order, order);
    }
    @Test void rotatingRootCarriesCustomizedChildrenAndGrandchildrenThroughDiagonalOctants() {
        var graph=List.of(tree("root"),node("child","root",List.of(),1),node("grandchild","root",List.of("child"),2),tree("other"));
        Map<String,GraphLayout.Point> anchors=new HashMap<>(Map.of("root",new GraphLayout.Point(200,300),
                "child",new GraphLayout.Point(235,470),"grandchild",new GraphLayout.Point(188,591),"other",new GraphLayout.Point(-500,0)));
        var original=Map.copyOf(anchors);
        assertEquals(Set.of("child","grandchild"),GraphLayout.rotateSubtree("root",graph,anchors,"south","northeast"));
        assertEquals(original.get("root"),anchors.get("root"));assertEquals(original.get("other"),anchors.get("other"));
        GraphLayout.rotateSubtree("root",graph,anchors,"northeast","south");
        original.forEach((id,p)->{assertEquals(p.x(),anchors.get(id).x(),1e-8);assertEquals(p.y(),anchors.get(id).y(),1e-8);});
    }
    @Test void purchaseChronologyMovesEarlierSiblingLeftAndDependenciesOutward() {
        var graph = List.of(tree("t"), node("a", "t", List.of(), 2), node("b", "t", List.of(), 1), node("c", "t", List.of("a"), 3));
        var result = GraphLayout.arrange(graph, Map.of(), Set.of());
        assertTrue(result.anchors().get("b").x() < result.anchors().get("a").x());
        assertTrue(result.anchors().get("c").y() > result.anchors().get("a").y());
    }
    @Test void hundredsOfNodesAreDeterministicAndKeepUnaffectedAnchors() {
        var graph = new ArrayList<GraphLayout.Entry>();
        for (int t = 0; t < 12; t++) {
            graph.add(tree("tree" + t));
            for (int n = 0; n < 50; n++) graph.add(node("t" + t + "/n" + n, "tree" + t,
                    n < 10 ? List.of() : List.of("t" + t + "/n" + (n - 10)), n < 6 ? 6 - n : 0));
        }
        var first = GraphLayout.arrange(graph, Map.of(), Set.of());
        Collections.shuffle(graph, new Random(731));
        var second = GraphLayout.arrange(graph, Map.of(), Set.of());
        assertEquals(612, first.anchors().size());
        assertEquals(first.anchors(), second.anchors());
        graph.add(node("new", "tree0", List.of(), 20));
        var changed = GraphLayout.arrange(graph, first.anchors(), Set.of("tree0"));
        first.anchors().forEach((id, point) -> { if (!id.startsWith("t0/")) assertEquals(point, changed.anchors().get(id)); });
    }
    @Test void bridgeOccupiesSpaceBetweenTreesAndEdgesCrossingViewportAreKept() {
        var synergy = new GraphLayout.Entry("bridge", "", "left", GraphLayout.Kind.SYNERGY, List.of(), Set.of("left", "right"), 0, 0);
        var result = GraphLayout.arrange(List.of(tree("left"), tree("right"), synergy),
                Map.of("left", new GraphLayout.Point(-400, 0), "right", new GraphLayout.Point(400, 0)), Set.of());
        assertEquals(0, result.anchors().get("bridge").x(), 0.01);
        assertTrue(result.edges().stream().allMatch(GraphLayout.Edge::synergy));
        assertTrue(GraphLayout.edgeInViewport(new GraphLayout.Point(-200, 20), new GraphLayout.Point(200, 20), -50, -50, 50, 50));
        assertFalse(GraphLayout.edgeInViewport(new GraphLayout.Point(-200, 120), new GraphLayout.Point(200, 120), -50, -50, 50, 50));
    }
    @Test void childDragPersistsLocalOffsetsAndMovesDescendantsOnly() {
        var graph = List.of(tree("tree"), node("a", "tree", List.of(), 1), node("b", "tree", List.of("a"), 2), node("c", "tree", List.of(), 3));
        var original = GraphLayout.arrange(graph, Map.of(), Set.of());
        var moved = new HashMap<>(original.anchors());
        var children = GraphLayout.translateSubtree("a", graph, moved, 47, -29);
        assertEquals(Set.of("a", "b"), children);
        assertEquals(original.anchors().get("c"), moved.get("c"));
        var parents = GraphLayout.primaryParents(graph);
        var offsets = GraphLayout.relativeOffsets(moved, parents);
        assertEquals(GraphLayout.relativeOffsets(original.anchors(), parents).get("b"), offsets.get("b"));
        var restored = new HashMap<>(original.anchors());
        GraphLayout.applyRelativeOffsets(restored, parents, offsets, children);
        assertEquals(moved, restored);
    }
    @Test void quadrantPlacementControlsGrowthAndExplicitChildOffsetSurvivesRootMovement() {
        for (String side : List.of("north", "east", "south", "west")) {
            var graph = List.of(tree("t"), node("a", "t", List.of(), 1));
            var result = GraphLayout.arrange(graph, Map.of(), Set.of(), Map.of("t", side), Set.of());
            var root = result.anchors().get("t"); var child = result.anchors().get("a");
            switch (side) {
                case "north" -> assertTrue(child.y() < root.y());
                case "east" -> assertTrue(child.x() > root.x());
                case "south" -> assertTrue(child.y() > root.y());
                case "west" -> assertTrue(child.x() < root.x());
            }
            var moved = new HashMap<>(result.anchors());
            var offsets = GraphLayout.relativeOffsets(moved, GraphLayout.primaryParents(graph));
            GraphLayout.translateSubtree("t", graph, moved, 333, 71);
            assertEquals(offsets, GraphLayout.relativeOffsets(moved, GraphLayout.primaryParents(graph)));
        }
    }
    @Test void multiParentNodesAverageMovementWithInverseSquareDistanceWeights() {
        var graph=List.of(tree("t"),tree("u"),node("near","t",List.of(),1),node("far","u",List.of(),1),
                node("shared","t",List.of("near","far"),2),node("grandchild","t",List.of("shared"),3));
        var original=Map.of("t",new GraphLayout.Point(-100,0),"u",new GraphLayout.Point(1000,0),
                "near",new GraphLayout.Point(0,0),"far",new GraphLayout.Point(300,0),"shared",new GraphLayout.Point(100,0),"grandchild",new GraphLayout.Point(100,100));
        var close=new HashMap<>(original);GraphLayout.translateSubtree("near",graph,close,0,100);
        var far=new HashMap<>(original);GraphLayout.translateSubtree("far",graph,far,0,100);
        assertEquals(80,close.get("shared").y(),1e-9);assertEquals(20,far.get("shared").y(),1e-9);
        assertEquals(180,close.get("grandchild").y(),1e-9);
        assertEquals(original.get("far"),close.get("far"));
        var together=List.of(tree("t"),node("a","t",List.of(),1),node("b","t",List.of(),2),node("c","t",List.of("a","b"),3));
        var anchors=new HashMap<>(GraphLayout.arrange(together,Map.of(),Set.of()).anchors());var before=Map.copyOf(anchors);
        GraphLayout.translateSubtree("t",together,anchors,50,70);
        assertEquals(before.get("c").x()+50,anchors.get("c").x(),1e-8);assertEquals(before.get("c").y()+70,anchors.get("c").y(),1e-8);
    }
    @Test void newChildrenRespectOrientationWithoutMovingEstablishedManualSubtree() {
        var graph = new ArrayList<>(List.of(tree("t"), node("a", "t", List.of(), 1)));
        var original = GraphLayout.arrange(graph, Map.of(), Set.of(), Map.of("t", "west"), Set.of());
        var anchors = new HashMap<>(original.anchors());
        GraphLayout.translateSubtree("a", graph, anchors, 35, 19);
        var offsets = GraphLayout.relativeOffsets(anchors, GraphLayout.primaryParents(graph));
        graph.add(node("b", "t", List.of("a"), 2));
        var changed = GraphLayout.arrange(graph, anchors, Set.of("t"), Map.of("t", "west"), Set.of("a"));
        assertEquals(anchors.get("a"), changed.anchors().get("a"));
        assertTrue(changed.anchors().get("b").x() < changed.anchors().get("a").x());
        assertEquals(offsets.get("a"), GraphLayout.relativeOffsets(changed.anchors(), GraphLayout.primaryParents(graph)).get("a"));
    }
    @Test void initialSectionsGrowOutwardThroughEveryDependencyDepth() {
        Map<String, GraphLayout.Point> outward = Map.of(
                "north", new GraphLayout.Point(0, -1), "east", new GraphLayout.Point(1, 0),
                "south", new GraphLayout.Point(0, 1), "west", new GraphLayout.Point(-1, 0));
        for (var direction : outward.entrySet()) {
            String side = direction.getKey();
            var root = new GraphLayout.Entry("t", "", "t", GraphLayout.Kind.TREE, List.of(), Set.of(), 0, 0, side);
            var graph = List.of(root, node("a", "t", List.of(), 1), node("b", "t", List.of("a"), 2));
            var layout = GraphLayout.arrange(graph, Map.of(), Set.of());
            GraphLayout.Point origin = layout.anchors().get("t"), a = layout.anchors().get("a"), b = layout.anchors().get("b");
            assertEquals(direction.getValue().x() * 138, a.x() - origin.x(), 0.001, side);
            assertEquals(direction.getValue().y() * 138, a.y() - origin.y(), 0.001, side);
            assertEquals(direction.getValue().x() * 138, b.x() - a.x(), 0.001, side);
            assertEquals(direction.getValue().y() * 138, b.y() - a.y(), 0.001, side);
        }
    }
    @Test void eachDroppedEdgeOverridesInitialSectionAndKeepsSavedManualOffsets() {
        Map<String, GraphLayout.Point> drops = Map.of(
                "north", new GraphLayout.Point(12, -100), "east", new GraphLayout.Point(100, 12),
                "south", new GraphLayout.Point(-12, 100), "west", new GraphLayout.Point(-100, -12));
        for (var drop : drops.entrySet()) {
            String side = GraphLayout.sectionAt(drop.getValue().x(), drop.getValue().y());
            assertEquals(drop.getKey(), side);
            var graph = List.of(tree("t"), node("manual", "t", List.of(), 1),
                    node("descendant", "t", List.of("manual"), 2), node("automatic", "t", List.of(), 3));
            var saved = new HashMap<>(GraphLayout.arrange(graph, Map.of(), Set.of()).anchors());
            Set<String> manual = GraphLayout.translateSubtree("manual", graph, saved, 35, -21);
            Map<String, String> parents = GraphLayout.primaryParents(graph);
            Map<String, GraphLayout.Point> offsets = GraphLayout.relativeOffsets(saved, parents);
            GraphLayout.translateSubtree("t", graph, saved, drop.getValue().x(), drop.getValue().y());
            var layout = GraphLayout.arrange(graph, saved, Set.of("t"), Map.of("t", side), manual);
            var restored = new HashMap<>(layout.anchors());
            GraphLayout.applyRelativeOffsets(restored, parents, offsets, manual);
            Map<String, GraphLayout.Point> restoredOffsets = GraphLayout.relativeOffsets(restored, parents);
            for (String id : manual) assertEquals(offsets.get(id), restoredOffsets.get(id), side + " / " + id);
            GraphLayout.Point root = restored.get("t"), automatic = restored.get("automatic");
            switch (side) {
                case "north" -> assertTrue(automatic.y() < root.y());
                case "east" -> assertTrue(automatic.x() > root.x());
                case "south" -> assertTrue(automatic.y() > root.y());
                case "west" -> assertTrue(automatic.x() < root.x());
            }
        }
    }
    private static Map<String, GraphLayout.Point> directions() {
        double d = Math.sqrt(.5);
        return Map.of("north", new GraphLayout.Point(0,-1), "northeast",new GraphLayout.Point(d,-d),
                "east",new GraphLayout.Point(1,0), "southeast",new GraphLayout.Point(d,d),
                "south",new GraphLayout.Point(0,1), "southwest",new GraphLayout.Point(-d,d),
                "west",new GraphLayout.Point(-1,0), "northwest",new GraphLayout.Point(-d,-d));
    }
    @Test void allEightInitialSectionsSeedInsideTheirSectorAndGrowByNormalizedDepth() {
        var graph = new ArrayList<GraphLayout.Entry>();
        for (String section : directions().keySet()) for (int i = 0; i < 3; i++) {
            String id = section + i;
            graph.add(new GraphLayout.Entry(id,"",id,GraphLayout.Kind.TREE,List.of(),Set.of(),0,0,section));
            graph.add(node(id+"/a",id,List.of(),1));
            graph.add(node(id+"/b",id,List.of(id+"/a"),2));
        }
        var layout = GraphLayout.arrange(graph,Map.of(),Set.of());
        for (String section : directions().keySet()) for (int i = 0; i < 3; i++) {
            String id = section + i;
            var root=layout.anchors().get(id); var first=layout.anchors().get(id+"/a"); var second=layout.anchors().get(id+"/b");
            assertEquals(section,GraphLayout.sectionAt(root.x(),root.y()),id);
            var direction=directions().get(section);
            assertEquals(direction.x()*138,first.x()-root.x(),1e-8,id);
            assertEquals(direction.y()*138,first.y()-root.y(),1e-8,id);
            assertEquals(direction.x()*138,second.x()-first.x(),1e-8,id);
            assertEquals(direction.y()*138,second.y()-first.y(),1e-8,id);
        }
    }
    @Test void allEightDropsChangeAutomaticGrowthAndKeepManualLocalCoordinates() {
        for (var drop : directions().entrySet()) {
            var graph=List.of(tree("t"),node("manual","t",List.of(),1),node("child","t",List.of("manual"),2),node("automatic","t",List.of(),3));
            var original=GraphLayout.arrange(graph,Map.of(),Set.of());
            var saved=new HashMap<>(original.anchors());
            var manual=GraphLayout.translateSubtree("manual",graph,saved,33,21);
            var parents=GraphLayout.primaryParents(graph);
            var offsets=GraphLayout.relativeOffsets(saved,parents);
            var oldRoot=saved.get("t"); var newRoot=new GraphLayout.Point(drop.getValue().x()*900,drop.getValue().y()*900);
            GraphLayout.translateSubtree("t",graph,saved,newRoot.x()-oldRoot.x(),newRoot.y()-oldRoot.y());
            String section=GraphLayout.sectionAt(newRoot.x(),newRoot.y());
            assertEquals(drop.getKey(),section);
            var changed=GraphLayout.arrange(graph,saved,Set.of("t"),Map.of("t",section),manual);
            var restored=new HashMap<>(changed.anchors());
            GraphLayout.applyRelativeOffsets(restored,parents,offsets,manual);
            var restoredOffsets=GraphLayout.relativeOffsets(restored,parents);
            for(String id:manual) {
                assertEquals(offsets.get(id).x(),restoredOffsets.get(id).x(),1e-8,section+"/"+id);
                assertEquals(offsets.get(id).y(),restoredOffsets.get(id).y(),1e-8,section+"/"+id);
            }
            var automatic=restored.get("automatic");
            double forward=(automatic.x()-newRoot.x())*drop.getValue().x()+(automatic.y()-newRoot.y())*drop.getValue().y();
            assertEquals(138,forward,1e-8,section);
        }
    }
    @Test void diagonalChronologyRunsPerpendicularToOutwardGrowth() {
        for(String section:List.of("northeast","southeast","southwest","northwest")) {
            var graph=List.of(tree("t"),node("later","t",List.of(),2),node("earlier","t",List.of(),1));
            var layout=GraphLayout.arrange(graph,Map.of(),Set.of(),Map.of("t",section),Set.of());
            var earlier=layout.anchors().get("earlier"); var later=layout.anchors().get("later"); var direction=directions().get(section);
            double dx=later.x()-earlier.x(),dy=later.y()-earlier.y();
            assertEquals(0,dx*direction.x()+dy*direction.y(),1e-8,section);
            assertEquals(132,dx*direction.y()-dy*direction.x(),1e-8,section);
        }
    }
    @Test void sectorBoundariesTurnClockwiseAndOriginHasDeterministicDefault() {
        var clockwise=List.of("east","southeast","south","southwest","west","northwest","north","northeast");
        for(int index=0;index<8;index++) {
            double boundary=22.5+index*45;
            double before=Math.toRadians(boundary-1e-6),after=Math.toRadians(boundary+1e-6),exact=Math.toRadians(boundary);
            assertEquals(clockwise.get(index),GraphLayout.sectionAt(Math.cos(before),Math.sin(before)));
            assertEquals(clockwise.get((index+1)%8),GraphLayout.sectionAt(Math.cos(after),Math.sin(after)));
            assertEquals(clockwise.get((index+1)%8),GraphLayout.sectionAt(Math.cos(exact),Math.sin(exact)));
        }
        assertEquals("south",GraphLayout.sectionAt(0,0));
        assertEquals("south",GraphLayout.sectionAt(Double.NaN,1));
    }}
