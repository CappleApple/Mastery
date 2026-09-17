package com.cappleapple.mastery.data;

import com.google.gson.JsonObject;

/** Usage source; scale selects a numeric event field, or "none" for a fixed grant. */
public record XpSourceDefinition(String id, String tree, String event, double amount, String scale,
        JsonObject condition, int points, boolean once) {
    public XpSourceDefinition { condition = condition.deepCopy(); }
}
