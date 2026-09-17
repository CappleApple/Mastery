package com.cappleapple.mastery.export;

import com.cappleapple.mastery.data.DefinitionLoader;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MasteryDatapackExportTest {
    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
    private static Map<String,JsonObject> read(byte[] bytes) throws IOException {
        Map<String,JsonObject> files=new LinkedHashMap<>();
        try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes),StandardCharsets.UTF_8)) {
            for(ZipEntry entry;(entry=zip.getNextEntry())!=null;) {
                assertNull(files.put(entry.getName(),json(new String(zip.readAllBytes(),StandardCharsets.UTF_8))),"Duplicate ZIP entry");
            }
        }
        return files;
    }
    @Test void acceptedMergedResourcesRoundTripWithoutLosingTombstonesKindsOrAuthorFields() throws IOException {
        Map<String,JsonObject> nodes=new LinkedHashMap<>();
        nodes.put("addon:branches/upgraded",json("{\"tree\":\"pack:fire\",\"name\":\"Base\"}"));
        nodes.put("addon:branches/upgraded",json("{\"tree\":\"pack:fire\",\"name\":\"Edited\",\"author_extra\":{\"note\":\"Rune 火\",\"value\":[1,true]}}"));
        nodes.put("pack:removed",json("{\"disabled\":true,\"author_reason\":\"Replaced\"}"));
        var snapshot=Map.of("trees",Map.of("pack:fire",json("{}")),"nodes",nodes,
                "synergies",Map.of("addon:bridges/unusual",json("{\"tree\":\"pack:fire\",\"type\":\"passive\",\"custom\":7}")));
        var original=DefinitionLoader.load(snapshot);
        assertTrue(original.valid(),original.errors().toString());
        var files=read(MasteryDatapackExport.zip(snapshot,1_000_000));
        assertEquals(5,files.size());
        assertEquals(48,files.get("pack.mcmeta").getAsJsonObject("pack").get("pack_format").getAsInt());
        assertEquals(nodes.get("addon:branches/upgraded"),files.get("data/addon/mastery/nodes/branches/upgraded.json"));
        assertEquals(nodes.get("pack:removed"),files.get("data/pack/mastery/nodes/removed.json"));
        assertTrue(files.containsKey("data/addon/mastery/synergies/bridges/unusual.json"));
        assertTrue(files.keySet().stream().noneMatch(path -> path.contains("masteryedits")));
        Map<String,Map<String,JsonObject>> restored=new LinkedHashMap<>();
        files.forEach((path,value) -> {
            if(path.equals("pack.mcmeta"))return;
            String[] parts=path.split("/",5);
            String id=parts[1]+":"+parts[4].substring(0,parts[4].length()-5);
            restored.computeIfAbsent(parts[3],ignored->new LinkedHashMap<>()).put(id,value);
        });
        assertEquals(snapshot,restored);
        var reloaded=DefinitionLoader.load(restored);
        assertTrue(reloaded.valid(),reloaded.errors().toString());
        assertEquals(original.definitions().toJson(),reloaded.definitions().toJson());
        assertFalse(reloaded.definitions().nodes().containsKey("pack:removed"));
    }
    @Test void copiedRawSnapshotCannotBeChangedThroughOriginalOrExportView() {
        var original=json("{\"disabled\":true,\"metadata\":{\"revision\":1}}");
        var accepted=MasteryDatapackExport.copyResources(Map.of("nodes",Map.of("pack:old",original)));
        original.getAsJsonObject("metadata").addProperty("revision",2);
        var exported=MasteryDatapackExport.copyResources(accepted);
        exported.get("nodes").get("pack:old").getAsJsonObject("metadata").addProperty("revision",3);
        assertEquals(1,accepted.get("nodes").get("pack:old").getAsJsonObject("metadata").get("revision").getAsInt());
        assertThrows(UnsupportedOperationException.class,()->accepted.get("nodes").clear());
    }
    @Test void rejectsTraversalAbsolutePathsAndUnknownDirectories() {
        for(String id:List.of("pack:../escape","pack:a/../../escape","pack:/absolute","pack:a//b","..:outside","pack:a\\b","pack:a/./b","pack:a/","missing_colon")) {
            var failure=assertThrows(IllegalArgumentException.class,()->MasteryDatapackExport.zip(Map.of("nodes",Map.of(id,json("{\"disabled\":true}"))),100_000));
            assertTrue(failure.getMessage().contains("resource ID"),id);
        }
        assertThrows(IllegalArgumentException.class,()->MasteryDatapackExport.zip(Map.of("masteryedits",Map.of()),100_000));
    }
    @Test void transferIsDeterministicPlainBase64AndBounded() throws IOException {
        var snapshot=Map.of("nodes",Map.of("pack:deleted",json("{\"disabled\":true}")));
        String first=MasteryDatapackExport.encode(snapshot,100_000),second=MasteryDatapackExport.encode(snapshot,100_000);
        assertEquals(first,second);
        assertTrue(read(Base64.getDecoder().decode(first)).containsKey("pack.mcmeta"));
        var error=assertThrows(IllegalArgumentException.class,()->MasteryDatapackExport.encode(snapshot,100));
        assertTrue(error.getMessage().contains("client transfer limit"));
    }
}
