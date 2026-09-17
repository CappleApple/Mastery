package com.cappleapple.mastery.data;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class NodeAppearanceTest {
    @Test void circlesAndIconOnlyAreDefault(){assertEquals(NodeAppearance.Shape.CIRCLE,NodeAppearance.DEFAULT.shape());assertFalse(NodeAppearance.DEFAULT.showName());}
    @Test void shapeHitAreasFollowTheirOutlines(){
        for(var shape:NodeAppearance.Shape.values()){assertTrue(shape.contains(0,0,20));assertFalse(shape.contains(21,0,20));}
        assertTrue(NodeAppearance.Shape.SQUARE.contains(19,19,20));
        assertFalse(NodeAppearance.Shape.CIRCLE.contains(19,19,20));
        assertFalse(NodeAppearance.Shape.DIAMOND.contains(12,12,20));
        assertTrue(NodeAppearance.Shape.HEXAGON.contains(8,19,20));
    }
    @Test void settingsAndDelayInheritThroughNetworkRoundTrip(){
        var global=JsonParser.parseString("{\"appearance\":{\"shape\":\"square\",\"show_name\":true},\"unlock\":{\"hold_delay_ms\":750}}").getAsJsonObject();
        var tree=JsonParser.parseString("{\"appearance\":{\"shape\":\"hexagon\"}}").getAsJsonObject();
        var node=JsonParser.parseString("{\"tree\":\"test:tree\",\"appearance\":{\"show_name\":false},\"unlock\":{\"hold_delay_ms\":\"default\"}}").getAsJsonObject();
        var loaded=DefinitionLoader.load(Map.of("settings",Map.of("mastery:defaults",global),"trees",Map.of("test:tree",tree),"nodes",Map.of("test:node",node)));
        assertTrue(loaded.valid(),loaded.errors().toString());
        var settings=SettingsResolver.forNode(DefinitionSet.fromJson(loaded.definitions().toJson()),"test:node");
        assertEquals(new NodeAppearance(NodeAppearance.Shape.HEXAGON,false),NodeAppearance.parse(settings.getAsJsonObject("appearance")));
        assertEquals(750,UnlockPresentation.parse(settings.getAsJsonObject("unlock")).holdDelayMs());
    }
    @Test void invalidDelaysRejected(){for(String value:new String[]{"-1","60001","0.5","\"500\""})assertThrows(IllegalArgumentException.class,()->UnlockPresentation.parse(JsonParser.parseString("{\"hold_delay_ms\":"+value+"}").getAsJsonObject()));}
}
