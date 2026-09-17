package com.cappleapple.mastery.data;

import com.google.gson.JsonObject;

/** Higher priority matching contexts win; ID ordering breaks ties deterministically. */
public record ContextDefinition(String id, String name, int priority, JsonObject condition) {
    public ContextDefinition { condition = condition.deepCopy(); }
}
