package com.cappleapple.mastery.progression;

import com.cappleapple.mastery.data.*;
import com.cappleapple.mastery.graph.GraphValidator;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Pure transactional progression. The runtime dispatches events/effects only after successful changes. */
public final class ProgressionService {
    private ProgressionService() {}
    @FunctionalInterface public interface RequirementEvaluator { boolean test(JsonObject requirement); }
    @FunctionalInterface public interface CostPayer { Change pay(NodeDefinition node); }
    public record Change(boolean success, String message, int levels, int points) {
        public static Change failure(String message) { return new Change(false, message, 0, 0); }
        public static Change success(String message) { return new Change(true, message, 0, 0); }
    }

    public static Change addXp(DefinitionSet definitions, PlayerProgress player, String treeId, double amount, int worldTier) {
        TreeDefinition definition = definitions.trees().get(treeId);
        if (definition == null) return Change.failure("Unknown tree: " + treeId);
        if (!Double.isFinite(amount) || amount < 0) return Change.failure("XP must be finite and nonnegative");
        if (!PromotedTrees.unlocked(definitions,player,treeId,worldTier)) return Change.failure("Unlock the tree root before earning its XP");
        TreeProgress existing = player.trees().get(treeId);
        int oldLevel = existing == null ? 0 : existing.level();
        int highest = existing == null ? 0 : existing.highestLevel();
        int oldPoints = existing == null ? 0 : existing.points();
        int level = oldLevel;
        int cap = definition.levelCap(worldTier);
        double xp = existing == null ? 0 : existing.xp();
        int awarded;
        try {
            if (level < cap) {
                xp += amount;
                if (!Double.isFinite(xp)) return Change.failure("XP total exceeds the supported range");
                int low=0,high=cap-level;
                while(low<high) {
                    int count=low+(int)(((long)high-low+1)/2);
                    double cost=count*definition.xpForLevel(level)+definition.xpGrowth()*count*(count-1.0)/2;
                    if(cost<=xp)low=count;else high=count-1;
                }
                double spent=low*definition.xpForLevel(level)+definition.xpGrowth()*low*(low-1.0)/2;
                xp=Math.max(0,xp-spent);level+=low;
                if (level >= cap) xp = 0; // World caps never bank an unlimited burst for the next tier.
            }
            awarded = pointsBetween(definition, highest, Math.max(highest, level));
            Math.addExact(oldPoints, awarded);
        } catch (ArithmeticException | IllegalArgumentException ex) { return Change.failure("Point rules: " + ex.getMessage()); }
        TreeProgress state = player.tree(treeId);
        state.xp(xp);
        state.level(level);
        state.highestLevel(Math.max(highest, level));
        state.points(oldPoints + awarded);
        state.lifetimeXp(Math.min(Double.MAX_VALUE, state.lifetimeXp() + amount));
        return new Change(true, "Granted " + amount + " XP to " + treeId, level - oldLevel, awarded);
    }

    public static Change grantPoints(DefinitionSet definitions, PlayerProgress player, String treeId, int amount) {
        if (!definitions.trees().containsKey(treeId)) return Change.failure("Unknown tree: " + treeId);
        if (amount < 0) return Change.failure("Point grants must be nonnegative");
        TreeProgress existing = player.trees().get(treeId);
        int points = existing == null ? 0 : existing.points();
        try { points = Math.addExact(points, amount); }
        catch (ArithmeticException ex) { return Change.failure("Point balance exceeds 2147483647"); }
        player.tree(treeId).points(points);
        return new Change(true, "Granted " + amount + " points to " + treeId, 0, amount);
    }

