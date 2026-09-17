package com.cappleapple.mastery.requirements;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;

/** Server-side predicate input. Event fields are copied by the caller and never client-supplied. */
public record RequirementContext(ServerPlayer player, JsonObject event) {}
