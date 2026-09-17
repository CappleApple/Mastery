package com.cappleapple.mastery.data;

import com.cappleapple.mastery.Mastery;
import com.cappleapple.mastery.MasteryRuntime;
import com.cappleapple.mastery.export.MasteryDatapackExport;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.storage.LevelResource;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Stages Mastery resources and editor overrides without reloading unrelated server data. */
public final class MasteryReloadListener extends SimplePreparableReloadListener<MasteryReloadListener.Candidate> {
    private static volatile Map<String,String> nodeKinds = Map.of();
    private static volatile Map<String,Map<String,JsonObject>> acceptedResources = Map.of();
    record Candidate(DefinitionLoader.LoadResult result, Map<String,String> nodeKinds,
                     Map<String,Map<String,JsonObject>> rawResources) {}

    /** Includes disabled tombstones, original resource kinds, and unknown author fields from the last accepted reload. */
    public static Map<String,Map<String,JsonObject>> exportResources() {
        return MasteryDatapackExport.copyResources(acceptedResources);
    }
    @FunctionalInterface private interface ReaderSource { Reader open() throws IOException; }
    private record Source(String path, ReaderSource reader) {}

    /** The accepted resource directory is preserved independently of the node's visual type. */
    public static String nodeResourceKind(String id) { return nodeKinds.getOrDefault(id, "nodes"); }

    @Override protected Candidate prepare(ResourceManager resources, ProfilerFiller profiler) {
        return read(resources, null);
    }

    @Override protected void apply(Candidate candidate, ResourceManager manager, ProfilerFiller profiler) {
        install(candidate);
    }

    /** Runs on the server thread. Only Mastery definitions, effects and client snapshots are refreshed. */
    public static DefinitionLoader.LoadResult reloadOnly(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Mastery reload must run on the server thread");
        Path editorData = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve("mastery-editor/data");
        Candidate candidate = read(server.getResourceManager(), editorData);
        install(candidate);
        return candidate.result();
    }

    private static Candidate read(ResourceManager resources, Path editorData) {
        Map<String,Map<String,Source>> sources = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        for (String kind : DefinitionLoader.KINDS) {
            Map<String,Source> files = new LinkedHashMap<>();
            sources.put(kind, files);
            for (String directory : List.of("mastery", "masteryedits")) {
                String prefix = directory + "/" + kind + "/";
                resources.listResources(directory + "/" + kind, path -> path.getPath().endsWith(".json")).forEach((path, resource) -> {
                    String id = path.getNamespace() + ":" + path.getPath().substring(prefix.length(), path.getPath().length() - 5);
                    files.put(id, new Source(path.toString(), resource::openAsReader));
                });
            }
        }
        if (editorData != null && Files.isDirectory(editorData)) {
            try (var paths = Files.walk(editorData)) {
                for (Path file : paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".json")).sorted().toList()) {
                    Path relative = editorData.relativize(file);
                    if (relative.getNameCount() < 4 || !relative.getName(1).toString().equals("masteryedits")) continue;
                    String kind = relative.getName(2).toString();
                    if (!sources.containsKey(kind)) continue;
                    String local = relative.subpath(3, relative.getNameCount()).toString().replace('\\', '/');
                    String id = relative.getName(0) + ":" + local.substring(0, local.length() - 5);
                    sources.get(kind).put(id, new Source(file.toString(), () -> Files.newBufferedReader(file)));
                }
            } catch (IOException exception) { errors.add(editorData + ": " + exception.getMessage()); }
        }
        Map<String,Map<String,JsonObject>> byKind = new LinkedHashMap<>();
        sources.forEach((kind, files) -> {
            Map<String,JsonObject> parsed = new LinkedHashMap<>();
            byKind.put(kind, parsed);
            files.forEach((id, source) -> {
                try (Reader reader = source.reader().open()) {
                    var json = JsonParser.parseReader(reader);
                    if (!json.isJsonObject()) throw new IllegalArgumentException("Expected JSON object");
                    parsed.put(id, json.getAsJsonObject());
                } catch (Exception exception) { errors.add(source.path() + ": " + exception.getMessage()); }
            });
        });
        var parsed = DefinitionLoader.load(byKind);
        errors.addAll(parsed.errors());
        if (parsed.valid()) errors.addAll(RuntimeDefinitionValidator.validate(parsed.definitions()));
        Map<String,String> origins = new HashMap<>();
        if (parsed.valid()) for (String kind : List.of("nodes", "synergies"))
            byKind.getOrDefault(kind, Map.of()).forEach((id, json) -> {
                if (!json.has("disabled") || !json.get("disabled").getAsBoolean()) origins.put(id, kind);
            });
        return new Candidate(new DefinitionLoader.LoadResult(parsed.definitions(), errors), Map.copyOf(origins),
                MasteryDatapackExport.copyResources(byKind));
    }

    private static void install(Candidate candidate) {
        var result = candidate.result();
        MasteryRuntime.lastReloadErrors = result.errors();
        if (!result.valid()) {
            result.errors().forEach(error -> Mastery.LOGGER.error("Mastery datapack: {}", error));
            Mastery.LOGGER.error("Retaining previous Mastery definitions after {} errors", result.errors().size());
            return;
        }
        nodeKinds = candidate.nodeKinds();
        acceptedResources = candidate.rawResources();
        MasteryRuntime.install(result.definitions());
        Mastery.LOGGER.info("Loaded Mastery: {} independent trees, {} nodes, {} spells, {} XP sources",
                result.definitions().trees().size(), result.definitions().nodes().size(),
                result.definitions().spells().size(), result.definitions().xpSources().size());
    }
}