    /** Administrative level changes do not award a previously reached milestone twice. */
    public static Change setLevel(DefinitionSet definitions, PlayerProgress player, String treeId, int target, int worldTier) {
        TreeDefinition definition = definitions.trees().get(treeId);
        if (definition == null) return Change.failure("Unknown tree: " + treeId);
        if (target < 0 || target > definition.levelCap(worldTier)) return Change.failure("Level must be between 0 and " + definition.levelCap(worldTier));
        TreeProgress existing = player.trees().get(treeId);
        int previous = existing == null ? 0 : existing.level();
        int highest = existing == null ? 0 : existing.highestLevel();
        int balance = existing == null ? 0 : existing.points();
        int awarded;
        try {
            awarded = pointsBetween(definition, highest, Math.max(highest, target));
            Math.addExact(balance, awarded);
        } catch (ArithmeticException | IllegalArgumentException ex) { return Change.failure("Point rules: " + ex.getMessage()); }
        TreeProgress tree = player.tree(treeId);
        tree.level(target);
        tree.xp(0);
        tree.highestLevel(Math.max(highest, target));
        tree.points(balance + awarded);
        return new Change(true, "Set " + treeId + " to level " + target, target - previous, awarded);
    }

    public static Change purchase(DefinitionSet definitions, PlayerProgress player, String nodeId, int worldTier, RequirementEvaluator evaluator) {
        return purchase(definitions,player,nodeId,worldTier,evaluator,node->{
            try {
                var resolved=com.cappleapple.mastery.costs.CostResolver.forNode(definitions,nodeId);
                var plan=resolved.plan(com.cappleapple.mastery.costs.CostResolver.pointsBudget(definitions,player,worldTier,evaluator));
                if(plan.isEmpty())return Change.failure("Not enough resources: "+resolved.describe(id->id));
                int spent=(int)Math.min(Integer.MAX_VALUE,plan.get().points().values().stream().mapToLong(Integer::longValue).sum());
                plan.get().points().forEach((tree,amount)->player.tree(tree).points(player.tree(tree).points()-amount));
                return new Change(true,"Paid purchase costs",0,-spent);
            }catch(RuntimeException ex){return Change.failure("Could not resolve purchase costs: "+ex.getMessage());}
        });
    }
    /** Prerequisites and chronology are validated before a payer can debit any resource. */
    public static Change purchase(DefinitionSet definitions, PlayerProgress player, String nodeId, int worldTier, RequirementEvaluator evaluator, CostPayer payer) {
        String blocked=purchaseBlockReason(definitions,player,nodeId,worldTier,evaluator,false);
        if(!blocked.isEmpty())return Change.failure(blocked);
        NodeDefinition definition=definitions.nodes().get(nodeId);
        NodeProgress previous=player.nodes().get(nodeId);
        int stamps=(previous==null||previous.unlockOrder()==0?1:0)+(previous==null||previous.purchaseOrder()==0?1:0);
        if(!player.canAdvanceSequence(stamps))return Change.failure("Mastery chronology exhausted");
        Change payment=payer.pay(definition);
        if(!payment.success())return payment;
        TreeProgress tree=player.tree(definition.tree());NodeProgress node=player.node(nodeId);
        if(node.unlockOrder()==0)node.unlockOrder(player.nextSequence());
        if(node.purchaseOrder()==0)node.purchaseOrder(player.nextSequence());
        tree.discovered(true);node.rank(node.rank()+1);
        if(node.rank()==1)node.toggled(true);
        if(definition.rootTree()!=null)player.tree(PromotedTrees.treeId(definition)).discovered(true);
        return new Change(true,"Purchased "+nodeId+" rank "+node.rank(),0,payment.points());
    }
    /** Empty means allowed. This method never creates or mutates player state. */
    public static String purchaseBlockReason(DefinitionSet definitions, PlayerProgress player, String nodeId, int worldTier,
            RequirementEvaluator evaluator, boolean checkPoints) {
        NodeDefinition node = definitions.nodes().get(nodeId);
        if (node == null) return "Unknown node: " + nodeId;
        if (!bookUnlocked(definitions, player, nodeId)) return "Requires a skill book unlock";
        TreeDefinition tree = definitions.trees().get(node.tree());
        if (tree == null) return "Unknown tree: " + node.tree();
        if (!PromotedTrees.unlocked(definitions,player,node.tree(),worldTier,evaluator)) return "Unlock the tree root first";
        int rank = player.rank(nodeId);
        if (rank >= node.maxRank()) return "Maximum rank reached";
        TierCap caps = tree.capAt(worldTier);
        if (caps.maxRank() >= 0 && rank + 1 > caps.maxRank()) return "World tier limits this node's rank";
        if (worldTier >= 0 && worldTier < node.worldTier()) return "Requires world tier " + node.worldTier();
        if (caps.maxDepth() >= 0 && GraphValidator.depthOf(definitions, nodeId) > caps.maxDepth())
            return "World tier limits this dependency depth";
        TreeProgress progression = player.trees().get(node.tree());
        int level = progression == null ? 0 : progression.level();
        if (Math.min(level, tree.levelCap(worldTier)) < node.level()) return "Requires " + node.tree() + " level " + node.level();
        for(Dependency dependency:node.dependencies())
            if(!dependency.test(leaf->cappedRank(definitions,player,leaf.node(),worldTier)>=leaf.rank()))return "Requires "+dependency.describe(id->id);
        for (String exclusion : node.exclusions()) if (player.rank(exclusion) > 0) return "Mutually exclusive with " + exclusion;
        for (var entry : player.nodes().entrySet()) {
            NodeDefinition other = definitions.nodes().get(entry.getKey());
            if (entry.getValue().rank() > 0 && other != null && other.exclusions().contains(nodeId)) return "Mutually exclusive with " + other.id();
        }
        try {
            for (JsonObject requirement : node.requirements()) if (!evaluator.test(requirement)) return "An external requirement is not met";
        } catch (RuntimeException ex) { return "Requirement evaluation failed: " + ex.getMessage(); }
        if(checkPoints)try {
            var cost=com.cappleapple.mastery.costs.CostResolver.forNode(definitions,nodeId);
            if(cost.plan(com.cappleapple.mastery.costs.CostResolver.pointsBudget(definitions,player,worldTier,evaluator)).isEmpty())return "Not enough resources: "+cost.describe(id->id);
        }catch(RuntimeException ex){return "Could not resolve purchase costs: "+ex.getMessage();}
        return "";
    }

