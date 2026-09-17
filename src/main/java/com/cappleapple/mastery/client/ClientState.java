package com.cappleapple.mastery.client;

import com.cappleapple.mastery.data.*;
import com.cappleapple.mastery.layout.GraphLayout;
import com.cappleapple.mastery.layout.GraphPresentation;
import com.cappleapple.mastery.progression.PlayerProgress;
import com.google.gson.*;
import com.mojang.logging.LogUtils;

import java.util.*;

/** Main-thread, read-only replicas of server data with an independently persisted view. */
public final class ClientState {
    private static DefinitionSet definitions = DefinitionSet.EMPTY;
    private static PlayerProgress progress = new PlayerProgress();
    private static JsonObject runtime = new JsonObject();
    private static LayoutPreferences preferences;
    private static LayoutPreferences editorPreferences;
    private static GraphLayout.Result layout;
    private static Map<String, GraphLayout.Entry> entries = Map.of();
    private static long revision;
    private static long acceptedDefinitionRevision;
    private static long structureRevision;
    private static long receivedClientTick;
    private static final Set<String> dirtyTrees = new HashSet<>();

    private ClientState() {}
    public static DefinitionSet definitions() { return definitions; }
    public static PlayerProgress progress() { return progress; }
    public static long revision() { return revision; }
    public static long structureRevision() { return structureRevision; }
    public static JsonObject runtime() { return runtime; }
    public static boolean nodeEnabled(String id) {
        var state=progress.nodes().get(id);
        var node=definitions.nodes().get(id);
        if(state==null||state.rank()==0||!state.toggled()||node==null) return false;
        return node.modifier().isBlank()||progress.activeModifiers().values().stream().anyMatch(set -> set.contains(id));
    }
    public static boolean editMode() { return runtime.has("edit_mode") && runtime.get("edit_mode").getAsBoolean(); }
    public static long definitionRevision() { return acceptedDefinitionRevision; }
    public static String context() {
        var player=net.minecraft.client.Minecraft.getInstance().player;
        return progress.bindingMode().equals("quick_cast")?com.cappleapple.mastery.spells.BindingSlots.QUICK:
                com.cappleapple.mastery.spells.BindingSlots.hotbar(player==null?0:player.getInventory().selected);
    }
    public static int bindingPage;
    public static int inputSlot(int key){return bindingPage*4+key;}
    public static void cycleBindingPage(int direction){
        int pages=Math.max(1,(capacity()+3)/4);bindingPage=Math.floorMod(bindingPage+direction,pages);
    }
    public static int capacity() { return runtime.has("capacity") ? runtime.get("capacity").getAsInt() : 0; }
    public static long serverTime() {
        var level = net.minecraft.client.Minecraft.getInstance().level;
        long elapsed = level == null ? 0 : Math.max(0, level.getGameTime() - receivedClientTick);
        return (runtime.has("game_time") ? runtime.get("game_time").getAsLong() : 0) + elapsed;
    }
    public static int worldTier() { return runtime.has("world_tier") ? runtime.get("world_tier").getAsInt() : 0; }

