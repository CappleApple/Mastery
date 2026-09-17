package com.cappleapple.mastery.editor;

import com.cappleapple.mastery.MasteryRuntime;
import com.cappleapple.mastery.data.MasteryReloadListener;
import com.cappleapple.mastery.data.RuntimeDefinitionValidator;
import com.cappleapple.mastery.network.MasteryNetwork;
import com.google.gson.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Operator-only sessions and validated overrides. Reloading never touches other mods' datapack listeners. */
public final class EditorService {
    private static final Set<UUID> ENABLED=new HashSet<>();
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private EditorService() {}
    public static boolean enabled(ServerPlayer player) {
        if(!player.hasPermissions(2)) {
            if(ENABLED.remove(player.getUUID()))MasteryRuntime.sync(player);
            return false;
        }
        return ENABLED.contains(player.getUUID());
    }
    public static void setEnabled(ServerPlayer player,boolean value) {
        if(value&&!player.hasPermissions(2))throw new IllegalArgumentException("Operator permission is required");
        if(value)ENABLED.add(player.getUUID());else ENABLED.remove(player.getUUID());
        MasteryRuntime.sync(player);MasteryRuntime.flush(player);
    }
    public static void forget(ServerPlayer player){ENABLED.remove(player.getUUID());}
    public static String handle(ServerPlayer player,String action,String id,String value) {
        if(!enabled(player))return "Enable operator edit mode before editing definitions";
        try {
            if(action.equals("editor_get")) {
                String kind=EditorDefinitions.logicalKind(value);
                var entries=MasteryRuntime.definitions().toJson().getAsJsonObject(kind);
                if(!entries.has(id))return "Definition no longer exists";
                JsonObject response=new JsonObject();response.addProperty("kind",kind);response.addProperty("id",id);
                var resources=MasteryReloadListener.exportResources();
                String diskKind=kind.equals("nodes")?MasteryReloadListener.nodeResourceKind(id):kind;
                var definition=resources.getOrDefault(diskKind,Map.of()).getOrDefault(id,entries.getAsJsonObject(id)).deepCopy();definition.remove("id");
                response.add("definition",definition);response.addProperty("revision",MasteryRuntime.definitionRevision());
                MasteryNetwork.send(player,"editor_definition",JSON.toJson(response));return "";
            }
            if(!action.equals("editor_save")&&!action.equals("editor_delete"))return "Unknown editor action";
            boolean delete=action.equals("editor_delete");
            JsonObject request=delete?new JsonObject():JsonParser.parseString(value).getAsJsonObject();
            String kind=EditorDefinitions.logicalKind(delete?value:request.get("kind").getAsString());
            if(request.has("revision")&&request.get("revision").getAsLong()!=MasteryRuntime.definitionRevision())return "Definitions changed while editing; reopen the definition before saving";
            JsonObject definition=delete?new JsonObject():request.getAsJsonObject("definition");
            var previous=MasteryRuntime.definitions();
            var candidate=EditorDefinitions.change(previous,kind,id,definition,delete);
            var errors=RuntimeDefinitionValidator.validate(candidate);
            if(!errors.isEmpty())return String.join("; ",errors);
            String diskKind=kind;
            if(kind.equals("nodes")&&previous.nodes().containsKey(id))diskKind=MasteryReloadListener.nodeResourceKind(id);
            Path world=player.server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            Path root=world.resolve("datapacks/mastery-editor");
            Path target=EditorDefinitions.checkedPath(root,diskKind,id);
            Files.createDirectories(target.getParent());
            if(!root.toRealPath().startsWith(world.toRealPath())||!target.getParent().toRealPath().startsWith(root.toRealPath()))
                return "Editor directory must remain within this world";
            Path metadata=root.resolve("pack.mcmeta");
            byte[] old=Files.exists(target)?Files.readAllBytes(target):null;
            byte[] oldMetadata=Files.exists(metadata)?Files.readAllBytes(metadata):null;
            JsonObject output=definition.deepCopy();output.remove("id");
            if(delete)output.addProperty("disabled",true);
            try {
                atomicWrite(target,JSON.toJson(output).getBytes(StandardCharsets.UTF_8));
                atomicWrite(metadata,"{\"pack\":{\"pack_format\":48,\"description\":\"Mastery in-game editor overrides\"}}".getBytes(StandardCharsets.UTF_8));
                var result=MasteryReloadListener.reloadOnly(player.server);
                if(!result.valid())throw new IllegalArgumentException(String.join("; ",result.errors()));
                // Persist the pack selection for the next world load without reloading recipes, tags or other mods.
                var repository=player.server.getPackRepository();
                List<String> selected=new ArrayList<>(repository.getSelectedIds());
                repository.reload();
                if(!selected.contains("file/mastery-editor"))selected.add("file/mastery-editor");
                repository.setSelected(selected);
                var worldData=player.server.getWorldData();
                List<String> disabled=new ArrayList<>(worldData.getDataConfiguration().dataPacks().getDisabled());
                disabled.remove("file/mastery-editor");
                worldData.setDataConfiguration(new WorldDataConfiguration(new DataPackConfig(selected,disabled),worldData.enabledFeatures()));
            } catch(Exception failure) {
                restore(target,old);restore(metadata,oldMetadata);
                var restored=MasteryReloadListener.reloadOnly(player.server);
                if(!restored.valid())MasteryRuntime.install(previous);
                return "Edit rolled back: "+Objects.toString(failure.getMessage(),failure.getClass().getSimpleName());
            }
            status(player,true,"Saved "+id+"; reloaded Mastery data only");
            return "";
        } catch(Exception error) {
            return "Editor: "+Objects.toString(error.getMessage(),error.getClass().getSimpleName());
        }
    }
    private static void restore(Path path,byte[] bytes)throws java.io.IOException {
        if(bytes==null)Files.deleteIfExists(path);else atomicWrite(path,bytes);
    }
    private static void atomicWrite(Path path,byte[] bytes)throws java.io.IOException {
        Path temporary=Files.createTempFile(path.getParent(),"mastery-",".tmp");
        try {
            Files.write(temporary,bytes);
            try{Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException ignored){Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temporary);}
    }
    public static void status(ServerPlayer player,boolean success,String message) {
        JsonObject response=new JsonObject();response.addProperty("success",success);response.addProperty("message",message);response.addProperty("pending",false);
        MasteryNetwork.send(player,"editor_status",response.toString());
        player.displayClientMessage(Component.literal(message),false);
    }
}
