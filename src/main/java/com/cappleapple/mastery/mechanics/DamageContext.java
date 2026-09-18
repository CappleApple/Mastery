package com.cappleapple.mastery.mechanics;

import com.google.gson.JsonObject;
import java.util.*;

/** Immutable damage facts captured at impact, before the queued trigger runs. */
public record DamageContext(Set<String> categories,Set<String> elements,Set<String> damageTypes,Set<String> damageTags,String spell,UUID caster) {
    public static final Set<String> CATEGORIES=Set.of("melee","ranged","magic","physical","elemental");
    public static final List<String> FIELDS=List.of("categories","elements","damage_types","damage_tags");
    public static final DamageContext EMPTY=new DamageContext(Set.of(),Set.of(),Set.of(),Set.of());
    public DamageContext(Set<String> categories,Set<String> elements,Set<String> damageTypes,Set<String> damageTags) {this(categories,elements,damageTypes,damageTags,"",null);}
    public DamageContext {categories=Set.copyOf(categories);elements=Set.copyOf(elements);damageTypes=Set.copyOf(damageTypes);damageTags=Set.copyOf(damageTags);}
    public DamageContext merge(DamageContext other) {
        // A mixed weapon hit retains attribution only when its portions agree on the spell owner.
        boolean same=spell.equals(other.spell)&&Objects.equals(caster,other.caster);
        return new DamageContext(union(categories,other.categories),union(elements,other.elements),union(damageTypes,other.damageTypes),union(damageTags,other.damageTags),
                spell.isEmpty()?other.spell:other.spell.isEmpty()||same?spell:"",spell.isEmpty()?other.caster:other.spell.isEmpty()||same?caster:null);
    }
    private static Set<String> union(Set<String> a,Set<String> b){var result=new HashSet<>(a);result.addAll(b);return result;}
    /** Alternatives within a field; all populated fields must match. Empty contexts never match. */
    public boolean matches(JsonObject condition) {
        return !damageTypes.isEmpty()&&matches(condition,"categories",categories)&&matches(condition,"elements",elements)
                &&matches(condition,"damage_types",damageTypes)&&matches(condition,"damage_tags",damageTags);
    }
    private static boolean matches(JsonObject condition,String field,Set<String> actual) {
        if(!condition.has(field)||condition.getAsJsonArray(field).isEmpty())return true;
        for(var value:condition.getAsJsonArray(field))if(actual.contains(value.getAsString()))return true;
        return false;
    }
}