    public static LayoutPreferences view() {
        if (editMode()) {
            if (editorPreferences == null) editorPreferences = new LayoutPreferences();
            return editorPreferences;
        }
        if (preferences == null) preferences = LayoutPreferences.load(runtime.has("world_id") ? runtime.get("world_id").getAsString() : "");
        return preferences;
    }
    public static void acceptDefinitions(String text) {
        try {
            JsonObject json = JsonParser.parseString(text).getAsJsonObject();
            DefinitionSet accepted = DefinitionLoader.fromJson(json);
            long acceptedRevision = json.has("definition_revision") ? json.get("definition_revision").getAsLong() : 0;
            definitions = accepted; acceptedDefinitionRevision = acceptedRevision;
            if (!editMode()) dirtyTrees.addAll(definitions.trees().keySet());
            if (editorPreferences != null) { editorPreferences.anchors.clear(); editorPreferences.offsets.clear(); }
            layout = null; revision++; structureRevision++;
        } catch (Exception exception) { LogUtils.getLogger().error("Rejected malformed Mastery definition packet", exception); }
    }
    public static void acceptProgress(String text) {
        try {
            JsonObject json = JsonParser.parseString(text).getAsJsonObject();
            boolean wasEditing = editMode();
            PlayerProgress next = PlayerProgress.fromJson(json.has("progress") ? json.getAsJsonObject("progress") : json);
            for (NodeDefinition node : definitions.nodes().values()) {
                var before = progress.nodes().get(node.id());
                var after = next.nodes().get(node.id());
                if (before == null && after != null || before != null && after == null
                        || before != null && after != null && (before.rank() != after.rank() || before.purchaseOrder() != after.purchaseOrder() || before.unlockOrder() != after.unlockOrder())) dirtyTrees.add(node.tree());
            }
            for (var tree : next.trees().entrySet()) {
                var before = progress.trees().get(tree.getKey());
                if (before == null || before.discovered() != tree.getValue().discovered()) dirtyTrees.add(tree.getKey());
            }
            for (String oldTree : progress.trees().keySet()) if (!next.trees().containsKey(oldTree)) dirtyTrees.add(oldTree);
            if (!progress.bookUnlocks().equals(next.bookUnlocks())) dirtyTrees.addAll(definitions.trees().keySet());
            String oldWorld = runtime.has("world_id") ? runtime.get("world_id").getAsString() : "";
            String newWorld = json.has("world_id") ? json.get("world_id").getAsString() : "";
            if (!oldWorld.equals(newWorld)) { if (preferences != null) preferences.save(); preferences = null; editorPreferences = null; layout = null; structureRevision++; }
            if(!wasEditing)for(var entry:next.nodes().entrySet())if(entry.getValue().rank()>0&&progress.rank(entry.getKey())==0)view().expanded.add(entry.getKey());
            // The first snapshot establishes a baseline; reconnecting never replays old levels.
            if (runtime.has("world_id") && oldWorld.equals(newWorld)) {
                var mc = net.minecraft.client.Minecraft.getInstance();
                next.trees().forEach((id, after) -> {
                    var before = progress.trees().get(id);
                    if (after.level() > (before == null ? 0 : before.level()) && definitions.trees().containsKey(id))
                        mc.getToasts().addToast(new SkillLevelToast(definitions.trees().get(id).name(), after.level()));
                });
            }
            progress = next; runtime = json;
            if (wasEditing != editMode()) {
                if (preferences != null) preferences.save();
                editorPreferences = null; layout = null; dirtyTrees.clear(); structureRevision++;
            }
            var level = net.minecraft.client.Minecraft.getInstance().level;
            receivedClientTick = level == null ? 0 : level.getGameTime();
            if (!dirtyTrees.isEmpty()) { layout = null; structureRevision++; }
            if (json.has("relayout") && json.get("relayout").getAsBoolean()) reorganize();
            revision++;
        } catch (Exception exception) { LogUtils.getLogger().error("Rejected malformed Mastery player packet", exception); }
    }

    public static void clear() {
        if (preferences != null) preferences.save();
        definitions = DefinitionSet.EMPTY; acceptedDefinitionRevision = 0; progress = new PlayerProgress(); runtime = new JsonObject();
        preferences = null; editorPreferences = null; layout = null; entries = Map.of(); dirtyTrees.clear(); revision++; structureRevision++;
    }

    public static GraphLayout.Result layout() {
        if (layout != null) return layout;
        var all = new TreeMap<String, GraphLayout.Entry>();
        definitions.trees().values().forEach(t -> {
            var treeProgress = progress.trees().get(t.id());
            if (!editMode() && (treeProgress == null || !treeProgress.discovered())) return;
            all.put(t.id(), new GraphLayout.Entry(t.id(), "", t.id(), GraphLayout.Kind.TREE, List.of(), Set.of(), 0, 0, t.section()));
            if (view().seenTrees.add(t.id())) view().expanded.add(t.id());
        });
        definitions.nodes().values().forEach(n -> {
            if (!all.containsKey(n.tree())) return;
            if (!editMode() && !com.cappleapple.mastery.progression.ProgressionService.bookUnlocked(definitions, progress, n.id())) return;
            var state = progress.nodes().get(n.id());
            if (!editMode() && !GraphPresentation.visible(n.visibility(), progress.rank(n.id()), state == null ? 0 : state.unlockOrder())) return;
            Set<String> related = GraphPresentation.relatedTrees(definitions, n);
            GraphLayout.Kind kind = n.type() == NodeType.SYNERGY ? GraphLayout.Kind.SYNERGY : n.type() == NodeType.MODIFIER ? GraphLayout.Kind.MODIFIER : GraphLayout.Kind.NODE;
            if (kind == GraphLayout.Kind.SYNERGY && related.stream().anyMatch(tree -> !all.containsKey(tree))) return;
            all.put(n.id(), new GraphLayout.Entry(n.id(), "", n.tree(), kind, n.dependencyLeaves().stream().map(Dependency::node).distinct().toList(), related,
                    editMode() || state == null ? 0 : state.purchaseOrder(), editMode() || state == null ? 0 : state.unlockOrder()));
        });
        entries = Collections.unmodifiableMap(all);
        if (editMode()) {
            view().expanded.addAll(all.keySet());
            layout = GraphLayout.arrange(all.values(), Map.of(), all.keySet(), Map.of(), Set.of());
            view().anchors.clear(); view().anchors.putAll(layout.anchors());
            dirtyTrees.clear();
            return layout;
        }
        layout = GraphLayout.arrange(all.values(), view().anchors, dirtyTrees, view().orientations, view().manualAnchors);
        var positioned = new HashMap<>(layout.anchors());
        var parents = GraphLayout.primaryParents(all.values());
        GraphLayout.applyRelativeOffsets(positioned, parents, view().offsets, view().manualAnchors);
        layout = new GraphLayout.Result(Map.copyOf(positioned), layout.depths(), layout.edges());
        view().anchors.clear(); view().anchors.putAll(positioned);
        view().offsets.clear(); view().offsets.putAll(GraphLayout.relativeOffsets(positioned, parents));
        dirtyTrees.clear();
        return layout;
    }
    public static Map<String, GraphLayout.Entry> entries() { layout(); return entries; }

