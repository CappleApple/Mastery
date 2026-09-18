package com.cappleapple.mastery.spells;

import java.util.*;

/** Pure slot validation used before every server-side loadout mutation. */
public final class LoadoutRules {
    public static final int SLOTS = BindingSlots.LIMIT;
    private LoadoutRules() {}
    public static int equipped(Map<String,List<String>> loadouts) {
        return loadouts.values().stream().mapToInt(slots -> (int)slots.stream().filter(id -> !id.isBlank()).count()).sum();
    }
    public static String validate(Map<String,List<String>> loadouts,String context,int slot,String spell,
                                  int capacity,boolean owned,boolean allowed) {
        if (slot < 0 || slot >= BindingSlots.limit(context)) return "Invalid spell slot";
        if (spell.isBlank()) return "";
        if(slot>=Math.max(0,capacity))return "No spell slot available";
        if (!owned) return "Spell has not been unlocked";
        if (!allowed) return "Spell cannot be bound here";
        List<String> slots = loadouts.getOrDefault(context, List.of("","","",""));
        for (int i=0;i<slots.size();i++) if (i!=slot && spell.equals(slots.get(i))) return "Spell is already bound in this set";
        int previous = slot < slots.size() && !slots.get(slot).isBlank() ? 1 : 0;
        if (equipped(loadouts) - previous + 1 > Math.max(0,capacity)) return "Global active capacity exceeded";
        return "";
    }
    public static String modifier(int count,int limit,boolean owned,boolean related,boolean alreadyEnabled) {
        if (!owned) return "Modifier has not been purchased";
        if (!related) return "Modifier does not belong to this spell";
        if (!alreadyEnabled && count>=Math.max(0,limit)) return "No modifier slots available";
        return "";
    }
}
