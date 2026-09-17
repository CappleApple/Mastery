package com.cappleapple.mastery.api;

import net.minecraft.server.level.ServerPlayer;

/** Adds equipped spell slots. Evaluated on the server; return zero for absent equipment. */
@FunctionalInterface
public interface SpellCapacitySource {
    int capacity(ServerPlayer player);
}