    /** A book gate applies to the node and its dependency descendants, without granting ranks or points. */
    public static boolean bookUnlocked(DefinitionSet definitions, PlayerProgress player, String nodeId) {
        var memo=new java.util.HashMap<String,Boolean>();var pending=new java.util.ArrayDeque<String>();var visiting=new java.util.HashSet<String>();pending.push(nodeId);
        while(!pending.isEmpty()) {
            String id=pending.peek();var node=definitions.nodes().get(id);
            if(node==null||!node.bookToken().isEmpty()&&!player.bookUnlocks().contains(node.bookToken())){memo.put(id,false);pending.pop();visiting.remove(id);continue;}
            visiting.add(id);boolean waiting=false;
            for(var leaf:node.dependencyLeaves())if(!memo.containsKey(leaf.node())) {
                if(visiting.contains(leaf.node()))return false;
                pending.push(leaf.node());waiting=true;break;
            }
            if(waiting)continue;
            memo.put(id,node.dependenciesMet(leaf->memo.getOrDefault(leaf.node(),false)));pending.pop();visiting.remove(id);
        }
        return memo.getOrDefault(nodeId,false);
    }

    /** Purchased rank available under the node's own tree, level, rank and depth caps. */
    public static int cappedRank(DefinitionSet definitions, PlayerProgress player, String nodeId, int worldTier) {
        NodeDefinition node = definitions.nodes().get(nodeId);
        if (node == null || !bookUnlocked(definitions, player, nodeId) || !PromotedTrees.unlocked(definitions,player,node.tree(),worldTier)) return 0;
        TreeDefinition tree = definitions.trees().get(node.tree());
        TreeProgress state = player.trees().get(node.tree());
        if (tree == null || state == null || worldTier >= 0 && worldTier < node.worldTier()
                || Math.min(state.level(), tree.levelCap(worldTier)) < node.level()) return 0;
        TierCap cap = tree.capAt(worldTier);
        if (cap.maxDepth() >= 0 && GraphValidator.depthOf(definitions, nodeId) > cap.maxDepth()) return 0;
        int rank = Math.min(node.maxRank(), player.rank(nodeId));
        return cap.maxRank() < 0 ? rank : Math.min(rank, cap.maxRank());
    }

