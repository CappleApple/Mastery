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
    public static List<Component> tooltip(String id) {return tooltip(id,ClientState.progress());}
    /** Class selection uses projected rewards without mutating the live client state. */
    public static List<Component> tooltip(String id,com.cappleapple.mastery.progression.PlayerProgress progress) {
        var definitions = ClientState.definitions();
        var node = definitions.nodes().get(id);
        var tree = definitions.trees().get(node == null ? id : node.tree());
        var state = tree == null ? null : progress.trees().get(tree.id());
        int level = state == null ? 0 : state.level();
        if(node==null)return List.of(Component.literal(ClientState.name(id)),
                Component.literal("Level "+level+" / "+(tree==null?0:tree.levelCap(ClientState.worldTier()))),
                Component.literal("Points: "+(state==null?0:state.points())),
                Component.literal(String.format(Locale.ROOT,"XP %.0f / %.0f",state==null?0:state.xp(),tree==null?0:tree.xpForLevel(level))));
        var lines=new ArrayList<Component>();int rank=progress.rank(id);
        lines.add(Component.literal(ClientState.name(id)));lines.add(Component.literal("Rank "+rank+" / "+node.maxRank()));
        if(node.rootTree()!=null) {
            var root=definitions.trees().get(PromotedTrees.treeId(node));var rootState=progress.trees().get(root.id());
            lines.add(Component.literal("Tree level "+(rootState==null?0:rootState.level())+" / "+root.levelCap(ClientState.worldTier())+"; Points: "+(rootState==null?0:rootState.points())));
        }
        if(spells(node).isEmpty()||progress!=ClientState.progress())lines.addAll(bonuses(node,Math.max(1,rank)));
        else for(String spell:spells(node)){lines.addAll(SpellDetails.stats(spell));lines.addAll(SpellDetails.modifiers(spell));}
        if(com.cappleapple.mastery.progression.ProgressionService.purchaseBlockReason(definitions,progress,id,ClientState.worldTier(),requirement->true,false).isEmpty())
            lines.add(Component.literal("Next rank cost: "+costText(id)));
        return List.copyOf(lines);
    }
    /** Includes direct and reusable unlock rewards, in authored order without duplicates. */
    public static List<String> spells(NodeDefinition node) {
        var spells=new LinkedHashSet<String>();
        if(node==null||node.spellModifier())return List.of();
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
    static List<Component> bonuses(NodeDefinition node,int rank){
        var lines=new ArrayList<Component>();
        for(var raw:node.effects()) {
            JsonObject effect=resolveEffect(raw);
            if(effect==null)continue;
            if(value(effect,"type","").equals("mastery:unlock_spell"))continue;
            if(value(effect,"type","").equals("mastery:trigger")) {
                var trigger=ClientState.definitions().triggers().get(value(effect,"trigger",""));
                if(trigger!=null)lines.add(Component.literal(ScriptText.trigger(trigger)+(node.treeModifier()?ScriptText.damageScope(SettingsResolver.forNode(ClientState.definitions(),node.id()).getAsJsonObject("damage_filter")):"")));
                continue;
            }
            if(value(effect,"type","").equals("mastery:spell_modifier")&&!value(effect,"spell","").isBlank())
                lines.add(Component.literal("For "+ClientState.name(value(effect,"spell",""))+":"));
            for(String line:com.cappleapple.mastery.layout.BonusText.describe(effect,rank,id->{
                if(ClientState.definitions().keywords().containsKey(id))return ScriptText.keywordName(id);
                var attribute=net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.get(net.minecraft.resources.ResourceLocation.parse(id));
                return attribute==null?readable(id):Component.translatable(attribute.getDescriptionId()).getString();
            },id->net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.get(net.minecraft.resources.ResourceLocation.parse(id)) instanceof io.redspace.ironsspellbooks.api.attribute.MagicPercentAttribute||net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.get(net.minecraft.resources.ResourceLocation.parse(id)) instanceof net.neoforged.neoforge.common.PercentageAttribute))if(!line.contains("Modifier Slots")&&(!SpellDetails.hideMana()||!line.endsWith("Mana Cost")))lines.add(Component.literal(line));
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
            if(!tree.description().isBlank())lines.add(Component.literal(tree.description()));
            var state = ClientState.progress().trees().get(id);
            if (!ClientState.editMode() && state != null) {
                lines.add(Component.literal("Level " + state.level() + " / " + tree.levelCap(ClientState.worldTier())));
                lines.add(Component.literal(tree.name() + " Points: " + state.points()));
                lines.add(Component.literal(String.format(Locale.ROOT, "XP %.0f / %.0f", state.xp(), tree.xpForLevel(state.level()))));
            }
            if (tree.pointEvery() > 0) lines.add(Component.literal(tree.pointEvery() == 1 ? "Earn "+tree.pointsPerAward()+" point(s) per level." : "Earn "+tree.pointsPerAward()+" point(s) every " + tree.pointEvery() + " levels."));
            if(ClientState.editMode())lines.add(Component.literal("Default section: " + tree.section() + ". Right-click to edit this definition."));
        }
        if (node != null) {
            if(spells(node).isEmpty()&&bonuses(node,Math.max(1,ClientState.progress().rank(id))).isEmpty()&&!node.description().isBlank())lines.add(Component.literal(node.description()));
            if(node.rootTree()!=null) {
                var promoted=definitions.trees().get(PromotedTrees.treeId(node));var rootState=ClientState.progress().trees().get(promoted.id());
                int rootLevel=rootState==null?0:rootState.level();
                lines.add(Component.literal("Tree level "+rootLevel+" / "+promoted.levelCap(ClientState.worldTier())+"; Points: "+(rootState==null?0:rootState.points())));
                lines.add(Component.literal(String.format(Locale.ROOT,"Tree XP %.0f / %.0f",rootState==null?0:rootState.xp(),promoted.xpForLevel(rootLevel))));
            }
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
            if (rank == 0) {
                TreeDefinition owner = definitions.trees().get(node.tree());
                String treeName = owner == null ? node.tree() : owner.name();
                try {
                    lines.add(Component.literal("Cost: "+costText(node.id())));
                }catch(RuntimeException ex){lines.add(Component.literal("Invalid purchase costs: "+ex.getMessage()));}
                if (node.level() > 0) lines.add(Component.literal("Requires " + treeName + " level " + node.level()));
                if (!node.bookToken().isBlank()) lines.add(Component.literal("Requires a matching Skill Book."));
                if (node.worldTier() > 0) lines.add(Component.literal("Requires world tier " + node.worldTier()));
                for (Dependency dep : node.dependencies()) lines.add(Component.literal("Requires " + dep.describe(ClientState::name)));
                for (JsonObject requirement : node.requirements()) lines.add(Component.literal(requirement(requirement)));
            }
            if (!node.exclusions().isEmpty()) lines.add(Component.literal("Excludes: " + String.join(", ", node.exclusions().stream().map(ClientState::name).toList())));
            for(String spell:spells(node)) {
                lines.addAll(SpellDetails.stats(spell));
                lines.addAll(SpellDetails.modifiers(spell));
            }
            lines.addAll(bonuses(node,Math.max(1,rank)));

        }
        return List.copyOf(lines);
    }
    private static String costText(String nodeId) {
        return com.cappleapple.mastery.costs.CostResolver.forNode(ClientState.definitions(),nodeId).describe(id->{
            if(ClientState.definitions().trees().containsKey(id))return ClientState.name(id);
            var resource=net.minecraft.resources.ResourceLocation.tryParse(id);
            return resource!=null&&net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(resource)?net.minecraft.core.registries.BuiltInRegistries.ITEM.get(resource).getDescription().getString():id;
        });
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
    private static String readable(String id) {
        String text = id.substring(id.indexOf(':') + 1).replace('_', ' ').replace('.', ' ');
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
