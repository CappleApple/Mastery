package com.cappleapple.mastery.spells;

import java.util.*;

/** Two layouts share the same capacity policy. Only the selected mode is active. */
public final class BindingSlots {
    public static final String QUICK="mastery:quick_cast";
    public static final int LIMIT=4096, PAGE_SIZE=4;
    private BindingSlots() {}
    public static String hotbar(int index) {return "mastery:hotbar/"+Math.clamp(index,0,8);}
    public static boolean valid(String id){return id.equals(QUICK)||id.matches("mastery:hotbar/[0-8]");}
    public static boolean inMode(String id,String mode){return mode.equals("quick_cast")?id.equals(QUICK):id.matches("mastery:hotbar/[0-8]");}
    public static Map<String,List<String>> modeLoadouts(Map<String,List<String>> loadouts,String mode) {
        Map<String,List<String>> result=new TreeMap<>();
        loadouts.forEach((id,slots)->{if(inMode(id,mode))result.put(id,slots);});return result;
    }
    public static String get(Map<String,List<String>> loadouts,String context,int slot) {
        var slots=loadouts.get(context);return slots!=null&&slot>=0&&slot<slots.size()?slots.get(slot):"";
    }
    /** Visible slots are occupied positions plus only as many empty positions as capacity allows. */
    public static List<Integer> visible(Map<String,List<String>> loadouts,String mode,String context,int capacity) {
        int free=Math.max(0,capacity-LoadoutRules.equipped(modeLoadouts(loadouts,mode)));
        var slots=loadouts.getOrDefault(context,List.of());List<Integer> result=new ArrayList<>();
        int bound=0;for(String id:slots)if(!id.isBlank())bound++;
        int wanted=Math.min(LIMIT,bound+free);
        for(int index=0;index<LIMIT&&result.size()<wanted;index++)
            if(index<slots.size()&&!slots.get(index).isBlank()||free>0){result.add(index);if(index>=slots.size()||slots.get(index).isBlank())free--;}
        return result;
    }
}
