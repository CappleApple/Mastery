package com.cappleapple.mastery.progression;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Authoritative persistent state. Mutate only on the server thread through validated services. */
public final class PlayerProgress {
    public static final int FORMAT_VERSION = 4;
    private final Map<String, TreeProgress> trees = new LinkedHashMap<>();
    private final Map<String, NodeProgress> nodes = new LinkedHashMap<>();
    private final Map<String, List<String>> loadouts = new LinkedHashMap<>();
    private final Map<String, Set<String>> activeModifiers = new LinkedHashMap<>();
    private final Set<String> bookUnlocks = new LinkedHashSet<>();
    private final Set<String> usageGrants = new LinkedHashSet<>();
    private long sequence;
    private String selectedClass = "";
    private boolean classRewardsGranted;
    private final List<JsonObject> pendingClassItems = new ArrayList<>();
    public List<JsonObject> pendingClassItems() { return pendingClassItems; }
    private com.cappleapple.mastery.classes.ClassSelectionState classSelection;
    public String selectedClass() { return selectedClass; }
    public void selectedClass(String value) { selectedClass = value == null ? "" : value; }
    public boolean classRewardsGranted() { return classRewardsGranted; }
    public void classRewardsGranted(boolean value) { classRewardsGranted = value; }
    public com.cappleapple.mastery.classes.ClassSelectionState classSelection() { return classSelection; }
    public void classSelection(com.cappleapple.mastery.classes.ClassSelectionState value) { classSelection = value; }

    private String bindingMode="hotbar";
    public String bindingMode(){return bindingMode;}
    public void bindingMode(String value){if(!value.equals("hotbar")&&!value.equals("quick_cast"))throw new IllegalArgumentException("Unknown binding mode");bindingMode=value;}

    public Map<String, TreeProgress> trees() { return trees; }
    public Map<String, NodeProgress> nodes() { return nodes; }
    public TreeProgress tree(String id) { return trees.computeIfAbsent(id, ignored -> new TreeProgress()); }
    public NodeProgress node(String id) { return nodes.computeIfAbsent(id, ignored -> new NodeProgress()); }
    public int rank(String id) { NodeProgress node = nodes.get(id); return node == null ? 0 : node.rank(); }
    public Map<String, List<String>> loadouts() { return loadouts; }
    public List<String> loadout(String context) {
        return loadouts.computeIfAbsent(context, ignored -> new ArrayList<>(List.of("", "", "", "")));
    }
    public void bind(String context,int slot,String spell) {
        if(slot<0||slot>=com.cappleapple.mastery.spells.BindingSlots.limit(context))throw new IllegalArgumentException("Invalid spell slot");
        var slots=loadout(context);while(slots.size()<=slot)slots.add("");slots.set(slot,spell);
    }
    public Map<String, Set<String>> activeModifiers() { return activeModifiers; }
    public Set<String> modifiers(String spell) { return activeModifiers.computeIfAbsent(spell, ignored -> new LinkedHashSet<>()); }
    public Set<String> bookUnlocks() { return bookUnlocks; }
    public Set<String> usageGrants() { return usageGrants; }
    public boolean canAdvanceSequence(int amount) {return amount>=0&&sequence<=Long.MAX_VALUE-amount;}
    public long nextSequence() {
        if (sequence == Long.MAX_VALUE) throw new IllegalStateException("Mastery chronology exhausted");
        return ++sequence;
    }

    /** Deep copy used by attachment copying and tests; derived effects are intentionally absent. */
    public PlayerProgress copy() { return fromJson(toJson()); }

