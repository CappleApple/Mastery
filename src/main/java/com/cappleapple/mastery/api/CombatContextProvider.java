package com.cappleapple.mastery.api;

import net.minecraft.world.entity.player.Player;
import java.util.Optional;

/** Returns a namespaced context ID, or empty when this provider does not classify the equipment. */
@FunctionalInterface
public interface CombatContextProvider {
    Optional<String> context(Player player);
}
