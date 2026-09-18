package com.cappleapple.mastery.client.gui;

import com.cappleapple.mastery.client.ClientState;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.util.TooltipsUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Current native spell data shared by the details panel and node tooltips. */
public final class SpellDetails {
    private SpellDetails() {}
    private static final java.lang.reflect.Method TEMPO_MANA_DISABLED=tempoManaMethod();
    private static java.lang.reflect.Method tempoManaMethod() {
        try {return Class.forName("com.cappleapple.temponottime.network.ClientCooldownState").getMethod("manaDisabled");}
        catch(ReflectiveOperationException|LinkageError ignored){return null;}
    }
    public static boolean hideMana() {
        try {return TEMPO_MANA_DISABLED!=null&&(boolean)TEMPO_MANA_DISABLED.invoke(null);}
        catch(ReflectiveOperationException ignored){return false;}
    }
    public static int level(String id) {
        var levels=ClientState.runtime().getAsJsonObject("spell_levels");
        var definition=ClientState.definitions().spells().get(id);
        return levels!=null&&levels.has(id)?levels.get(id).getAsInt():definition==null?1:definition.level();
    }
    public static List<Component> stats(String id) {
        var player=Minecraft.getInstance().player;var spell=SpellRegistry.getSpell(id);
        if(player==null||spell.obfuscateStats(player))return List.of();
        int level=level(id);var result=new ArrayList<Component>();
        result.add(Component.translatable("tooltip.irons_spellbooks.level",level));
        result.addAll(spell.getUniqueInfo(level,player));
        var all=ClientState.runtime().getAsJsonObject("spell_stats");
        var stats=all!=null?all.getAsJsonObject(id):null;
        int mana=stats!=null?stats.get("mana").getAsInt():spell.getManaCost(level);
        int cast=stats!=null?stats.get("cast_ticks").getAsInt():spell.getEffectiveCastTime(level,player);
        int cooldown=stats!=null?stats.get("cooldown_ticks").getAsInt():io.redspace.ironsspellbooks.capabilities.magic.MagicManager.getEffectiveSpellCooldown(spell,player,io.redspace.ironsspellbooks.api.spells.CastSource.SPELLBOOK);
        if(spell.getCastType()!=CastType.INSTANT)result.add(TooltipsUtils.getCastTimeComponent(spell.getCastType(),io.redspace.ironsspellbooks.api.util.Utils.timeFromTicks(cast,2)));
        if(mana>0&&!hideMana())result.add(TooltipsUtils.getManaCostComponent(spell.getCastType(),mana));
        if(cooldown>0)result.add(Component.translatable("tooltip.irons_spellbooks.cooldown_length_seconds",io.redspace.ironsspellbooks.api.util.Utils.timeFromTicks(cooldown,2)));
        var charges=ClientState.runtime().getAsJsonObject("spell_charges");
        if(charges!=null&&charges.has(id))result.add(Component.translatable("mastery.spell.charges",charges.get(id).getAsInt()));
        return List.copyOf(result);
    }
    public static List<Component> modifiers(String spell) {
        var all=ClientState.runtime().getAsJsonObject("equipped_modifiers");
        var result=new ArrayList<Component>();
        var ids=new LinkedHashSet<String>();
        if(all!=null&&all.has(spell))for(var value:all.getAsJsonArray(spell))ids.add(value.getAsString());
        var player=Minecraft.getInstance().player;
        if(player!=null) {
            var damage=com.cappleapple.mastery.mechanics.DamageContexts.capture(SpellRegistry.getSpell(spell).getDamageSource(player));
            for(var node:ClientState.definitions().nodes().values())if(node.treeModifier()&&ClientState.nodeEnabled(node.id())
                    &&com.cappleapple.mastery.progression.ProgressionService.cappedRank(ClientState.definitions(),ClientState.progress(),node.id(),ClientState.worldTier())>0
                    &&com.cappleapple.mastery.mechanics.TreeModifiers.matches(ClientState.definitions(),node,damage))ids.add(node.id());
        }
        for(var id:ids) {
            var node=ClientState.definitions().nodes().get(id);if(node==null)continue;
            var bonuses=NodeDetails.bonuses(node,ClientState.progress().rank(node.id()));
            if(bonuses.isEmpty())result.add(Component.literal(node.name()));
            result.addAll(bonuses.stream().filter(line->!line.getString().startsWith("For ")).toList());
        }
        return List.copyOf(result);
    }
}
