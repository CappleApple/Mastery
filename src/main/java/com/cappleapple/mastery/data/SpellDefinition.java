package com.cappleapple.mastery.data;

import java.util.List;

/** A progression binding to an existing Iron's Spells registry entry; casting remains native. */
public record SpellDefinition(String id, String tree, String name, String description, String icon,
        List<String> contexts, int modifierSlots, int level, ChargeDefinition charge) {
    public SpellDefinition(String id,String tree,String name,String description,String icon,List<String> contexts,int modifierSlots,int level) {
        this(id,tree,name,description,icon,contexts,modifierSlots,level,ChargeDefinition.defaults(id));
    }
    public SpellDefinition {
        charge=charge==null?ChargeDefinition.defaults(id):charge;
        contexts = List.copyOf(contexts);
    }
}
