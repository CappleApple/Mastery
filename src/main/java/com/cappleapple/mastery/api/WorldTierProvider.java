package com.cappleapple.mastery.api;

import net.minecraft.server.level.ServerLevel;

/** Called on the server thread. Return -1 to defer to the next provider or configured fallback. */
@FunctionalInterface
public interface WorldTierProvider {
    int getTier(ServerLevel level);
}
