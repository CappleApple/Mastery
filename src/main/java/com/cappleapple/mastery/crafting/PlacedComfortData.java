package com.cappleapple.mastery.crafting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Dimension-local placement bonuses, indexed by chunk and never copied onto block drops. */
public final class PlacedComfortData extends SavedData {
    public record Bonus(BlockPos position,String block,String id,String type,double amount,double radius) {}
    private final Map<Long,Map<Long,List<Bonus>>> chunks=new HashMap<>();
    public static PlacedComfortData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(PlacedComfortData::new,PlacedComfortData::load,null),"mastery_placed_comfort");
    }
    public void put(BlockPos position,List<Bonus> bonuses) {
        remove(position);
        if(bonuses.isEmpty())return;
        chunks.computeIfAbsent(ChunkPos.asLong(position),ignored->new HashMap<>()).put(position.asLong(),List.copyOf(bonuses));
        setDirty();
    }
    public void remove(BlockPos position) {
        long key=ChunkPos.asLong(position);var chunk=chunks.get(key);
        if(chunk!=null&&chunk.remove(position.asLong())!=null){if(chunk.isEmpty())chunks.remove(key);setDirty();}
    }
    public List<Bonus> nearby(ServerPlayer player) {
        List<Bonus> result=new ArrayList<>();List<BlockPos> stale=new ArrayList<>();
        var center=player.blockPosition();var level=player.serverLevel();
        for(int x=(center.getX()-64)>>4;x<=(center.getX()+64)>>4;x++)for(int z=(center.getZ()-64)>>4;z<=(center.getZ()+64)>>4;z++) {
            var chunk=chunks.get(ChunkPos.asLong(x,z));if(chunk==null)continue;
            for(var bonuses:chunk.values())for(var bonus:bonuses) {
                if(!level.hasChunkAt(bonus.position()))continue;
                if(!BuiltInRegistries.BLOCK.getKey(level.getBlockState(bonus.position()).getBlock()).toString().equals(bonus.block())){stale.add(bonus.position());continue;}
                if(player.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(bonus.position()))<=bonus.radius()*bonus.radius())result.add(bonus);
            }
        }
        stale.forEach(this::remove);
        return result;
    }
    public static PlacedComfortData load(CompoundTag tag,HolderLookup.Provider registries) {
        var result=new PlacedComfortData();
        for(Tag raw:tag.getList("bonuses",Tag.TAG_COMPOUND)) {
            var value=(CompoundTag)raw;var position=BlockPos.of(value.getLong("position"));
            double amount=value.getDouble("amount"),radius=value.getDouble("radius");
            if(!Double.isFinite(amount)||amount<=0||!Double.isFinite(radius)||radius<1||radius>64)continue;
            if(net.minecraft.resources.ResourceLocation.tryParse(value.getString("block"))==null||net.minecraft.resources.ResourceLocation.tryParse(value.getString("id"))==null||value.getString("type").isBlank())continue;
            var bonus=new Bonus(position,value.getString("block"),value.getString("id"),value.getString("type"),amount,radius);
            result.chunks.computeIfAbsent(ChunkPos.asLong(position),ignored->new HashMap<>()).computeIfAbsent(position.asLong(),ignored->new ArrayList<>()).add(bonus);
        }
        return result;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries) {
        var values=new ListTag();
        chunks.values().forEach(chunk->chunk.values().forEach(bonuses->bonuses.forEach(bonus->{
            var value=new CompoundTag();value.putLong("position",bonus.position().asLong());value.putString("block",bonus.block());
            value.putString("id",bonus.id());value.putString("type",bonus.type());value.putDouble("amount",bonus.amount());value.putDouble("radius",bonus.radius());values.add(value);
        })));
        tag.put("bonuses",values);return tag;
    }
}
