package com.cappleapple.mastery.api;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/** Posted on the NeoForge game bus after an authoritative XP/level mutation, on the server thread. */
public final class ProficiencyChangedEvent extends Event {
    private final ServerPlayer player;
    private final String tree;
    private final int previousLevel;
    private final int level;
    public ProficiencyChangedEvent(ServerPlayer player, String tree, int previousLevel, int level) {
        this.player = player; this.tree = tree; this.previousLevel = previousLevel; this.level = level;
    }
    public ServerPlayer player() { return player; }
    public String tree() { return tree; }
    public int previousLevel() { return previousLevel; }
    public int level() { return level; }
}
