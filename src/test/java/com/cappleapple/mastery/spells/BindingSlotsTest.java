package com.cappleapple.mastery.spells;
import com.cappleapple.mastery.progression.PlayerProgress;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class BindingSlotsTest {
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
