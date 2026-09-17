package com.cappleapple.mastery.data;

/** An organizational branch. A tree may use the same parent as a sibling group. */
public record GroupDefinition(String id, String name, String description, String icon, String parent) {}
