package com.cappleapple.mastery.client;

import com.cappleapple.mastery.layout.GraphLayout;
import com.google.gson.*;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Client presentation only. Atomic, world/player-scoped files never grant progression. */
public final class LayoutPreferences {
    public final Map<String, GraphLayout.Point> anchors = new HashMap<>();
    public final Map<String, GraphLayout.Point> offsets = new HashMap<>();
    public final Set<String> expanded = new HashSet<>();
    public final Set<String> seenTrees = new HashSet<>();
    public final Set<String> manualAnchors = new HashSet<>();
    public final Map<String, String> orientations = new HashMap<>();
    public double panX, panY, zoom = 0.85;
    public boolean cameraInitialized;
    public boolean showSectors = true;
    public String selected = "";
    private Path file;

    public static LayoutPreferences load(String worldId) {
        var result = new LayoutPreferences();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || worldId.isBlank()) return result;
        String server = mc.getSingleplayerServer() != null
                ? mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().toString()
                : mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "local";
        try {
            String key = server + "|" + worldId + "|" + mc.player.getUUID();
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)));
            result.file = mc.gameDirectory.toPath().resolve("config/mastery/layouts/" + digest + ".json");
            if (Files.isRegularFile(result.file)) {
                JsonObject json = JsonParser.parseString(Files.readString(result.file)).getAsJsonObject();
                result.showSectors = !json.has("show_sectors") || json.get("show_sectors").getAsBoolean();
                result.cameraInitialized = json.has("camera_initialized") && json.get("camera_initialized").getAsBoolean();
                result.panX = bounded(json, "pan_x", 0, -1000000, 1000000);
                result.panY = bounded(json, "pan_y", 0, -1000000, 1000000);
                result.zoom = bounded(json, "zoom", 0.85, com.cappleapple.mastery.layout.ReadableZoom.MIN_ZOOM, 2.5);
                if (json.has("orientations")) json.getAsJsonObject("orientations").entrySet().forEach(e -> result.orientations.put(e.getKey(), e.getValue().getAsString()));
                if (json.has("manual_anchors")) json.getAsJsonArray("manual_anchors").forEach(v -> result.manualAnchors.add(v.getAsString()));
                if (json.has("selected")) result.selected = json.get("selected").getAsString();
                if (json.has("expanded")) json.getAsJsonArray("expanded").forEach(v -> result.expanded.add(v.getAsString()));
                if (json.has("seen_trees")) json.getAsJsonArray("seen_trees").forEach(v -> result.seenTrees.add(v.getAsString()));
                if (json.has("offsets")) json.getAsJsonObject("offsets").entrySet().forEach(e -> {
                    JsonArray xy = e.getValue().getAsJsonArray();
                    GraphLayout.Point p = new GraphLayout.Point(xy.get(0).getAsDouble(), xy.get(1).getAsDouble());
                    if (p.finite()) result.offsets.put(e.getKey(), p);
                });
                if (json.has("anchors")) json.getAsJsonObject("anchors").entrySet().forEach(e -> {
                    JsonArray xy = e.getValue().getAsJsonArray();
                    GraphLayout.Point p = new GraphLayout.Point(xy.get(0).getAsDouble(), xy.get(1).getAsDouble());
                    if (p.finite()) result.anchors.put(e.getKey(), p);
                });
            }
        } catch (Exception exception) {
            LogUtils.getLogger().warn("Could not load Mastery layout; using a new presentation", exception);
        }
        return result;
    }

    private static double bounded(JsonObject object, String key, double fallback, double min, double max) {
        double value = object.has(key) ? object.get(key).getAsDouble() : fallback;
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }

    public void save() {
        if (file == null) return;
        JsonObject json = new JsonObject();
        json.addProperty("camera_initialized", cameraInitialized);
        json.addProperty("show_sectors", showSectors);
        json.addProperty("pan_x", panX); json.addProperty("pan_y", panY); json.addProperty("zoom", zoom);
        json.addProperty("selected", selected);
        JsonArray open = new JsonArray(); expanded.stream().sorted().forEach(open::add); json.add("expanded", open);
        JsonArray seen = new JsonArray(); seenTrees.stream().sorted().forEach(seen::add); json.add("seen_trees", seen);
        JsonArray manual = new JsonArray(); manualAnchors.stream().sorted().forEach(manual::add); json.add("manual_anchors", manual);
        JsonObject directions = new JsonObject(); orientations.forEach(directions::addProperty); json.add("orientations", directions);
        JsonObject positions = new JsonObject();
        anchors.forEach((id, p) -> { if (offsets.containsKey(id)) return; JsonArray xy = new JsonArray(); xy.add(p.x()); xy.add(p.y()); positions.add(id, xy); });
        json.add("anchors", positions);
        JsonObject relative = new JsonObject();
        offsets.forEach((id, p) -> { JsonArray xy = new JsonArray(); xy.add(p.x()); xy.add(p.y()); relative.add(id, xy); });
        json.add("offsets", relative);
        try {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(json));
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } catch (Exception exception) { LogUtils.getLogger().warn("Could not save Mastery presentation", exception); }
    }
}
