package com.cappleapple.mastery.api.spell;

import com.cappleapple.mastery.spells.SpellModifiers;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;

/** Adjusts an existing Iron spell through native level, mana, cooldown and cast-time contracts. Server thread only. */
@FunctionalInterface
public interface SpellModifier {
    void apply(ServerPlayer player,String spell,int effectiveRank,JsonObject parameters,SpellModifiers result);
}
