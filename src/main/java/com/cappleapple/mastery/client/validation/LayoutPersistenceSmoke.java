package com.cappleapple.mastery.client.validation;

import com.cappleapple.mastery.client.*;
import com.cappleapple.mastery.client.gui.*;
import com.cappleapple.mastery.layout.GraphLayout;
import com.google.gson.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;
import java.nio.file.*;
import java.util.*;

/** Real client login, reconnect and fresh-process persistence regression; excluded from release JARs. */
@EventBusSubscriber(modid="mastery",value=Dist.CLIENT)
public final class LayoutPersistenceSmoke {
    private static final String MODE=System.getProperty("mastery.layoutSmoke","");
    private static final Path OUT=Path.of("../build/layout-persistence-smoke").toAbsolutePath();
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final JsonObject REPORT=new JsonObject();
    private static String phase="init";
    private static int ticks,age;
    private static boolean done;
    private LayoutPersistenceSmoke() {}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(MODE.isBlank()||done)return;
        var mc=Minecraft.getInstance();ticks++;age++;
        try {
            if(ticks==1) {
                Files.createDirectories(OUT);mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
                mc.options.pauseOnLostFocus=false;mc.options.framerateLimit().set(60);mc.options.guiScale().set(2);mc.resizeDisplay();mc.options.save();
                GLFW.glfwSetWindowAttrib(mc.getWindow().getWindow(),GLFW.GLFW_FOCUS_ON_SHOW,GLFW.GLFW_FALSE);GLFW.glfwHideWindow(mc.getWindow().getWindow());
                // Early UI access must never cache a layout before the world/player identity arrives.
                ClientState.view();
            }
            mc.mouseHandler.releaseMouse();mc.getToasts().clear();
            if(ticks>1800)throw new IllegalStateException("Timeout: "+phase);
            if(mc.screen instanceof DisconnectedScreen)throw new IllegalStateException("Disconnected: "+phase);
            switch(phase) {
                case "init" -> {if(age>30&&mc.getOverlay()==null){connect(mc);next("joined");}}
                case "joined" -> {if(ready(mc)&&age>20) {
                    check(!ClientState.editMode(),"Normal player mode");verifyHotbarControls(mc);
                    if(MODE.equals("restore")) {verify();mc.setScreen(new MasteryScreen());verify();REPORT.addProperty("fresh_client_process_restores_layout",true);complete(mc,null);}
                    else {
                        mc.setScreen(new MasteryScreen());
                        var root=ClientState.layout().anchors().get("mastery:fire");check(root!=null,"Fire root fixture");
                        ClientState.moveSubtree("mastery:fire",876-root.x(),-456-root.y());
                        ClientState.orientTree("mastery:fire",GraphLayout.sectionAt(876,-456));
                        ClientState.layout();ClientState.moveSubtree("mastery:fireball",41,63);
                        check(ClientState.layout().anchors().containsKey("mastery:spellblade_practice"),"Promoted root fixture");
                        ClientState.moveSubtree("mastery:spellblade_practice",-81,53);
                        var view=ClientState.view();view.expanded.clear();view.expanded.addAll(Set.of("mastery:fire","mastery:two_handed"));
                        view.panX=157;view.panY=-83;view.zoom=.73;view.cameraInitialized=true;view.showSectors=false;view.selected="mastery:fireball";
                        Files.writeString(OUT.resolve("expected.json"),JSON.toJson(snapshot()));
                        mc.setScreen(null); // Closing the map must persist without an explicit test-only save call.
                        var path=layoutFile();check(path!=null&&Files.isRegularFile(path),"Missing persistent layout path after normal player login");
                        REPORT.addProperty("player_layout_written_on_screen_close",true);
                        // Clear only the replica to model a fresh packet handshake in the same world.
                        mc.disconnect(new TitleScreen());next("reconnect_wait");
                    }
                }}
                case "reconnect_wait" -> {if(age>25&&mc.player==null&&mc.getOverlay()==null){connect(mc);next("rejoined");}}
                case "rejoined" -> {if(ready(mc)&&age>20){verify();mc.setScreen(new MasteryScreen());verify();REPORT.addProperty("reconnect_restores_custom_nodes_roots_and_view",true);complete(mc,null);}}
            }
        }catch(Exception error){complete(mc,error);}
    }
    private static void verifyHotbarControls(Minecraft mc) {
        String mode=ClientState.progress().bindingMode();var capacity=ClientState.runtime().get("capacity");int page=ClientState.bindingPage;var screen=mc.screen;
        try {
            ClientState.progress().bindingMode("hotbar");ClientState.runtime().addProperty("capacity",64);ClientState.bindingPage=3;
            for(int i=0;i<4;i++)check(ClientState.inputSlot(i)==i,"Hotbar keys never address another page");
            ClientState.cycleBindingPage(1);check(ClientState.bindingPage==0,"Hotbar page keys do nothing");
            mc.setScreen(new SpellBindingsScreen(null,""));
            check(mc.screen.children().stream().filter(c->c instanceof NativeSpellSlot).count()==4,"Exactly four hotbar slot widgets with 64 capacity");
            check(mc.screen.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.Button).map(c->((net.minecraft.client.gui.components.Button)c).getMessage().getString()).noneMatch(t->t.equals("<")||t.equals(">")),"Hotbar slot page arrows removed");
            REPORT.addProperty("four_hotbar_slots_no_page_controls",true);
        }finally{ClientState.progress().bindingMode(mode);if(capacity==null)ClientState.runtime().remove("capacity");else ClientState.runtime().add("capacity",capacity);ClientState.bindingPage=page;mc.setScreen(screen);}
    }
    private static JsonObject snapshot() {
        var json=new JsonObject();json.add("anchors",JSON.toJsonTree(ClientState.layout().anchors()));
        var view=ClientState.view();json.add("offsets",JSON.toJsonTree(view.offsets));json.add("manual",JSON.toJsonTree(new TreeSet<>(view.manualAnchors)));
        json.add("expanded",JSON.toJsonTree(new TreeSet<>(view.expanded)));json.add("orientations",JSON.toJsonTree(view.orientations));
        json.addProperty("pan_x",view.panX);json.addProperty("pan_y",view.panY);json.addProperty("zoom",view.zoom);json.addProperty("selected",view.selected);json.addProperty("sectors",view.showSectors);
        json.addProperty("world",ClientState.runtime().get("world_id").getAsString());return json;
    }
    private static void verify()throws Exception {
        check(!ClientState.editMode(),"Player layout outside edit mode");
        var expected=JsonParser.parseString(Files.readString(OUT.resolve("expected.json"))).getAsJsonObject();var actual=snapshot();
        for(String group:List.of("anchors","offsets")) {
            var before=expected.getAsJsonObject(group);var after=actual.getAsJsonObject(group);check(before.keySet().equals(after.keySet()),group+" node set");
            for(String id:before.keySet())for(String axis:List.of("x","y"))check(Math.abs(before.getAsJsonObject(id).get(axis).getAsDouble()-after.getAsJsonObject(id).get(axis).getAsDouble())<1e-6,group+" persisted: "+id+"/"+axis);
        }
        for(String key:List.of("manual","expanded","orientations","pan_x","pan_y","zoom","selected","sectors","world"))check(expected.get(key).equals(actual.get(key)),"Persisted "+key);
        check(layoutFile()!=null&&Files.isRegularFile(layoutFile()),"Scoped layout file loaded");
    }
    private static Path layoutFile()throws Exception {var f=LayoutPreferences.class.getDeclaredField("file");f.setAccessible(true);return (Path)f.get(ClientState.view());}
    private static boolean ready(Minecraft mc){return mc.player!=null&&ClientState.runtime().has("world_id")&&!ClientState.definitions().nodes().isEmpty()&&!ClassSelectionScreen.required();}
    private static void connect(Minecraft mc){ConnectScreen.startConnecting(new TitleScreen(),mc,ServerAddress.parseString("127.0.0.1:25578"),new ServerData("Mastery layout validation","127.0.0.1:25578",ServerData.Type.OTHER),false,null);}
    private static void next(String value){phase=value;age=0;}
    private static void check(boolean valid,String message){if(!valid)throw new IllegalStateException(message);}
    private static void complete(Minecraft mc,Exception error){done=true;REPORT.addProperty("success",error==null);REPORT.addProperty("phase",phase);REPORT.addProperty("muted",true);REPORT.addProperty("hidden",true);if(error!=null)REPORT.addProperty("error",error.toString());try{Files.writeString(OUT.resolve(MODE+"-result.json"),JSON.toJson(REPORT));}catch(Exception ignored){}if(mc.player!=null)mc.disconnect(new TitleScreen());mc.stop();}
}