    public static Set<String> visible() {
        layout();
        if (editMode()) return Set.copyOf(entries.keySet());
        Set<String> result = GraphPresentation.expandedNodes(entries,view().expanded);
        return result;
    }

    public static void reveal(String id) {
        layout();
        reveal(id, new HashSet<>());
    }
    private static void reveal(String id, Set<String> seen) {
        if (!seen.add(id)) return;
        var entry = entries.get(id);
        if (entry == null) return;
        if (!entry.parent().isBlank()) { view().expanded.add(entry.parent()); reveal(entry.parent(), seen); }
        if (!entry.organizational()) {
            view().expanded.add(entry.tree()); reveal(entry.tree(), seen);
            entry.dependencies().forEach(dep -> { view().expanded.add(dep); reveal(dep, seen); });
        }
    }
    public static boolean expandable(String id) {
        var entry = entries().get(id);
        return !editMode() && entry != null && (entry.organizational() || entries.values().stream().anyMatch(n -> n.dependencies().contains(id)));
    }
    public static void moveSubtree(String id, double dx, double dy) {
        if (editMode()) return;
        layout();
        Set<String> moved = GraphLayout.translateSubtree(id, entries.values(), view().anchors, dx, dy);
        view().manualAnchors.addAll(moved);view().manualAnchors.removeAll(definitions.trees().keySet());
        view().offsets.clear(); view().offsets.putAll(GraphLayout.relativeOffsets(view().anchors, GraphLayout.primaryParents(entries.values())));
        layout = new GraphLayout.Result(Map.copyOf(view().anchors), layout.depths(), layout.edges());
    }
    public static void orientTree(String id, String direction) {
        if (editMode() || !definitions.trees().containsKey(id)) return;
        layout();
        String previous=view().orientations.getOrDefault(id,definitions.trees().get(id).section());
        var moved=GraphLayout.rotateSubtree(id,entries.values(),view().anchors,previous,direction);
        view().manualAnchors.addAll(moved);
        view().offsets.clear();view().offsets.putAll(GraphLayout.relativeOffsets(view().anchors,GraphLayout.primaryParents(entries.values())));
        view().orientations.put(id, direction);
        dirtyTrees.add(id); layout = null; revision++; structureRevision++;
    }
    public static void reorganize() { view().anchors.clear(); view().manualAnchors.clear(); view().offsets.clear(); view().orientations.clear(); dirtyTrees.addAll(definitions.trees().keySet()); layout = null; revision++; structureRevision++; }
    public static String boundSpell(int slot) {
        if(slot<0||slot>=capacity())return "";
        List<String> loadout = progress.loadouts().get(context());
        return loadout != null && slot >= 0 && slot < loadout.size() ? loadout.get(slot) : "";
    }
    public static long cooldownRemaining(String spell) {
        if (!runtime.has("cooldowns") || !runtime.get("cooldowns").isJsonObject()) return 0;
        var cooldowns = runtime.getAsJsonObject("cooldowns");
        return cooldowns.has(spell) ? Math.max(0, cooldowns.get(spell).getAsLong() - serverTime()) : 0;
    }
    public static String name(String id) {
        if (definitions.groups().containsKey(id)) return definitions.groups().get(id).name();
        if (definitions.trees().containsKey(id)) return definitions.trees().get(id).name();
        if (definitions.nodes().containsKey(id)) return definitions.nodes().get(id).name();
        if (definitions.spells().containsKey(id)) return definitions.spells().get(id).name();
        return id;
    }
}
