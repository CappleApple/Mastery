package com.cappleapple.mastery.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.cappleapple.mastery.client.ActiveInputPolicy.*;

class ActiveInputPolicyTest {
    @Test void emptyContextLeavesEveryVanillaActionAlone() {
        for (Action action : Action.values()) assertEquals(Decision.PASS, decide(action, false, false, true));
    }
    @Test void equippedSlotConsumesPressAndRepeatOnlyOnce() {
        assertEquals(Decision.START, decide(Action.PRESS, false, true, true));
        assertEquals(Decision.CONSUME, decide(Action.REPEAT, true, true, true));
        assertEquals(Decision.CONSUME, decide(Action.PRESS, true, true, true));
        assertEquals(Decision.RELEASE, decide(Action.RELEASE, true, true, true));
    }
    @Test void heldChargeReleasesAfterScreenOrContextChanges() {
        assertEquals(Decision.RELEASE, decide(Action.RELEASE, true, false, false));
        assertEquals(Decision.PASS, decide(Action.PRESS, false, true, false));
        assertEquals(Decision.PASS, decide(Action.RELEASE, false, true, true));
    }
}
