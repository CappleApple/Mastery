package com.cappleapple.mastery.client.gui;

import com.cappleapple.mastery.client.ClientState;
import com.cappleapple.mastery.data.*;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;

import java.util.*;

/** Reusable content model for the optional side panel and hover tooltips. */
public final class NodeDetails {
    private NodeDetails() {}
    /** Hover information stays compact; descriptions and requirements belong in the side panel. */
    public static List<Component> tooltip(String id) {
        var definitions = ClientState.definitions();
        var node = definitions.nodes().get(id);
        var tree = definitions.trees().get(node == null ? id : node.tree());
        var state = tree == null ? null : ClientState.progress().trees().get(tree.id());
        int level = state == null ? 0 : state.level();
        if(node==null)return List.of(Component.literal(ClientState.name(id)),
                Component.literal("Level "+level+" / "+(tree==null?0:tree.levelCap(ClientState.worldTier()))),
                Component.literal("Points: "+(state==null?0:state.points())),
                Component.literal(String.format(Locale.ROOT,"XP %.0f / %.0f",state==null?0:state.xp(),tree==null?0:tree.xpForLevel(level))));
        var lines=new ArrayList<Component>();int rank=ClientState.progress().rank(id);
        lines.add(Component.literal(ClientState.name(id)));lines.add(Component.literal("Rank "+rank+" / "+node.maxRank()));
        lines.addAll(bonuses(node,Math.max(1,rank)));
        return List.copyOf(lines);
    }
    /** Includes direct and reusable unlock rewards, in authored order without duplicates. */
    public static List<String> spells(NodeDefinition node) {
        var spells=new LinkedHashSet<String>();
        if(node==null||!node.modifier().isBlank())return List.of();
        if(!node.spell().isBlank())spells.add(node.spell());
        for(var raw:node.effects()) {
            var effect=resolveEffect(raw);
            if(effect!=null&&value(effect,"type","").equals("mastery:unlock_spell")&&!value(effect,"spell","").isBlank())spells.add(value(effect,"spell",""));
        }
        return List.copyOf(spells);
    }
    private static JsonObject resolveEffect(JsonObject effect) {
        int depth=0;
        while(effect!=null&&effect.has("ref")&&depth++<32)effect=ClientState.definitions().effects().get(value(effect,"ref",""));
        return effect==null||effect.has("ref")?null:effect;
    }
    private static List<Component> bonuses(NodeDefinition node,int rank){
        var lines=new ArrayList<Component>();
        for(var raw:node.effects()) {
            JsonObject effect=resolveEffect(raw);
            if(effect==null)continue;
            if(value(effect,"type","").equals("mastery:unlock_spell")) {
                int level=(effect.has("level")?effect.get("level").getAsInt():1)+(effect.has("levels_per_rank")?effect.get("levels_per_rank").getAsInt():0)*Math.max(0,rank-1);
                var binding=ClientState.definitions().spells().get(value(effect,"spell",""));
                level=Math.clamp(Math.max(binding==null?1:binding.level(),level),1,255);
                lines.add(Component.literal("Unlocks "+ClientState.name(value(effect,"spell",""))+" (level "+level+")"));
                continue;
            }
            if(value(effect,"type","").equals("mastery:spell_modifier")&&!value(effect,"spell","").isBlank())
                lines.add(Component.literal("For "+ClientState.name(value(effect,"spell",""))+":"));
            for(String line:com.cappleapple.mastery.layout.BonusText.describe(effect,rank,id->{
                var attribute=net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.get(net.minecraft.resources.ResourceLocation.parse(id));
                return attribute==null?readable(id):Component.translatable(attribute.getDescriptionId()).getString();
            },id->net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.get(net.minecraft.resources.ResourceLocation.parse(id)) instanceof io.redspace.ironsspellbooks.api.attribute.MagicPercentAttribute||net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.get(net.minecraft.resources.ResourceLocation.parse(id)) instanceof net.neoforged.neoforge.common.PercentageAttribute))lines.add(Component.literal(line));
        }
        return lines;
    }
    public static List<Component> describe(String id) {
        var lines = new ArrayList<Component>();
        var definitions = ClientState.definitions();
        lines.add(Component.literal(ClientState.name(id)));
        TreeDefinition tree = definitions.trees().get(id);
        NodeDefinition node = definitions.nodes().get(id);
        if (tree != null) {
            lines.add(Component.literal(tree.description()));
            var state = ClientState.progress().trees().get(id);
            if (!ClientState.editMode() && state != null) {
                lines.add(Component.literal("Level " + state.level() + " / " + tree.levelCap(ClientState.worldTier())));
                lines.add(Component.literal(tree.name() + " Points: " + state.points()));
                lines.add(Component.literal(String.format(Locale.ROOT, "XP %.0f / %.0f", state.xp(), tree.xpForLevel(state.level()))));
            }
            if (tree.pointEvery() > 0) lines.add(Component.literal(tree.pointEvery() == 1 ? "Earn "+tree.pointsPerAward()+" point(s) per level." : "Earn "+tree.pointsPerAward()+" point(s) every " + tree.pointEvery() + " levels."));
            lines.add(Component.literal(ClientState.editMode()
                    ? "Default section: " + tree.section() + ". Right-click to edit this definition."
                    : "Drag to move this tree. Skills grow outward from the fixed map center in eight directions."));
        }
        if (node != null) {
            lines.add(Component.literal(node.description()));
            for(String spell:spells(node)) {
                    var nativeSpell=io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell(spell);
                    if(spells(node).size()>1)lines.add(Component.literal(ClientState.name(spell)));
                    var player=net.minecraft.client.Minecraft.getInstance().player;
                    String guide=nativeSpell.getComponentId()+".guide";
                    if(player!=null&&!nativeSpell.obfuscateStats(player)&&net.minecraft.client.resources.language.I18n.exists(guide))
                        lines.add(Component.translatable(guide).withStyle(net.minecraft.ChatFormatting.GRAY));
            }
            int rank = ClientState.progress().rank(id);
            lines.add(Component.literal("Rank " + rank + " / " + node.maxRank()));
            if(!ClientState.editMode()&&rank<node.maxRank())lines.add(Component.literal("Hold left click on the node to buy the next rank."));
            if(!ClientState.editMode()&&rank>0) {
                lines.add(Component.literal(ClientState.nodeEnabled(id)?"Enabled":"Disabled"));
                lines.add(Component.literal("Left-click for details. Right-click to expand or collapse. Middle-click to enable or disable."));
            }
            TreeDefinition owner = definitions.trees().get(node.tree());
            String treeName = owner == null ? node.tree() : owner.name();
            var state = ClientState.progress().trees().get(node.tree());
            try {
                var costs=com.cappleapple.mastery.costs.CostResolver.forNode(definitions,node.id());
                lines.add(Component.literal("Cost: "+costs.describe(costId->{
                    if(definitions.trees().containsKey(costId))return ClientState.name(costId);
                    var resource=net.minecraft.resources.ResourceLocation.parse(costId);
                    return net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(resource)?net.minecraft.core.registries.BuiltInRegistries.ITEM.get(resource).getDescription().getString():costId;
                })));
                var balances=com.cappleapple.mastery.costs.CostService.budget(ClientState.progress(),net.minecraft.client.Minecraft.getInstance().player);
                lines.add(Component.literal("Available: "+balances.experience()+" Minecraft XP; "+String.join(", ",balances.points().entrySet().stream().filter(e->e.getValue()>0).map(e->e.getValue()+" "+ClientState.name(e.getKey())+" Points").toList())));
            }catch(RuntimeException ex){lines.add(Component.literal("Invalid purchase costs: "+ex.getMessage()));}
            if (node.level() > 0) lines.add(Component.literal("Requires " + treeName + " level " + node.level()));
            if (!node.bookToken().isBlank()) lines.add(Component.literal("Requires a matching Skill Book."));
            if (node.worldTier() > 0) lines.add(Component.literal("Requires world tier " + node.worldTier()));
            for (Dependency dep : node.dependencies()) lines.add(Component.literal("Requires " + dep.describe(ClientState::name)));
            for (JsonObject requirement : node.requirements()) lines.add(Component.literal(requirement(requirement)));
            if (!node.exclusions().isEmpty()) lines.add(Component.literal("Excludes: " + String.join(", ", node.exclusions().stream().map(ClientState::name).toList())));
            for(String spell:spells(node)) {
                SpellDefinition ability = definitions.spells().get(spell);
                if (ability != null) {
                    lines.add(Component.literal((spells(node).size()>1?ClientState.name(spell)+" modifier slots: ":"Modifier slots: ") + (ClientState.runtime().has("modifier_limits")&&ClientState.runtime().getAsJsonObject("modifier_limits").has(ability.id())?ClientState.runtime().getAsJsonObject("modifier_limits").get(ability.id()).getAsInt():SettingsResolver.modifierSlots(SettingsResolver.forNode(definitions,node.id()),Math.max(1,rank)))));

                }
            }
            if(!spells(node).isEmpty()) {
                long equipped=ClientState.progress().loadouts().values().stream().flatMap(List::stream).filter(a->!a.isBlank()).count();
                lines.add(Component.literal("Assigned spells: "+equipped+" / "+ClientState.capacity()));
            }
            if (!node.effects().isEmpty()) {
                lines.add(Component.literal(rank < node.maxRank() ? "Next rank:" : "Effects:"));
                lines.addAll(bonuses(node,rank<node.maxRank()?rank+1:Math.max(1,rank)));
            }
            var entry = ClientState.entries().get(id);
            if (entry != null && entry.relatedTrees().size() > 1) lines.add(Component.literal("Connects: " + String.join(", ", entry.relatedTrees().stream().map(ClientState::name).toList())));
        }
        return List.copyOf(lines);
    }
    private static String value(JsonObject object, String key, String fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : fallback;
    }
    private static String requirement(JsonObject r) { return requirement(r, 0); }
    private static String requirement(JsonObject r, int depth) {
        if (depth > 32) return "Additional requirement";
        if (r.has("ref")) {
            JsonObject target = ClientState.definitions().requirements().get(value(r, "ref", ""));
            return target == null ? "Additional requirement" : requirement(target, depth + 1);
        }
        for (String operator : List.of("and", "or")) if (r.has(operator)) {
            List<String> parts = new ArrayList<>();
            r.getAsJsonArray(operator).forEach(child -> parts.add(requirement(child.getAsJsonObject(), depth + 1)));
            return String.join(" " + operator + " ", parts);
        }
        if (r.has("not")) return "Must not meet: " + requirement(r.getAsJsonObject("not"), depth + 1);
        if (r.has("description")) return value(r, "description", "");
        String type = value(r, "type", "requirement");
        if (r.has("tree")) return "Requires " + ClientState.name(value(r, "tree", "")) + " level " + value(r, "level", value(r, "value", "1"));
        if (r.has("node")) return "Requires " + ClientState.name(value(r, "node", "")) + " rank " + value(r, "rank", "1");
        return "Requires " + readable(type);
    }
    private static String effect(JsonObject e) { return effect(e, 0); }
    private static String effect(JsonObject e, int depth) {
        if (depth > 32) return "Additional effect";
        if (e.has("ref")) {
            JsonObject target = ClientState.definitions().effects().get(value(e, "ref", ""));
            return target == null ? "Additional effect" : effect(target, depth + 1);
        }
        if (e.has("description")) return value(e, "description", "");
        if (e.has("attribute")) return readable(value(e, "attribute", "")) + " " + value(e, "amount", "");
        if (e.has("spell")) return "Spell: " + ClientState.name(value(e, "spell", ""));
        return readable(value(e, "type", "effect")) + (e.has("amount") ? " +" + value(e, "amount", "") : "");
    }
    private static String readable(String id) {
        String text = id.substring(id.indexOf(':') + 1).replace('_', ' ').replace('.', ' ');
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