    /** Records the first moment a node becomes eligible, independent of its available point balance. */
    public static void refreshUnlocks(DefinitionSet definitions, PlayerProgress player, int worldTier, RequirementEvaluator evaluator) {
        definitions.nodes().values().stream().sorted(Comparator.comparing(NodeDefinition::id)).forEach(node -> {
            TreeProgress tree = player.trees().get(node.tree());
            NodeProgress progress = player.nodes().get(node.id());
            if (tree != null && tree.discovered() && (progress == null || progress.unlockOrder() == 0)
                    && purchaseBlockReason(definitions, player, node.id(), worldTier, evaluator, false).isEmpty())
                player.node(node.id()).unlockOrder(player.nextSequence());
        });
    }

    /** Administrative rank assignment bypasses prices/prerequisites, while respecting definition/tier maxima. */
    public static Change setRank(DefinitionSet definitions, PlayerProgress player, String nodeId, int rank, int worldTier) {
        NodeDefinition node = definitions.nodes().get(nodeId);
        if (node == null) return Change.failure("Unknown node: " + nodeId);
        TreeDefinition tree = definitions.trees().get(node.tree());
        if (tree == null) return Change.failure("Unknown tree: " + node.tree());
        int cap = tree.capAt(worldTier).maxRank();
        int maximum = cap < 0 ? node.maxRank() : Math.min(cap, node.maxRank());
        if (rank < 0 || rank > maximum) return Change.failure("Rank must be between 0 and " + maximum);
        if (rank > 0 && worldTier >= 0 && worldTier < node.worldTier()) return Change.failure("Requires world tier " + node.worldTier());
        NodeProgress state = player.node(nodeId);
        if(state.rank()==0&&rank>0)state.toggled(true);
        state.rank(rank);
        if (rank > 0) {
            if (state.unlockOrder() == 0) state.unlockOrder(player.nextSequence());
            if (state.purchaseOrder() == 0) state.purchaseOrder(player.nextSequence());
            player.tree(node.tree()).discovered(true);
            if(node.rootTree()!=null)player.tree(PromotedTrees.treeId(node)).discovered(true);
        }
        reconcile(definitions, player);
        return Change.success("Set " + nodeId + " to rank " + rank);
    }

    public static Change resetTree(DefinitionSet definitions, PlayerProgress player, String treeId) {
        if (!definitions.trees().containsKey(treeId)) return Change.failure("Unknown tree: " + treeId);
        player.trees().remove(treeId);
        player.nodes().keySet().removeIf(id -> definitions.nodes().containsKey(id) && definitions.nodes().get(id).tree().equals(treeId));
        player.usageGrants().removeIf(id -> definitions.xpSources().containsKey(id) && definitions.xpSources().get(id).tree().equals(treeId));
        reconcile(definitions, player);
        return Change.success("Reset " + treeId);
    }

    public static Change resetAll(PlayerProgress player) { player.clear(); return Change.success("Reset all Mastery progression"); }

    /** Discards orphan mechanics after a reload; retained tree history allows restored datapacks to recover XP. */
    public static void reconcile(DefinitionSet definitions, PlayerProgress player) {
        player.trees().forEach((id,state)->{var tree=definitions.trees().get(id);if(tree!=null&&state.level()>=tree.maxLevel()){state.level(tree.maxLevel());state.xp(0);}});
        player.nodes().entrySet().removeIf(entry -> !definitions.nodes().containsKey(entry.getKey()));
        player.nodes().forEach((id, progress) -> {
            NodeDefinition node = definitions.nodes().get(id);
            progress.rank(Math.min(node.maxRank(), progress.rank()));
            if (progress.rank() > 0) {
                player.tree(node.tree()).discovered(true);
                if(node.rootTree()!=null)player.tree(PromotedTrees.treeId(node)).discovered(true);
            }
        });
        migrateBindings(player);
        player.loadouts().entrySet().removeIf(entry -> !com.cappleapple.mastery.spells.BindingSlots.valid(entry.getKey()));
        player.loadouts().forEach((context, slots) -> {
            while (slots.size() < 4) slots.add("");
            while (slots.size() > com.cappleapple.mastery.spells.BindingSlots.limit(context)) slots.removeLast();
            for (int index = 0; index < slots.size(); index++) {
                String id = slots.get(index);
                SpellDefinition spell = definitions.spells().get(id);
                if (spell == null || !ownsSpell(definitions, player, id)) slots.set(index, "");
            }
        });
        player.loadouts().entrySet().removeIf(entry->entry.getValue().stream().allMatch(String::isBlank));
        player.activeModifiers().entrySet().removeIf(entry -> !definitions.spells().containsKey(entry.getKey()) || !ownsSpell(definitions, player, entry.getKey()));
        player.activeModifiers().forEach((spell, modifiers) -> modifiers.removeIf(id -> {
            NodeDefinition node = definitions.nodes().get(id);
            return node == null || !node.spellModifier() || !(node.spell().equals(spell)||node.effects().stream().anyMatch(effect->effectTargetsModifier(definitions,effect,spell,0))) || player.rank(id) == 0;
        }));
    }

