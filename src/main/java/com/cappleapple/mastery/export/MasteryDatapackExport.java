package com.cappleapple.mastery.export;

import com.cappleapple.mastery.data.DefinitionLoader;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Serializes accepted raw resources without rebuilding their schema or writing server files. */
public final class MasteryDatapackExport {
    public static final int PACK_FORMAT=48;
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private MasteryDatapackExport() {}

    /** JSON objects are copied too: callers cannot mutate the accepted export snapshot. */
    public static Map<String,Map<String,JsonObject>> copyResources(Map<String,Map<String,JsonObject>> resources) {
        Map<String,Map<String,JsonObject>> copy=new LinkedHashMap<>();
        resources.forEach((kind,files) -> {
            Map<String,JsonObject> copied=new LinkedHashMap<>();
            files.forEach((id,json) -> copied.put(id,json.deepCopy()));
            copy.put(kind,Collections.unmodifiableMap(copied));
        });
        return Collections.unmodifiableMap(copy);
    }

    public static String encode(Map<String,Map<String,JsonObject>> resources,int maxCharacters) throws IOException {
        if(maxCharacters<4) throw new IllegalArgumentException("Mastery export transfer limit is too small");
        int maxBytes=(maxCharacters/4)*3;
        byte[] zipped=zip(resources,maxBytes);
        String encoded=Base64.getEncoder().encodeToString(zipped);
        if(encoded.length()>maxCharacters) throw tooLarge();
        return encoded;
    }

    /** Deterministic paths and entry times make identical accepted snapshots produce identical ZIPs. */
    public static byte[] zip(Map<String,Map<String,JsonObject>> resources,int maxBytes) throws IOException {
        if(maxBytes<1) throw new IllegalArgumentException("Mastery export transfer limit is too small");
        var output=new BoundedBytes(maxBytes);
        try(var zip=new ZipOutputStream(output,StandardCharsets.UTF_8)) {
            JsonObject pack=new JsonObject(); pack.addProperty("pack_format",PACK_FORMAT);
            pack.addProperty("description","Exported Mastery progression");
            JsonObject metadata=new JsonObject(); metadata.add("pack",pack);
            write(zip,"pack.mcmeta",metadata);
            for(String kind:new TreeSet<>(resources.keySet())) {
                if(!DefinitionLoader.KINDS.contains(kind)) throw new IllegalArgumentException("Unknown Mastery resource directory: "+kind);
                for(var entry:new TreeMap<>(resources.get(kind)).entrySet())
                    write(zip,resourcePath(kind,entry.getKey()),entry.getValue());
            }
        }
        return output.toByteArray();
    }

    private static String resourcePath(String kind,String id) {
        int colon=id.indexOf(':');
        if(colon<1||colon!=id.lastIndexOf(':')) throw invalidPath(id);
        String namespace=id.substring(0,colon),path=id.substring(colon+1);
        if(!namespace.matches("[a-z0-9_.-]+")||namespace.equals(".")||namespace.equals("..")||!path.matches("[a-z0-9/._-]+")) throw invalidPath(id);
        for(String segment:path.split("/",-1)) if(segment.isEmpty()||segment.equals(".")||segment.equals("..")) throw invalidPath(id);
        return "data/"+namespace+"/mastery/"+kind+"/"+path+".json";
    }
    private static IllegalArgumentException invalidPath(String id) { return new IllegalArgumentException("Unsafe Mastery export resource ID: "+id); }
    private static IllegalArgumentException tooLarge() { return new IllegalArgumentException("Mastery export exceeds the client transfer limit; reduce datapack resource size"); }
    private static void write(ZipOutputStream zip,String path,JsonObject json) throws IOException {
        ZipEntry entry=new ZipEntry(path); entry.setTime(0); zip.putNextEntry(entry);
        Writer writer=new OutputStreamWriter(zip,StandardCharsets.UTF_8);
        GSON.toJson(json,writer); writer.flush(); zip.closeEntry();
    }
    private static final class BoundedBytes extends ByteArrayOutputStream {
        private final int limit;
        BoundedBytes(int limit) { this.limit=limit; }
        @Override public synchronized void write(int value) { check(1); super.write(value); }
        @Override public synchronized void write(byte[] bytes,int offset,int length) { check(length); super.write(bytes,offset,length); }
        private void check(int length) { if((long)count+length>limit) throw tooLarge(); }
    }
}
