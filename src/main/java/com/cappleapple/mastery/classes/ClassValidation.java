package com.cappleapple.mastery.classes;

import com.cappleapple.mastery.data.DefinitionSet;
import java.util.List;

public final class ClassValidation {
    private ClassValidation() {}
    public static void validate(DefinitionSet definitions, List<String> errors) {
        definitions.classes().forEach((id, json) -> {
            String path = "classes/" + id;
            try {
                var definition = ClassDefinition.parse(id, json);
                definition.startingPoints().forEach((tree, points) -> {
                    if (!definitions.trees().containsKey(tree)) errors.add(path + ": unknown starting_points tree " + tree);
                });
                definition.startingSkills().forEach((nodeId, rank) -> {
                    var node = definitions.nodes().get(nodeId);
                    if (node == null) { errors.add(path + ": unknown starting_skills node " + nodeId); return; }
                    if (rank > node.maxRank()) errors.add(path + ": starting rank exceeds " + nodeId + " max_rank");
                    if (!node.dependenciesMet(parent -> definition.startingSkills().getOrDefault(parent.node(), 0) >= parent.rank()))
                        errors.add(path + ": starting_skills must include the required prerequisite ranks for " + nodeId);
                    for (String exclusion : node.exclusions()) if (definition.startingSkills().containsKey(exclusion))
                        errors.add(path + ": mutually exclusive starting skills " + nodeId + " and " + exclusion);
                });
            } catch (RuntimeException ex) { errors.add(path + ": " + ex.getMessage()); }
        });
    }
}
