package com.cappleapple.mastery.data;
import com.cappleapple.mastery.layout.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class TreePresentationTest {
    @Test void globalTreeAndNodeOverridesMergeFieldByFieldAndPreserveDefaultTokens() {
        var defaults=JsonParser.parseString("{\"modifier_slots\":{\"base\":2,\"per_level\":1,\"max\":12},\"unlock\":{\"fill_direction\":\"horizontal\"}}").getAsJsonObject();
        var tree=JsonParser.parseString("{\"modifier_slots\":{\"base\":3},\"unlock\":{\"fill_direction\":\"vertical\"}}").getAsJsonObject();
        var node=JsonParser.parseString("{\"tree\":\"test:tree\",\"modifier_slots\":{\"base\":\"default\",\"per_level\":2},\"unlock\":\"default\"}").getAsJsonObject();
        var loaded=DefinitionLoader.load(Map.of("settings",Map.of("mastery:defaults",defaults),"trees",Map.of("test:tree",tree),"nodes",Map.of("test:node",node)));
        assertTrue(loaded.valid(),loaded.errors().toString());var defs=DefinitionSet.fromJson(loaded.definitions().toJson());
        var resolved=SettingsResolver.forNode(defs,"test:node");
        assertEquals(7,SettingsResolver.modifierSlots(resolved,3));assertEquals(12,SettingsResolver.modifierSlots(resolved,100));
        assertEquals("vertical",resolved.getAsJsonObject("unlock").get("fill_direction").getAsString());
        assertEquals("default",defs.toJson().getAsJsonObject("nodes").getAsJsonObject("test:node").get("unlock").getAsString());
    }
    @Test void lineStylesInheritIndependentlyAndSurviveDefinitionSync() {
        var defaults=JsonParser.parseString("{\"connections\":{\"parent_line_style\":\"solid\",\"child_line_style\":\"dashed\"}}").getAsJsonObject();
        var tree=JsonParser.parseString("{\"connections\":{\"child_line_style\":\"solid\"}}").getAsJsonObject();
        var node=JsonParser.parseString("{\"tree\":\"test:tree\",\"connections\":{\"parent_line_style\":\"default\",\"child_line_style\":\"dashed\"}}").getAsJsonObject();
        var loaded=DefinitionLoader.load(Map.of("settings",Map.of("mastery:defaults",defaults),"trees",Map.of("test:tree",tree),"nodes",Map.of("test:node",node)));
        assertTrue(loaded.valid(),loaded.errors().toString());
        var synced=DefinitionSet.fromJson(loaded.definitions().toJson());
        var styles=ConnectionPresentation.parse(SettingsResolver.forNode(synced,"test:node").getAsJsonObject("connections"));
        assertEquals(ConnectionPresentation.Style.SOLID,styles.parentLineStyle());
        assertEquals(ConnectionPresentation.Style.DASHED,styles.childLineStyle());
        assertEquals(ConnectionPresentation.DEFAULT,ConnectionPresentation.parse(new JsonObject()));
        assertEquals(styles,ConnectionPresentation.parse(styles.toJson()));
        node.getAsJsonObject("connections").addProperty("child_line_style","zigzag");
        assertFalse(DefinitionLoader.load(Map.of("trees",Map.of("test:tree",tree),"nodes",Map.of("test:node",node))).valid());
    }
    @Test void themesValidateAndSurviveDefinitionSync() {
        var theme=new TreeTheme("#abcdef","#012345",.35);
        assertEquals(theme,TreeTheme.parse(theme.toJson()));
        assertEquals("#ABCDEF",theme.innerColor());
        for(double value:new double[]{-.1,1.1,Double.NaN,Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,()->new TreeTheme("#FFFFFF","#000000",value));
        assertThrows(IllegalArgumentException.class,()->new TreeTheme("red","#000000",1));
        var tree=new JsonObject();tree.add("theme",theme.toJson());
        var loaded=DefinitionLoader.load(Map.of("trees",Map.of("test:tree",tree)));
        assertTrue(loaded.valid(),loaded.errors().toString());
        assertEquals(theme,DefinitionSet.fromJson(loaded.definitions().toJson()).trees().get("test:tree").theme());
    }
    @Test void gradientsUseColoredEnabledDimDisabledAndGrayLockedStates() {
        var theme=new TreeTheme("#FF8040","#204080",1);
        assertEquals(0xFFFF8040,ThemePalette.color(theme,1,ThemePalette.State.ENABLED,1));
        assertEquals(0xFF204080,ThemePalette.color(theme,0,ThemePalette.State.ENABLED,1));
        assertEquals(0xFF66331A,ThemePalette.color(theme,1,ThemePalette.State.DISABLED,1));
        assertEquals(0xFFA0A0A0,ThemePalette.color(theme,1,ThemePalette.State.LOCKED,1));
        var sharp=new TreeTheme("#FFFFFF","#000000",0);
        assertEquals(0xFF000000,ThemePalette.color(sharp,.49,ThemePalette.State.ENABLED,1));
        assertEquals(0xFFFFFFFF,ThemePalette.color(sharp,.51,ThemePalette.State.ENABLED,1));
        assertEquals(0xFF808080,ThemePalette.color(new TreeTheme("#FFFFFF","#000000",1),.5,ThemePalette.State.ENABLED,1));
    }
    @Test void inheritedUnlockOptionsRemainDefaultAcrossNetworkAndResolveAtUse() {
        var settings=JsonParser.parseString("{\"unlock\":{\"fill_direction\":\"horizontal\",\"progress_sound\":\"none\"}}").getAsJsonObject();
        var loaded=DefinitionLoader.load(Map.of("settings",Map.of("mastery:defaults",settings),"trees",Map.of("test:tree",new JsonObject())));
        assertTrue(loaded.valid(),loaded.errors().toString());
        var synced=DefinitionSet.fromJson(loaded.definitions().toJson());
        var options=synced.trees().get("test:tree").unlock();
        assertEquals(UnlockPresentation.INHERIT,options);
        assertEquals("horizontal",options.resolve(synced.unlockDefaults()).fillDirection());
        assertEquals("none",options.resolve(synced.unlockDefaults()).progressSound());
        assertEquals("minecraft:block.beacon.power_select",options.resolve(synced.unlockDefaults()).completeSound());
        assertEquals("vertical",new UnlockPresentation("vertical","default","default").resolve(synced.unlockDefaults()).fillDirection());
        assertThrows(IllegalArgumentException.class,()->new UnlockPresentation("diagonal","default","default"));
        assertThrows(IllegalArgumentException.class,()->new UnlockPresentation("default","bad sound!","default"));
        assertFalse(DefinitionLoader.load(Map.of("settings",Map.of("test:unknown",settings))).valid());
    }
    @Test void holdingWaitsBeforeEightDingsAndCancelsCleanly() {
        var hold=new UnlockHold();hold.start();int dings=0;
        for(int tick=0;tick<2;tick++){assertEquals(-1,hold.advance());assertFalse(hold.presenting());assertEquals(0,hold.progress());}
        for(int tick=0;tick<20;tick++){if(hold.advance()>=0)dings++;assertEquals(tick==19,hold.ready());}
        assertEquals(8,dings);assertEquals(1,hold.progress());
        for(int tick=0;tick<40;tick++)assertEquals(-1,hold.advance());
        hold.cancel();assertFalse(hold.ready());assertEquals(0,hold.progress());
        hold.start();for(int tick=0;tick<8;tick++)hold.advance();hold.cancel();hold.start();
        assertEquals(0,hold.ticks());assertFalse(hold.ready());
    }
}