    /** Old weapon-type layouts become a single quick-cast layout without losing assigned spells. */
    private static void migrateBindings(PlayerProgress player) {
        var legacy=player.loadouts().keySet().stream().filter(id->!com.cappleapple.mastery.spells.BindingSlots.valid(id)).sorted().toList();
        if(legacy.isEmpty())return;
        var existing=player.loadouts().getOrDefault(com.cappleapple.mastery.spells.BindingSlots.QUICK,List.of());
        var spells=new java.util.LinkedHashSet<String>(existing);spells.remove("");
        for(String id:legacy)for(String spell:player.loadouts().remove(id))if(!spell.isBlank())spells.add(spell);
        if(!spells.isEmpty()) {
            var slots=player.loadout(com.cappleapple.mastery.spells.BindingSlots.QUICK);slots.clear();slots.addAll(spells);
            while(slots.size()<4)slots.add("");player.bindingMode("quick_cast");
        }
    }

    public static boolean ownsSpell(DefinitionSet definitions, PlayerProgress player, String spell) {
        for (var entry : player.nodes().entrySet()) {
            NodeDefinition node = definitions.nodes().get(entry.getKey());
            if (node == null || entry.getValue().rank() <= 0 || !bookUnlocked(definitions, player, node.id()) || !PromotedTrees.unlocked(definitions,player,node.tree(),-1)) continue;
            if (!node.spellModifier() && node.spell().equals(spell)) return true;
            for (JsonObject effect : node.effects()) if (effectUnlocks(definitions, effect, spell, 0)) return true;
        }
        return false;
    }

    private static boolean effectTargetsModifier(DefinitionSet definitions,JsonObject effect,String spell,int depth) {
        if(depth>32)return false;
        if(effect.has("ref")){var referenced=definitions.effects().get(effect.get("ref").getAsString());return referenced!=null&&effectTargetsModifier(definitions,referenced,spell,depth+1);}
        return effect.has("type")&&effect.get("type").getAsString().equals("mastery:spell_modifier")&&effect.has("spell")&&effect.get("spell").getAsString().equals(spell);
    }
    private static boolean effectUnlocks(DefinitionSet definitions, JsonObject effect, String spell, int depth) {
        if (depth > 32) return false;
        if (effect.has("ref")) {
            JsonObject referenced = definitions.effects().get(effect.get("ref").getAsString());
            return referenced != null && effectUnlocks(definitions, referenced, spell, depth + 1);
        }
        String type = effect.has("type") ? effect.get("type").getAsString() : "";
        return (type.equals("mastery:unlock_spell") || type.equals("unlock_spell"))
                && effect.has("spell") && effect.get("spell").getAsString().equals(spell);
    }

    private static int pointsBetween(TreeDefinition tree, int previous, int next) {
        if (next <= previous) return 0;
        long total = tree.pointEvery() == 0 ? 0 : (long)(next / tree.pointEvery() - previous / tree.pointEvery())*tree.pointsPerAward();
        for (Map.Entry<Integer, Integer> milestone : tree.pointMilestones().entrySet())
            if (milestone.getKey() > previous && milestone.getKey() <= next) total += milestone.getValue();
        if (!tree.pointFormula().isEmpty()) total += (long) PointFormula.total(tree.pointFormula(), next) - PointFormula.total(tree.pointFormula(), previous);
        if (total < 0 || total > Integer.MAX_VALUE) throw new ArithmeticException("point award out of range");
        return (int) total;
    }
}
