package com.cappleapple.mastery.client.gui.editor;

import com.cappleapple.mastery.client.ClientState;
import com.cappleapple.mastery.client.gui.MasteryScreen;
import com.cappleapple.mastery.network.MasteryNetwork;
import com.google.gson.*;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;

/** Main-thread editor responses. Permissions and filesystem writes live on the server. */
public final class EditorClient {
    private static String pendingId = "", pendingKind = "", status = "";
    private static MasteryScreen returnTo;
    private static boolean failed;
    private EditorClient() {}
    public static String status() { return status; }
    public static boolean failed() { return failed; }

    public static void editDefinition(MasteryScreen parent,String kind,String id){
        if(!ClientState.editMode())return;
        returnTo=parent;pendingId=id;pendingKind=kind;status="Loading definition...";failed=false;
        Minecraft.getInstance().setScreen(parent);MasteryNetwork.sendAction("editor_get",id,kind,0);
    }
    public static void deleteDefinition(MasteryScreen parent,String kind,String id){
        Minecraft.getInstance().setScreen(new ConfirmScreen(confirmed->{Minecraft.getInstance().setScreen(parent);if(confirmed&&ClientState.editMode())MasteryNetwork.sendAction("editor_delete",id,kind,0);},Component.literal("Delete "+id+"?"),Component.literal("The server rejects deletion while other definitions still reference it.")));
    }
    public static void edit(MasteryScreen parent, String id) {
        if (!ClientState.editMode()) return;
        returnTo = parent;
        pendingId = id;
        pendingKind = ClientState.definitions().trees().containsKey(id) ? "trees" : "nodes";
        status = "Loading definition..."; failed = false;
        MasteryNetwork.sendAction("editor_get", id, pendingKind, 0);
    }
    public static void editDefaults(MasteryScreen parent) {
        if(!ClientState.editMode())return;
        returnTo=parent;pendingId="mastery:defaults";pendingKind="settings";status="Loading defaults...";failed=false;
        MasteryNetwork.sendAction("editor_get",pendingId,pendingKind,0);
    }
    public static void editSpell(MasteryScreen parent,String spell) {
        if(!ClientState.editMode())return;
        returnTo=parent;pendingId=spell;pendingKind="spells";status="Loading spell upgrade settings...";failed=false;
        MasteryNetwork.sendAction("editor_get",spell,"spells",0);
    }
    public static void addTree(MasteryScreen parent) {
        if (!ClientState.editMode()) return;
        new DefinitionEditorScreen(parent, "trees", "mastery:new_tree", EditorDrafts.tree(), ClientState.definitionRevision(), true).openForm();
    }
    public static void addChild(MasteryScreen parent, String parentId) {
        if (!ClientState.editMode()) return;
        boolean tree = ClientState.definitions().trees().containsKey(parentId);
        var definition = ClientState.definitions().nodes().get(parentId);
        if (!tree && definition == null) return;
        String treeId = tree ? parentId : definition.tree();
        new DefinitionEditorScreen(parent, "nodes", treeId + "/new_skill", EditorDrafts.node(treeId, tree ? "" : parentId), ClientState.definitionRevision(), true).openForm();
    }
    public static void delete(MasteryScreen parent, String id) {
        if (!ClientState.editMode()) return;
        String kind = ClientState.definitions().trees().containsKey(id) ? "trees" : "nodes";
        Minecraft.getInstance().setScreen(new ConfirmScreen(confirmed -> {
            Minecraft.getInstance().setScreen(parent);
            if (confirmed && ClientState.editMode()) {
                status = "Deleting definition..."; failed = false;
                MasteryNetwork.sendAction("editor_delete", id, kind, 0);
            }
        }, Component.literal("Delete " + ClientState.name(id) + "?"), Component.literal("This changes the world's datapack for every player. The server rejects deletion while other definitions refer to it. Remove those links first.")));
    }
    public static void acceptDefinition(String text) {
        try {
            JsonObject json = JsonParser.parseString(text).getAsJsonObject();
            String id = json.get("id").getAsString(), kind = json.get("kind").getAsString();
            if (!ClientState.editMode() || !id.equals(pendingId) || !kind.equals(pendingKind) || Minecraft.getInstance().screen != returnTo) return;
            pendingId = pendingKind = "";
            new DefinitionEditorScreen(returnTo == null ? new MasteryScreen() : returnTo,
                    kind, id, json.getAsJsonObject("definition"), json.has("revision") ? json.get("revision").getAsLong() : ClientState.definitionRevision(), false).openForm();
            status = "";
        } catch (RuntimeException exception) {
            status = "Could not read the server's definition."; failed = true;
            LogUtils.getLogger().error("Invalid Mastery editor response", exception);
        }
    }
    public static void acceptStatus(String text) {
        try {
            JsonObject json = JsonParser.parseString(text).getAsJsonObject();
            failed = !json.get("success").getAsBoolean();
            status = json.get("message").getAsString();
            if (Minecraft.getInstance().screen instanceof DefinitionEditorScreen screen) screen.serverStatus(!failed, status);
            else if(Minecraft.getInstance().screen instanceof VisualDefinitionScreen screen)screen.serverStatus(!failed,status);
            else if(Minecraft.getInstance().screen instanceof VisualScriptScreen screen)screen.serverStatus(!failed,status);
        } catch (RuntimeException exception) { LogUtils.getLogger().error("Invalid Mastery editor status", exception); }
    }
    public static void clear() { pendingId = pendingKind = status = ""; returnTo = null; failed = false; }
}
