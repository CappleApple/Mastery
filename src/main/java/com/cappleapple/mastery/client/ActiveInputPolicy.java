package com.cappleapple.mastery.client;

/** Pure routing policy, shared by keyboard and mouse interception. */
public final class ActiveInputPolicy {
    public enum Action { PRESS, RELEASE, REPEAT }
    public enum Decision { PASS, START, RELEASE, CONSUME }
    private ActiveInputPolicy() {}

    /** A release for an already-held action remains owned even after its context disappears. */
    public static Decision decide(Action action, boolean held, boolean bound, boolean gameplayFocused) {
        if (action == Action.RELEASE && held) return Decision.RELEASE;
        if (!gameplayFocused || !bound) return Decision.PASS;
        if (action == Action.PRESS) return held ? Decision.CONSUME : Decision.START;
        return action == Action.REPEAT ? Decision.CONSUME : Decision.PASS;
    }
}
