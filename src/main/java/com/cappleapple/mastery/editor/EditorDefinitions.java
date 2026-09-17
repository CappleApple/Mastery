package com.cappleapple.mastery.editor;

import com.cappleapple.mastery.data.DefinitionLoader;
import com.cappleapple.mastery.data.DefinitionSet;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.Set;

/** Pure graph edits and bounded datapack paths; rejected edits never mutate the live snapshot. */
public final class EditorDefinitions {
    private static final Set<String> KINDS=Set.copyOf(DefinitionLoader.KINDS);
    private EditorDefinitions() {}
    public static String logicalKind(String kind) {
        if(!KINDS.contains(kind))throw new IllegalArgumentException("Unknown definition kind: "+kind);
        return kind.equals("synergies")?"nodes":kind;
    }
    public static DefinitionSet change(DefinitionSet current,String kind,String id,JsonObject definition,boolean delete) {
        String logical=logicalKind(kind);
        checkedPath(Path.of("editor"),kind,id);
        var snapshot=current.toJson();
        var entries=snapshot.getAsJsonObject(logical);
        if(delete) {
            if(!entries.has(id))throw new IllegalArgumentException("Definition no longer exists");
            entries.remove(id);
        } else {
            JsonObject value=definition.deepCopy();value.remove("id");
            if(value.has("disabled")&&value.get("disabled").getAsBoolean())throw new IllegalArgumentException("Use Delete to disable a definition");
            entries.add(id,value);
        }
        return DefinitionLoader.fromJson(snapshot);
    }
    public static Path checkedPath(Path root,String kind,String id) {
        logicalKind(kind);
        if(!id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))throw new IllegalArgumentException("Invalid resource ID");
        String[] parts=id.split(":",2);
        if(parts[0].equals(".")||parts[0].equals(".."))throw new IllegalArgumentException("Invalid namespace");
        for(String segment:parts[1].split("/",-1))if(segment.isBlank()||segment.equals(".")||segment.equals(".."))throw new IllegalArgumentException("Invalid resource path");
        Path base=root.toAbsolutePath().normalize();
        Path result=base.resolve("data").resolve(parts[0]).resolve("masteryedits").resolve(kind).resolve(parts[1]+".json").normalize();
        if(!result.startsWith(base))throw new IllegalArgumentException("Resource path leaves editor datapack");
        return result;
    }
}
