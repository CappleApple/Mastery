package com.cappleapple.mastery.client.model;

import com.cappleapple.mastery.data.DefinitionSet;
import com.cappleapple.mastery.data.NodeDefinition;
import java.util.*;

/** Pure token lookup, independent of learned ranks or the currently visible graph. */
public final class SkillBookIconLookup {
    private SkillBookIconLookup() {}
    public static Optional<NodeDefinition> find(DefinitionSet definitions,String token) {
        if(token==null||!token.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) return Optional.empty();
        var matches=definitions.nodes().values().stream()
                .filter(node -> token.equals(node.bookToken().isBlank()?node.id():node.bookToken())).toList();
        Set<String> ids=new HashSet<>(); matches.forEach(node -> ids.add(node.id()));
        return matches.stream().sorted(Comparator.comparing((NodeDefinition node) -> hasMatchingAncestor(node,ids,definitions))
                .thenComparing(NodeDefinition::id)).findFirst();
    }
    private static boolean hasMatchingAncestor(NodeDefinition node,Set<String> matches,DefinitionSet definitions) {
        Deque<String> pending=new ArrayDeque<>(); node.dependencyLeaves().forEach(dependency -> pending.add(dependency.node()));
        Set<String> seen=new HashSet<>();
        while(!pending.isEmpty()) {
            String id=pending.removeFirst(); if(!seen.add(id)) continue;
            if(matches.contains(id)) return true;
            var parent=definitions.nodes().get(id);
            if(parent!=null) parent.dependencyLeaves().forEach(dependency -> pending.addLast(dependency.node()));
        }
        return false;
    }
}
