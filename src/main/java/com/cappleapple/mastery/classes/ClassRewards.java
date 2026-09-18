package com.cappleapple.mastery.classes;

import com.cappleapple.mastery.data.DefinitionSet;
import com.cappleapple.mastery.progression.PlayerProgress;

/** Prepares starter progression without mutating the existing attachment. */
public final class ClassRewards {
    private ClassRewards() {}
    /** Pure preflight, also used to verify overflow and prerequisite behavior without a running game. */
    public static PlayerProgress prepare(DefinitionSet definitions, PlayerProgress original, ClassDefinition definition) {
        if (!original.selectedClass().isBlank() || original.classRewardsGranted()) throw new IllegalArgumentException("Class rewards were already granted");
        var next = original.copy();
        definition.startingPoints().forEach((tree, points) -> {
            if (!definitions.trees().containsKey(tree)) throw new IllegalArgumentException("Unknown starting tree " + tree);
            next.tree(tree).points(Math.addExact(next.tree(tree).points(), points));
        });
        int chronology = 0;
        for (var entry : definition.startingSkills().entrySet()) {
            var node = definitions.nodes().get(entry.getKey());
            if (node == null || entry.getValue() > node.maxRank()) throw new IllegalArgumentException("Invalid starting skill " + entry.getKey());
            var existing = next.nodes().get(entry.getKey());
            chronology += (existing == null || existing.unlockOrder() == 0 ? 1 : 0) + (existing == null || existing.purchaseOrder() == 0 ? 1 : 0);
            for (var owned : next.nodes().entrySet()) {
                var other = definitions.nodes().get(owned.getKey());
                if (owned.getValue().rank() > 0 && other != null && (node.exclusions().contains(other.id()) || other.exclusions().contains(node.id())))
                    throw new IllegalArgumentException("Starting skill conflicts with owned skill " + other.id());
            }
        }
        if (!next.canAdvanceSequence(chronology)) throw new IllegalArgumentException("Mastery chronology exhausted");
        definition.startingSkills().forEach((id, rank) -> {
            var node = definitions.nodes().get(id); var state = next.node(id);
            state.rank(Math.max(state.rank(), rank)); state.toggled(true);
            if (state.unlockOrder() == 0) state.unlockOrder(next.nextSequence());
            if (state.purchaseOrder() == 0) state.purchaseOrder(next.nextSequence());
            var tree = next.tree(node.tree()); tree.discovered(true);
            tree.level(Math.max(tree.level(), node.level())); tree.highestLevel(Math.max(tree.highestLevel(), tree.level()));
            if (!node.bookToken().isBlank()) next.bookUnlocks().add(node.bookToken());
        });
        for (var id : definition.startingSkills().keySet()) {
            var node = definitions.nodes().get(id);
            if (!node.dependenciesMet(parent -> next.rank(parent.node()) >= parent.rank())) throw new IllegalArgumentException("Missing starting prerequisite for " + id);
            for (String exclusion : node.exclusions()) if (next.rank(exclusion) > 0) throw new IllegalArgumentException("Conflicting starting skills " + id + " and " + exclusion);
        }
        next.selectedClass(definition.id()); next.classRewardsGranted(true);
        return next;
    }
}
