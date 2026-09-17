package com.cappleapple.mastery.storage;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.UUID;

/** A world-local public identifier. Contains no seed, server address, or authentication data. */
public final class WorldIdentity extends SavedData {
    private final String id;
    public WorldIdentity(){this(UUID.randomUUID().toString());setDirty();}
    private WorldIdentity(String id){this.id=id;}
    public static String get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(WorldIdentity::new,(tag,lookup)->new WorldIdentity(tag.getString("id")),null),"mastery_world").id;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider lookup){tag.putString("id",id);return tag;}
}
