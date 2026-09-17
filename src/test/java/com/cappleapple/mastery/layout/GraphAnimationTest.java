package com.cappleapple.mastery.layout;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class GraphAnimationTest {
    @Test void expandCollapseReverseAndDisabledAnimationKeepTargetsUnchanged() {
        var animation=new GraphAnimation();var zero=new GraphLayout.Point(0,0);var target=new GraphLayout.Point(100,200);
        var visible=Map.of("node",target);
        animation.update(visible,id->zero,0,true,1);assertEquals(zero,animation.frame(0).get("node"));
        assertEquals(new GraphLayout.Point(50,100),animation.frame(125).get("node"));
        animation.update(Map.of(),id->zero,125,true,1);assertTrue(animation.frame(200).containsKey("node"));assertTrue(animation.leaving("node"));
        animation.update(visible,id->zero,200,true,2);assertEquals(target,animation.frame(325).get("node"));assertFalse(animation.leaving("node"));
        animation.update(Map.of(),id->zero,325,false,1);assertTrue(animation.frame(325).isEmpty());assertEquals(target,visible.get("node"));
    }
}
