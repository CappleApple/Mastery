package com.cappleapple.mastery.api;

import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Register contextual equipment classifiers and additive spell-capacity sources during common setup. */
public final class SpellEquipmentRegistry {
    public static final Map<String,SpellCapacitySource> CAPACITY=new LinkedHashMap<>();
    public static final NavigableMap<Integer,List<CombatContextProvider>> CONTEXTS=new TreeMap<>(Comparator.reverseOrder());
    private SpellEquipmentRegistry() {}
    public static synchronized void registerCapacity(String id,SpellCapacitySource source) {
        if(ResourceLocation.tryParse(id)==null) throw new IllegalArgumentException("Invalid capacity provider ID: "+id);
        if(CAPACITY.putIfAbsent(id,Objects.requireNonNull(source))!=null) throw new IllegalArgumentException("Duplicate capacity provider: "+id);
    }
    public static synchronized void registerContext(int priority,CombatContextProvider provider) {
        CONTEXTS.computeIfAbsent(priority,ignored -> new ArrayList<>()).add(Objects.requireNonNull(provider));
    }
}
