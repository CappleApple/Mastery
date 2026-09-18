package com.cappleapple.mastery.data;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class NodeAppearanceTest {
    @Test void circlesAndIconOnlyAreDefault(){assertEquals(NodeAppearance.Shape.CIRCLE,NodeAppearance.DEFAULT.shape());assertFalse(NodeAppearance.DEFAULT.showName());}
    @Test void shapeHitAreasFollowTheirOutlines(){
        for(var shape:NodeAppearance.Shape.values()){assertTrue(shape.contains(0,0,20));assertFalse(shape.contains(21,0,20));}
        assertFalse(NodeAppearance.Shape.SQUARE.contains(19,19,20));
        assertTrue(NodeAppearance.Shape.SQUARE.contains(19,0,20));
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
    @Test void categoryDefaultsAndPromotedRootsSurviveSyncAndOverrides() {
        var nodes=new java.util.HashMap<String,JsonObject>();
        for(String type:java.util.List.of("passive","active","modifier"))nodes.put("test:"+type,JsonParser.parseString("{\"tree\":\"test:tree\",\"type\":\""+type+"\"}").getAsJsonObject());
        nodes.put("test:root",JsonParser.parseString("{\"tree\":\"test:tree\",\"type\":\"active\",\"root_tree\":true}").getAsJsonObject());
        var loaded=DefinitionLoader.load(Map.of("trees",Map.of("test:tree",new JsonObject()),"nodes",nodes));
        assertTrue(loaded.valid(),loaded.errors().toString());
        var defs=DefinitionSet.fromJson(loaded.definitions().toJson());
        var expected=Map.of("test:tree",NodeAppearance.Shape.PENTAGON,"test:root",NodeAppearance.Shape.PENTAGON,"test:passive",NodeAppearance.Shape.CIRCLE,"test:active",NodeAppearance.Shape.SQUARE,"test:modifier",NodeAppearance.Shape.TRIANGLE);
        expected.forEach((id,shape)->assertEquals(shape,NodeAppearance.parse(SettingsResolver.forNode(defs,id).getAsJsonObject("appearance")).shape(),id));
        assertFalse(NodeAppearance.Shape.PENTAGON.contains(0,19,20));
        assertFalse(NodeAppearance.Shape.TRIANGLE.contains(15,-10,20));
        assertTrue(NodeAppearance.Shape.TRIANGLE.contains(15,15,20));
    }
    @Test void rootSizeIsIndependentOfShapeAndRoundTripsWithOverrides() {
        var look=NodeAppearance.parse(JsonParser.parseString("{\"shape\":\"square\",\"scale\":1.2,\"root_scale\":1.3}").getAsJsonObject());
        assertEquals(1.2,look.scaleFor(false),1e-9);assertEquals(1.56,look.scaleFor(true),1e-9);
        assertEquals(look,NodeAppearance.parse(look.toJson()));
        assertEquals(1.3,NodeAppearance.DEFAULT.scaleFor(true),1e-9);
        assertThrows(IllegalArgumentException.class,()->NodeAppearance.parse(JsonParser.parseString("{\"root_scale\":0}").getAsJsonObject()));
    }
    @Test void invalidDelaysRejected(){for(String value:new String[]{"-1","60001","0.5","\"500\""})assertThrows(IllegalArgumentException.class,()->UnlockPresentation.parse(JsonParser.parseString("{\"hold_delay_ms\":"+value+"}").getAsJsonObject()));}
}
