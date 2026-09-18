package com.cappleapple.mastery.data;

import com.cappleapple.mastery.progression.PlayerProgress;
import com.cappleapple.mastery.progression.ProgressionService;
import com.google.gson.*;
import java.util.*;

/** Promoted nodes retain their purchase identity while their branches earn a separate point currency. */
public final class PromotedTrees {
    private PromotedTrees() {}
    public static JsonObject parse(JsonObject node) {
        if (!node.has("root_tree") || node.get("root_tree").isJsonNull()) return null;
        JsonElement value=node.get("root_tree");
        if(value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) return value.getAsBoolean()?new JsonObject():null;
        if(!value.isJsonObject())throw new JsonParseException("root_tree must be an object or boolean");
        JsonObject result=value.getAsJsonObject().deepCopy();
        if(result.has("enabled")) {
            var enabled=result.get("enabled");
            if(!enabled.isJsonPrimitive()||!enabled.getAsJsonPrimitive().isBoolean())throw new JsonParseException("root_tree.enabled must be boolean");
            if(!enabled.getAsBoolean())return null;
        }
        if(result.has("id")) {
            var id=result.get("id");
            if(!id.isJsonPrimitive()||!id.getAsJsonPrimitive().isString()||!id.getAsString().isBlank()&&!id.getAsString().matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))
                throw new JsonParseException("root_tree.id must be a resource ID or empty");
        }
        return result;
    }
    public static String treeId(NodeDefinition node) {
        if(node==null||node.rootTree()==null)return "";
        JsonElement id=node.rootTree().get("id");
        if(id==null||id.isJsonPrimitive()&&id.getAsJsonPrimitive().isString()&&id.getAsString().isBlank())return node.id()+"_tree";
        if(!id.isJsonPrimitive()||!id.getAsJsonPrimitive().isString())throw new JsonParseException("root_tree.id must be a resource ID");
        return id.getAsString();
    }
    public static NodeDefinition root(DefinitionSet definitions,String treeId) {
        if(treeId==null||treeId.isBlank())return null;
        return definitions.nodes().values().stream().filter(node->treeId(node).equals(treeId)).findFirst().orElse(null);
    }
    /** Tree ID rendered by a visible root icon; ordinary skill nodes return an empty ID. */
    public static String displayTree(DefinitionSet definitions,String id) {
        return definitions.trees().containsKey(id)?id:treeId(definitions.nodes().get(id));
    }
    public static boolean unlocked(DefinitionSet definitions,PlayerProgress player,String treeId,int worldTier) {
        return unlocked(definitions,player,treeId,worldTier,new HashSet<>());
    }
    /** Runtime callers also evaluate world-specific requirements such as advancements or addon predicates. */
    public static boolean unlocked(DefinitionSet definitions,PlayerProgress player,String treeId,int worldTier,ProgressionService.RequirementEvaluator evaluator) {
        var root=root(definitions,treeId);
        return root==null||runtimeRank(definitions,player,root.id(),worldTier,evaluator,new HashSet<>())>0;
    }
    private static int runtimeRank(DefinitionSet definitions,PlayerProgress player,String id,int tier,ProgressionService.RequirementEvaluator evaluator,Set<String> checking) {
        if(!checking.add(id))return 0;
        try {
            int rank=availableRank(definitions,player,id,tier,new HashSet<>());if(rank==0)return 0;
            var node=definitions.nodes().get(id);var owner=root(definitions,node.tree());
            if(owner!=null&&runtimeRank(definitions,player,owner.id(),tier,evaluator,checking)==0)return 0;
            if(!node.dependenciesMet(leaf->runtimeRank(definitions,player,leaf.node(),tier,evaluator,checking)>=leaf.rank()))return 0;
            for(var requirement:node.requirements())if(!evaluator.test(requirement))return 0;
            return rank;
        }catch(RuntimeException ex){return 0;}finally{checking.remove(id);}
    }
    private static boolean unlocked(DefinitionSet definitions,PlayerProgress player,String treeId,int worldTier,Set<String> checking) {
        NodeDefinition root=root(definitions,treeId);
        if(root==null)return true;
        String key="tree/"+treeId;if(!checking.add(key))return false;
        try{return availableRank(definitions,player,root.id(),worldTier,checking)>0;}
        finally{checking.remove(key);}
    }
    private static int availableRank(DefinitionSet definitions,PlayerProgress player,String id,int tier,Set<String> checking) {
        NodeDefinition node=definitions.nodes().get(id);String key="node/"+id;
        if(node==null||player.rank(id)<1||!checking.add(key))return 0;
        try {
            if(!ProgressionService.bookUnlocked(definitions,player,id)||!unlocked(definitions,player,node.tree(),tier,checking))return 0;
            var owner=definitions.trees().get(node.tree());var state=player.trees().get(node.tree());
            if(owner==null||state==null||tier>=0&&tier<node.worldTier()||Math.min(state.level(),owner.levelCap(tier))<node.level())return 0;
            var caps=owner.capAt(tier);
            if(caps.maxDepth()>=0&&com.cappleapple.mastery.graph.GraphValidator.depthOf(definitions,id)>caps.maxDepth())return 0;
            if(!node.dependenciesMet(leaf->availableRank(definitions,player,leaf.node(),tier,checking)>=leaf.rank()))return 0;
            for(var requirement:node.requirements())if(!knownRequirement(definitions,player,requirement,tier,checking,0))return 0;
            int rank=Math.min(node.maxRank(),player.rank(id));return caps.maxRank()<0?rank:Math.min(rank,caps.maxRank());
        }finally{checking.remove(key);}
    }
    private static boolean knownRequirement(DefinitionSet definitions,PlayerProgress player,JsonObject json,int tier,Set<String> checking,int depth) {
        return !Boolean.FALSE.equals(knownValue(definitions,player,json,tier,checking,depth));
    }
    /** Null means a world predicate that only the server requirement registry can evaluate. */
    private static Boolean knownValue(DefinitionSet definitions,PlayerProgress player,JsonObject json,int tier,Set<String> checking,int depth) {
        if(depth>32)return false;
        try {
            if(json.has("ref")){var ref=definitions.requirements().get(json.get("ref").getAsString());return ref==null?false:knownValue(definitions,player,ref,tier,checking,depth+1);}
            Boolean result=true;
            if(json.has("and"))for(var child:json.getAsJsonArray("and"))result=and(result,knownValue(definitions,player,child.getAsJsonObject(),tier,checking,depth+1));
            if(json.has("or")) {
                Boolean any=false;for(var child:json.getAsJsonArray("or"))any=or(any,knownValue(definitions,player,child.getAsJsonObject(),tier,checking,depth+1));
                result=and(result,any);
            }
            if(json.has("not")){Boolean child=knownValue(definitions,player,json.getAsJsonObject("not"),tier,checking,depth+1);result=and(result,child==null?null:!child);}
            String type=json.has("type")?json.get("type").getAsString():"";
            Boolean value=switch(type) {
                case "mastery:tree_level" -> {String tree=json.get("tree").getAsString();var definition=definitions.trees().get(tree);var state=player.trees().get(tree);yield definition!=null&&Math.min(state==null?0:state.level(),definition.levelCap(tier))>=(json.has("level")?json.get("level").getAsDouble():1);}
                case "mastery:node_rank" -> availableRank(definitions,player,json.get("node").getAsString(),tier,checking)>=(json.has("rank")?json.get("rank").getAsInt():1);
                case "mastery:world_tier" -> tier<0||tier>=(json.has("tier")?json.get("tier").getAsInt():0);
                case "mastery:book_unlocked" -> player.bookUnlocks().contains(json.get("token").getAsString());
                case "" -> json.keySet().stream().allMatch(key->Set.of("and","or","not").contains(key))?Boolean.TRUE:null;
                default -> null;
            };
            return and(result,value);
        }catch(RuntimeException ex){return false;}
    }
    private static Boolean and(Boolean first,Boolean second){return Boolean.FALSE.equals(first)||Boolean.FALSE.equals(second)?Boolean.FALSE:first==null||second==null?null:Boolean.TRUE;}
    private static Boolean or(Boolean first,Boolean second){return Boolean.TRUE.equals(first)||Boolean.TRUE.equals(second)?Boolean.TRUE:first==null||second==null?null:Boolean.FALSE;}
    /** Called before budgets/validation. Ambiguous branches require an explicit generated owner. */
    public static void expand(Map<String,TreeDefinition> trees,Map<String,NodeDefinition> nodes,Map<String,JsonObject> overrides,List<String> errors) {
        var originals=new TreeMap<>(nodes);
        for(NodeDefinition node:originals.values())if(node.rootTree()!=null)try {
            String id=treeId(node);
            if(!id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))throw new JsonParseException("root_tree.id must be a resource ID");
            if(trees.containsKey(id)||nodes.containsKey(id))throw new JsonParseException("root_tree.id conflicts with an existing tree or node: "+id);
            JsonObject json=node.rootTree().deepCopy();
            if(!json.has("name"))json.addProperty("name",node.name());
            if(!json.has("description"))json.addProperty("description",node.description());
            if(!json.has("icon"))json.addProperty("icon",node.icon());
            TreeDefinition source=trees.get(node.tree());
            if(!json.has("section")&&source!=null)json.addProperty("section",source.section());
            TreeDefinition generated=(TreeDefinition)DefinitionLoader.parse("trees",id,json);
            trees.put(id,generated);overrides.put("trees/"+id,SettingsResolver.extract(json));
        }catch(RuntimeException ex){errors.add("nodes/"+node.id()+": "+ex.getMessage());}
        // The dependency graph is validated separately; bounded Kahn traversal also handles very deep trees.
        Map<String,Integer> pending=new HashMap<>();Map<String,List<String>> children=new HashMap<>();ArrayDeque<String> ready=new ArrayDeque<>();
        for(NodeDefinition node:originals.values()) {
            int count=0;for(var dep:node.dependencyLeaves().stream().map(Dependency::node).distinct().toList())if(originals.containsKey(dep)){count++;children.computeIfAbsent(dep,k->new ArrayList<>()).add(node.id());}
            pending.put(node.id(),count);if(count==0)ready.add(node.id());
        }
        while(!ready.isEmpty()) {
            String id=ready.remove();NodeDefinition original=originals.get(id);Set<String> candidates=new TreeSet<>();
            for(var dependency:original.dependencyLeaves()) {
                NodeDefinition parent=nodes.get(dependency.node());
                if(parent==null||!parent.authoredTree().equals(original.authoredTree()))continue;
                String promoted=treeId(parent);
                if(!promoted.isBlank())candidates.add(promoted);
                else if(!parent.tree().equals(parent.authoredTree()))candidates.add(parent.tree());
            }
            // A nearest nested root wins over its ancestors when both are explicit prerequisites.
            candidates.removeIf(ancestor->candidates.stream().anyMatch(other->!other.equals(ancestor)&&descendsFrom(nodes,other,ancestor,new HashSet<>())));
            if(candidates.size()==1)nodes.put(id,original.withTree(candidates.iterator().next()));
            else if(candidates.size()>1)errors.add("nodes/"+id+": dependencies cross promoted branches "+candidates+"; set tree explicitly to the intended root_tree.id");
            for(String child:children.getOrDefault(id,List.of()))if(pending.merge(child,-1,Integer::sum)==0)ready.add(child);
        }
        for(var node:nodes.values())if(node.rootTree()!=null&&descendsFrom(nodes,node.tree(),treeId(node),new HashSet<>()))
            errors.add("nodes/"+node.id()+": promoted roots cannot belong to their own generated tree or a tree gated by themselves");
        for(var node:nodes.values())if(node.rootTree()!=null&&node.tree().equals(treeId(node)))
            errors.add("nodes/"+node.id()+": root node must keep a separately available owning tree for its initial purchase");
    }
    private static boolean descendsFrom(Map<String,NodeDefinition> nodes,String tree,String ancestor,Set<String> seen) {
        if(!seen.add(tree))return false;
        for(var node:nodes.values())if(treeId(node).equals(tree))return node.tree().equals(ancestor)||descendsFrom(nodes,node.tree(),ancestor,seen);
        return false;
    }
    /** A promoted root inherits modifiers from its owner and prerequisite trees, including nested roots. */
    public static boolean inherits(DefinitionSet definitions,String tree,String ancestor) {
        return inherits(definitions,tree,ancestor,new HashSet<>());
    }
    private static boolean inherits(DefinitionSet definitions,String tree,String ancestor,Set<String> seen) {
        if(tree.equals(ancestor))return true;
        if(!seen.add(tree))return false;
        var root=root(definitions,tree);if(root==null)return false;
        Set<String> parents=new HashSet<>();parents.add(root.tree());
        for(var leaf:root.dependencyLeaves()) {
            var parent=definitions.nodes().get(leaf.node());
            if(parent!=null)parents.add(treeId(parent).isBlank()?parent.tree():treeId(parent));
        }
        return parents.stream().anyMatch(parent->inherits(definitions,parent,ancestor,seen));
    }
    /** Pack sync/editor snapshots retain authored owners and root objects, never materialized duplicate files. */
    public static void prepareJson(DefinitionSet definitions,JsonObject json) {
        definitions.nodes().forEach((id,node)->{
            JsonObject encoded=json.getAsJsonObject("nodes").getAsJsonObject(id);
            encoded.remove("authored_tree");encoded.addProperty("tree",node.authoredTree());
            if(node.rootTree()==null)encoded.remove("root_tree");
            else json.getAsJsonObject("trees").remove(treeId(node));
        });
    }
}