    public void clear() {
        trees.clear(); nodes.clear(); loadouts.clear(); activeModifiers.clear(); usageGrants.clear(); bookUnlocks.clear(); sequence = 0;
    }

    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("version", FORMAT_VERSION);
        root.addProperty("sequence", sequence);
        root.addProperty("binding_mode", bindingMode);
        root.addProperty("selected_class", selectedClass);
        root.addProperty("class_rewards_granted", classRewardsGranted);
        JsonArray pendingItems = new JsonArray(); pendingClassItems.forEach(item -> pendingItems.add(item.deepCopy()));
        root.add("pending_class_items", pendingItems);
        if (classSelection != null) root.add("class_selection_state", classSelection.toJson());
        JsonObject treeJson = new JsonObject();
        trees.forEach((id, tree) -> {
            JsonObject value = new JsonObject();
            value.addProperty("xp", tree.xp());
            value.addProperty("lifetime_xp", tree.lifetimeXp());
            value.addProperty("level", tree.level());
            value.addProperty("points", tree.points());
            value.addProperty("highest_level", tree.highestLevel());
            value.addProperty("discovered", tree.discovered());
            treeJson.add(id, value);
        });
        root.add("trees", treeJson);
        JsonObject nodeJson = new JsonObject();
        nodes.forEach((id, node) -> {
            JsonObject value = new JsonObject();
            value.addProperty("rank", node.rank());
            value.addProperty("purchase_order", node.purchaseOrder());
            value.addProperty("unlock_order", node.unlockOrder());
            value.addProperty("toggled", node.toggled());
            nodeJson.add(id, value);
        });
        root.add("nodes", nodeJson);
        JsonObject loadoutJson = new JsonObject();
        loadouts.forEach((id, slots) -> {
            JsonArray values = new JsonArray();
            for (int slot = 0; slot < Math.min(com.cappleapple.mastery.spells.BindingSlots.limit(id),Math.max(4,slots.size())); slot++) values.add(slot < slots.size() && slots.get(slot) != null ? slots.get(slot) : "");
            loadoutJson.add(id, values);
        });
        root.add("loadouts", loadoutJson);
        JsonObject modifierJson = new JsonObject();
        activeModifiers.forEach((id, modifiers) -> {
            JsonArray values = new JsonArray();
            modifiers.forEach(values::add);
            modifierJson.add(id, values);
        });
        root.add("active_modifiers", modifierJson);
        JsonArray grants = new JsonArray();
        usageGrants.forEach(grants::add);
        root.add("usage_grants", grants);
        JsonArray books = new JsonArray();
        bookUnlocks.forEach(books::add);
        root.add("book_unlocks", books);
        return root;
    }

    /** Unknown fields are ignored for forward compatibility; malformed individual entries are skipped. */
    public static PlayerProgress fromJson(JsonObject root) {
        PlayerProgress result = new PlayerProgress();
        if(root.has("binding_mode")&&root.get("binding_mode").isJsonPrimitive()&&root.get("binding_mode").getAsString().equals("quick_cast"))result.bindingMode="quick_cast";
        if (root.has("selected_class") && root.get("selected_class").isJsonPrimitive()
                && root.get("selected_class").getAsJsonPrimitive().isString()) {
            String id = root.get("selected_class").getAsString();
            if (id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) result.selectedClass = id;
        }
        result.classRewardsGranted = boolValue(root, "class_rewards_granted", false) || !result.selectedClass.isBlank();
        if (root.has("pending_class_items") && root.get("pending_class_items").isJsonArray())
            for (JsonElement item : root.getAsJsonArray("pending_class_items"))
                if (item.isJsonObject()) result.pendingClassItems.add(item.getAsJsonObject().deepCopy());
        if (root.has("class_selection_state") && root.get("class_selection_state").isJsonObject())
            result.classSelection = com.cappleapple.mastery.classes.ClassSelectionState.fromJson(root.getAsJsonObject("class_selection_state"));
        result.sequence = Math.max(0, longValue(root, "sequence", 0));
        for (var entry : object(root, "trees").entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            JsonObject value = entry.getValue().getAsJsonObject();
            TreeProgress tree = result.tree(entry.getKey());
            tree.xp(doubleValue(value, "xp", 0));
            tree.lifetimeXp(doubleValue(value, "lifetime_xp", 0));
            tree.level(intValue(value, "level", 0));
            tree.points(intValue(value, "points", 0));
            tree.highestLevel(intValue(value, "highest_level", tree.level()));
            tree.discovered(boolValue(value, "discovered", false) || tree.points() > 0);
        }
        for (var entry : object(root, "nodes").entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            JsonObject value = entry.getValue().getAsJsonObject();
            NodeProgress node = result.node(entry.getKey());
            node.rank(intValue(value, "rank", 0));
            node.purchaseOrder(longValue(value, "purchase_order", 0));
            node.unlockOrder(longValue(value, "unlock_order", 0));
            node.toggled(boolValue(value, "toggled", true));
            result.sequence = Math.max(result.sequence, Math.max(node.purchaseOrder(), node.unlockOrder()));
        }
        for (var entry : object(root, "loadouts").entrySet()) {
            if (!entry.getValue().isJsonArray()) continue;
            JsonArray values = entry.getValue().getAsJsonArray();
            List<String> slots = result.loadout(entry.getKey());
            for (int slot = 0; slot < Math.min(com.cappleapple.mastery.spells.BindingSlots.limit(entry.getKey()), values.size()); slot++) {
                JsonElement value = values.get(slot);
                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) result.bind(entry.getKey(),slot,value.getAsString());
            }
        }
        for (var entry : object(root, "active_modifiers").entrySet()) {
            if (!entry.getValue().isJsonArray()) continue;
            for (JsonElement value : entry.getValue().getAsJsonArray())
                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) result.modifiers(entry.getKey()).add(value.getAsString());
        }
        JsonElement grants = root.get("usage_grants");
        if (grants != null && grants.isJsonArray()) for (JsonElement value : grants.getAsJsonArray())
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) result.usageGrants.add(value.getAsString());
        JsonElement books = root.get("book_unlocks");
        if (books != null && books.isJsonArray()) for (JsonElement value : books.getAsJsonArray())
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                    && value.getAsString().matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) result.bookUnlocks.add(value.getAsString());
        return result;
    }

    private static JsonObject object(JsonObject parent, String key) {
        return parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : new JsonObject();
    }
    private static int intValue(JsonObject parent, String key, int fallback) {
        try { return parent.has(key) ? parent.get(key).getAsBigDecimal().intValueExact() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }
    private static long longValue(JsonObject parent, String key, long fallback) {
        try { return parent.has(key) ? parent.get(key).getAsBigDecimal().longValueExact() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }
    private static double doubleValue(JsonObject parent, String key, double fallback) {
        try { double value = parent.has(key) ? parent.get(key).getAsDouble() : fallback; return Double.isFinite(value) ? value : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }
    private static boolean boolValue(JsonObject parent, String key, boolean fallback) {
        try { return parent.has(key) && parent.get(key).isJsonPrimitive() && parent.get(key).getAsJsonPrimitive().isBoolean() ? parent.get(key).getAsBoolean() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }
}
