package com.cappleapple.mastery.spells;
import com.cappleapple.mastery.progression.PlayerProgress;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class BindingSlotsTest {
    @Test void everyHotbarSetHasExactlyFourPositionsEvenWithLargeCapacity() {
        for(int index=0;index<9;index++) {
            String context=BindingSlots.hotbar(index);
            assertEquals(List.of(0,1,2,3),BindingSlots.visible(Map.of(),"hotbar",context,128));
            assertTrue(LoadoutRules.validate(Map.of(),context,3,"test:fourth",128,true,true).isEmpty());
            assertFalse(LoadoutRules.validate(Map.of(),context,4,"test:fifth",128,true,true).isEmpty());
            assertThrows(IllegalArgumentException.class,()->new PlayerProgress().bind(context,4,"test:fifth"));
            assertEquals("",BindingSlots.get(Map.of(context,List.of("a","b","c","d","e")),context,4));
        }
    }
    @Test void oldHotbarPagesAreRemovedFromSavedDataWithoutTruncatingQuickCast() {
        var p=new PlayerProgress();String context=BindingSlots.hotbar(0);
        p.loadouts().put(context,new ArrayList<>(List.of("a","b","c","d","e","f")));
        p.bind(BindingSlots.QUICK,7,"test:eighth");
        assertEquals(4,p.toJson().getAsJsonObject("loadouts").getAsJsonArray(context).size());
        var legacy=p.toJson();legacy.getAsJsonObject("loadouts").getAsJsonArray(context).add("test:fifth");
        var restored=PlayerProgress.fromJson(legacy);
        assertEquals(List.of("a","b","c","d"),restored.loadout(context));
        assertEquals("test:eighth",BindingSlots.get(restored.loadouts(),BindingSlots.QUICK,7));
    }
    @Test void singleCapacityShowsNoPhantomEmptySlotsAcrossHotbars() {
        var p=new PlayerProgress();p.bind(BindingSlots.hotbar(0),0,"test:spell");
        assertEquals(List.of(0),BindingSlots.visible(p.loadouts(),"hotbar",BindingSlots.hotbar(0),1));
        assertTrue(BindingSlots.visible(p.loadouts(),"hotbar",BindingSlots.hotbar(1),1).isEmpty());
        assertEquals(List.of(0),BindingSlots.visible(p.loadouts(),"quick_cast",BindingSlots.QUICK,1));
        assertFalse(LoadoutRules.validate(BindingSlots.modeLoadouts(p.loadouts(),"hotbar"),BindingSlots.hotbar(1),0,"test:other",1,true,true).isEmpty());
    }
    @Test void additionalSlotsPersistAndModesStayIndependent() {
        var p=new PlayerProgress();p.bindingMode("quick_cast");p.bind(BindingSlots.QUICK,7,"test:eighth");p.bind(BindingSlots.hotbar(8),0,"test:hotbar");
        var restored=PlayerProgress.fromJson(p.toJson());assertEquals("quick_cast",restored.bindingMode());
        assertEquals("test:eighth",BindingSlots.get(restored.loadouts(),BindingSlots.QUICK,7));
        assertEquals("test:hotbar",BindingSlots.get(restored.loadouts(),BindingSlots.hotbar(8),0));
        assertEquals(8,BindingSlots.visible(restored.loadouts(),"quick_cast",BindingSlots.QUICK,8).size());
        assertEquals(1,LoadoutRules.equipped(BindingSlots.modeLoadouts(restored.loadouts(),"hotbar")));
    }
}
