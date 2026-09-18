package com.cappleapple.mastery.layout;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class GraphAnimationTest {
    @Test void openingAndReopeningSnapEvenAfterAnEmptyLoadingFrame() {
        var animation=new GraphAnimation();var zero=new GraphLayout.Point(0,0);var target=new GraphLayout.Point(100,200);
        animation.update(Map.of(),id->zero,0,true,1);
        animation.update(Map.of("node",target),id->zero,1,true,1);
        assertEquals(target,animation.frame(1).get("node"));
        animation.update(Map.of(),id->zero,2,true,1);assertTrue(animation.leaving("node"));
        animation.reset();animation.update(Map.of("node",target),id->zero,3,true,1);
        assertEquals(target,animation.frame(3).get("node"));assertFalse(animation.leaving("node"));
    }

    @Test void expandCollapseReverseAndDisabledAnimationKeepTargetsUnchanged() {
        var animation=new GraphAnimation();var zero=new GraphLayout.Point(0,0);var target=new GraphLayout.Point(100,200);
        animation.update(Map.of("root",zero),id->zero,-1,true,1);
        var visible=Map.of("root",zero,"node",target);
        animation.update(visible,id->zero,0,true,1);assertEquals(zero,animation.frame(0).get("node"));
        assertEquals(new GraphLayout.Point(50,100),animation.frame(125).get("node"));
        animation.update(Map.of(),id->zero,125,true,1);assertTrue(animation.frame(200).containsKey("node"));assertTrue(animation.leaving("node"));
        animation.update(visible,id->zero,200,true,2);assertEquals(target,animation.frame(325).get("node"));assertFalse(animation.leaving("node"));
        animation.update(Map.of(),id->zero,325,false,1);assertTrue(animation.frame(325).isEmpty());assertEquals(target,visible.get("node"));
    }
}
