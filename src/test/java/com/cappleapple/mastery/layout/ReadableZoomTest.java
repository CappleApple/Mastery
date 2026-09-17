package com.cappleapple.mastery.layout;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ReadableZoomTest {
    @Test void deepZoomKeepsAllBoundsApartWithoutChangingSavedAnchors() {
        Map<String,GraphLayout.Point> anchors=new LinkedHashMap<>();
        for(int i=0;i<200;i++)anchors.put("node"+i,new GraphLayout.Point(i%20*150,i/20*110));
        var before=Map.copyOf(anchors);
        for(double zoom:new double[]{1,.25,.01,.0001}) {
            var projected=ReadableZoom.project(anchors,zoom,20);var points=new ArrayList<>(projected.values());
            double scale=Math.max(zoom,20/40.0);
            for(int a=0;a<points.size();a++)for(int b=a+1;b<points.size();b++)
                assertTrue(Math.abs(points.get(a).x()-points.get(b).x())*zoom>=52*scale+3.999
                        ||Math.abs(points.get(a).y()-points.get(b).y())*zoom>=52*scale+3.999,"Overlapping nodes at "+zoom);
            assertEquals(projected,ReadableZoom.project(new TreeMap<>(anchors),zoom,20));
        }
        assertEquals(before,anchors);
    }
    @Test void separatedNodesKeepTheirPositionAndZoomShrinksOnlyAvailableSpacing() {
        var anchors=Map.of("a",new GraphLayout.Point(-1000,0),"b",new GraphLayout.Point(1000,0));
        assertEquals(anchors,ReadableZoom.project(anchors,.5,20));
        var far=ReadableZoom.project(anchors,.01,20);
        assertTrue(Math.abs(far.get("a").x()-far.get("b").x())*.01>=29.999 || Math.abs(far.get("a").y()-far.get("b").y())*.01>=29.999);
    }
}
