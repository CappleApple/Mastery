package com.cappleapple.mastery.client.validation;

import com.cappleapple.mastery.client.*;
import com.cappleapple.mastery.client.gui.*;
import com.cappleapple.mastery.client.gui.editor.*;
import com.cappleapple.mastery.layout.*;
import com.cappleapple.mastery.network.MasteryNetwork;
import com.cappleapple.mastery.spells.BindingSlots;
import com.google.gson.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import org.lwjgl.glfw.GLFW;
import java.nio.file.*;
import java.util.*;

/** Opt-in real client UI verification. Excluded from distribution JARs. */
@EventBusSubscriber(modid="mastery",value=Dist.CLIENT)
public final class FeatureSmoke {
    private static final boolean ENABLED=Boolean.getBoolean("mastery.featureSmoke");
    private static final Path OUT=Path.of("../build/feature-smoke").toAbsolutePath();
    private static final String NODE="mastery:fireball",TREE="mastery:fire",SPELL="irons_spellbooks:fireball";
    private static final JsonObject REPORT=new JsonObject();
    private static final List<Float> DINGS=new ArrayList<>();
    private static int ticks,age,beacons,levelSounds;private static String phase="init";private static boolean done;
    private static int previousRenderTick=-1,renderFrames;
    private static double previousPan,previousHold;
    private static boolean subtickFocus,subtickHold;
    @SubscribeEvent public static void rendered(net.neoforged.neoforge.client.event.ScreenEvent.Render.Post event) {
        if(!ENABLED||done||!(event.getScreen() instanceof MasteryScreen))return;
        renderFrames++;
        try {
            var method=MasteryScreen.class.getDeclaredMethod("holdVisualProgress");method.setAccessible(true);
            double progress=(double)method.invoke(event.getScreen()),pan=ClientState.view().panX;
            if(previousRenderTick==ticks) {
                if(Math.abs(pan-previousPan)>.001)subtickFocus=true;
                if(phase.equals("hold_vertical")&&progress>0&&progress<1&&progress>previousHold)subtickHold=true;
            }
            previousRenderTick=ticks;previousPan=pan;previousHold=progress;
        }catch(Exception error){complete(Minecraft.getInstance(),error);}
    }
    private static double x,y;private static long revision;private static MasteryScreen map;
    @SubscribeEvent public static void sound(PlaySoundEvent event) {
        if(!ENABLED||done)return;String id=event.getOriginalSound().getLocation().toString();
        if(id.equals("minecraft:entity.experience_orb.pickup")){event.getOriginalSound().resolve(Minecraft.getInstance().getSoundManager());DINGS.add(event.getOriginalSound().getPitch());}
        if(id.equals("minecraft:block.beacon.power_select"))beacons++;
        if(id.equals("minecraft:entity.player.levelup"))levelSounds++;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!ENABLED||done)return;var mc=Minecraft.getInstance();ticks++;age++;
        try {
            if(ticks==1) {
                Files.createDirectories(OUT);mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
                mc.options.pauseOnLostFocus=false;mc.options.framerateLimit().set(60);mc.options.guiScale().set(2);mc.resizeDisplay();mc.options.save();
                GLFW.glfwSetWindowAttrib(mc.getWindow().getWindow(),GLFW.GLFW_FOCUS_ON_SHOW,GLFW.GLFW_FALSE);GLFW.glfwHideWindow(mc.getWindow().getWindow());
            }
            mc.mouseHandler.releaseMouse();if(!phase.equals("level_toast"))mc.getToasts().clear();
            if(ticks>6000)throw new IllegalStateException("Timeout: "+phase);
            if(mc.screen instanceof DisconnectedScreen)throw new IllegalStateException("Disconnected: "+phase);
            switch(phase) {
                case "init" -> {if(age>30&&mc.getOverlay()==null){var address=ServerAddress.parseString("127.0.0.1:25578");ConnectScreen.startConnecting(new TitleScreen(),mc,address,new ServerData("Mastery validation","127.0.0.1:25578",ServerData.Type.OTHER),false,null);next("connect");}}
                case "connect" -> {if(mc.player!=null&&ClientState.definitions().nodes().containsKey(NODE)&&ClientState.runtime().has("world_id")) {
                    if(mc.player.isDeadOrDying()){mc.player.respawn();break;}
                    for(String command:List.of("mana set @s 1000","clearCooldowns player @s","mastery edit_mode false","mastery reset all @s","mastery points add @s mastery:fire 20","mastery points add @s mastery:two_handed 20","mastery node rank @s mastery:fireball 1","mastery node rank @s mastery:fireball/efficiency 1","mastery node rank @s mastery:fire/foundation 1","mastery node rank @s mastery:two_handed/foundation 1"))mc.getConnection().sendCommand(command);
                    next("fixture");
                }}
                case "fixture" -> {if(age>25&&ClientState.progress().rank(NODE)==1&&ClientState.capacity()>0){check(ClientState.nodeEnabled(NODE),"New active node enabled");check(ClientState.nodeEnabled("mastery:fireball/efficiency"),"New modifier enabled");REPORT.addProperty("new_nodes_enabled",true);center(mc,NODE);next("ready_hold");}}
                case "ready_hold" -> {if(age>12){point(NODE);DINGS.clear();beacons=0;map.mouseClicked(x,y,0);next("hold_vertical");}}
                case "hold_vertical" -> {
                    if(age==1){check(DINGS.isEmpty(),"No dings before hold delay");capture(mc,"00-hold-delay.png");}
                    if(age==22)capture(mc,"01-hold-vertical.png");
                    if(age>=42&&ClientState.progress().rank(NODE)==2){map.mouseReleased(x,y,0);check(DINGS.size()==8&&beacons==1,"Eight progress dings then one beacon power select");for(int i=1;i<8;i++)check(DINGS.get(i)>DINGS.get(i-1),"Rising ding pitch");REPORT.add("ding_pitches",new Gson().toJsonTree(DINGS));REPORT.addProperty("hold_unlock",true);center(mc,NODE);next("selection");}
                }
                case "selection" -> {if(age>12){point(NODE);boolean before=ClientState.nodeEnabled(NODE);map.mouseClicked(x,y,0);map.mouseReleased(x,y,0);check(ClientState.progress().rank(NODE)==2&&before==ClientState.nodeEnabled(NODE),"Left click only selects");
                    check(NodeDetails.tooltip(TREE).size()==4,"Root has four-line progression tooltip");
                    check(NodeDetails.tooltip(NODE).stream().noneMatch(c->c.getString().startsWith("XP ")||c.getString().startsWith("Points:")),"Child tooltip excludes tree progress");
                    check(NodeDetails.tooltip("mastery:fire/foundation").stream().anyMatch(c->c.getString().contains("+5%")),"Stat bonus tooltip");
                    String guide=io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell(SPELL).getComponentId()+".guide";
                    check(NodeDetails.describe(NODE).stream().anyMatch(c->c.getString().equals(net.minecraft.client.resources.language.I18n.get(guide))),"Native Scroll Forge description in details");
                    check(map.children().stream().filter(Button.class::isInstance).map(Button.class::cast).noneMatch(b->b.getMessage().getString().equals("Center")),"Center button removed");
                    check(map.keyPressed(GLFW.GLFW_KEY_F,0,0),"Focus shortcut handled");
                    var focus=MasteryScreen.class.getDeclaredField("targetX");focus.setAccessible(true);check(focus.get(map)!=null,"Focus shortcut starts centering");
                    REPORT.addProperty("focus_shortcut",true);REPORT.addProperty("compact_tooltips",true);point(NODE);map.mouseClicked(x,y,1);check(!ClientState.view().expanded.contains(NODE),"Right click collapses");check(!ClientState.visible().contains("mastery:fireball/efficiency"),"Modifier hidden after collapse");next("collapse");}}
                case "collapse" -> {if(age==2)capture(mc,"02-collapse-animation.png");if(age>15){point(NODE);map.mouseClicked(x,y,1);check(ClientState.view().expanded.contains(NODE),"Right click expands");verifyUnseparatedAnimation();next("expand");}}
                case "expand" -> {if(age>15){capture(mc,"03-expanded-types.png");check(ClientState.visible().contains("mastery:fireball/efficiency"),"Modifier visible after expansion");point(NODE);map.mouseClicked(x,y,2);map.mouseReleased(x,y,2);next("disabled");}}
                case "disabled" -> {if(!ClientState.nodeEnabled(NODE)){capture(mc,"04-disabled-parent.png");check(ClientState.progress().rank("mastery:fireball/efficiency")==1,"Child investment retained");point(NODE);map.mouseClicked(x,y,2);map.mouseReleased(x,y,2);REPORT.addProperty("middle_click_toggle",true);mc.getConnection().sendCommand("mastery edit_mode true");next("editor");}}
                case "editor" -> {if(ClientState.editMode()){map=new MasteryScreen();mc.setScreen(map);EditorClient.edit(map,TREE);next("editor_form");}}
                case "editor_form" -> {if(mc.screen instanceof VisualDefinitionScreen&&age>8){
                    capture(mc,"11-visual-editor.png");openField(mc,"theme");editValue(mc,"inner_color","#55CCFF");editValue(mc,"outer_color","#153866");editValue(mc,"gradient","0.4");button(mc,"Done");
                    openField(mc,"unlock");openField(mc,"fill_direction");button(mc,"Choose...");button(mc,"horizontal");button(mc,"Apply");button(mc,"Done");
                    openField(mc,"appearance");openField(mc,"shape");button(mc,"Choose...");button(mc,"hexagon");button(mc,"Apply");button(mc,"Done");
                    button(mc,"JSON editor");next("json_editor");
                }}
                case "json_editor" -> {if(age>6){capture(mc,"12-json-editor.png");button(mc,"Format JSON");button(mc,"Visual editor");revision=ClientState.definitionRevision();button(mc,"Save to world");next("saved_theme");}}
                case "saved_theme" -> {if(ClientState.definitionRevision()>revision&&mc.screen instanceof MasteryScreen){check(ClientState.definitions().trees().get(TREE).theme().innerColor().equals("#55CCFF"),"Theme saved and synchronized");mc.getConnection().sendCommand("mastery edit_mode false");next("leave_editor");}}
                case "leave_editor" -> {if(!ClientState.editMode()){center(mc,NODE);next("ready_horizontal");}}
                case "ready_horizontal" -> {if(age>12){point(NODE);map.mouseClicked(x,y,0);next("hold_horizontal");}}
                case "hold_horizontal" -> {if(age==22)capture(mc,"06-hold-horizontal.png");if(age>=42&&ClientState.progress().rank(NODE)==3){map.mouseReleased(x,y,0);MasteryNetwork.sendAction("binding_mode","hotbar","",0);next("hotbar_mode");}}
                case "hotbar_mode" -> {if(ClientState.progress().bindingMode().equals("hotbar")){MasteryNetwork.sendAction("equip",SPELL,BindingSlots.hotbar(0),0);next("bound");}}
                case "bound" -> {if(BindingSlots.get(ClientState.progress().loadouts(),BindingSlots.hotbar(0),0).equals(SPELL)){mc.player.getInventory().selected=0;mc.getConnection().send(new ServerboundSetCarriedItemPacket(0));mc.setScreen(new SpellBindingsScreen(map,""));next("grid");}}
                case "grid" -> {if(age>15){check(mc.screen.children().stream().filter(NativeSpellSlot.class::isInstance).count()==4,"Four slots shown per hotbar page");capture(mc,"07-hotbar-grid.png");
                    var slot=mc.screen.children().stream().filter(NativeSpellSlot.class::isInstance).map(NativeSpellSlot.class::cast).findFirst().orElseThrow();mc.screen.mouseClicked(slot.getX()+5,slot.getY()+5,0);next("picker");}}
                case "picker" -> {if(age>10){capture(mc,"17-spell-picker.png");button(mc,"Mode: Hotbar sets");next("quick_mode");}}
                case "quick_mode" -> {if(ClientState.progress().bindingMode().equals("quick_cast")){MasteryNetwork.sendAction("equip",SPELL,BindingSlots.QUICK,0);next("quick_grid");}}
                case "quick_grid" -> {if(age>20&&BindingSlots.get(ClientState.progress().loadouts(),BindingSlots.QUICK,0).equals(SPELL)){capture(mc,"08-quick-grid.png");check(NativeSpellHud.view(io.redspace.ironsspellbooks.player.ClientMagicData.getSpellSelectionManager()).getSpellCount()==1,"Quick HUD contains shared set");MasteryNetwork.sendAction("binding_mode","hotbar","",0);mc.player.getInventory().selected=0;mc.getConnection().send(new ServerboundSetCarriedItemPacket(0));mc.setScreen(null);next("hud");}}
                case "hud" -> {if(age>20){check(NativeSpellHud.view(io.redspace.ironsspellbooks.player.ClientMagicData.getSpellSelectionManager()).getSpellCount()==1,"Native HUD active hotbar filter");capture(mc,"09-native-hud.png");MasteryNetwork.sendAction("press","","",0);next("charge_hud");}}
                case "charge_hud" -> {if(age==35){check(io.redspace.ironsspellbooks.player.ClientMagicData.isCasting(),"Native charge started");capture(mc,"18-charge-percent.png");var charge=ChargeHud.class.getDeclaredField("window");charge.setAccessible(true);check(charge.get(null)!=null,"Authoritative charge markers received");REPORT.addProperty("segmented_charge_hud",true);MasteryNetwork.sendAction("release","","",0);}if(age>65){mc.player.getInventory().selected=1;mc.getConnection().send(new ServerboundSetCarriedItemPacket(1));next("empty_hud");}}
                case "empty_hud" -> {if(age>20){check(NativeSpellHud.view(io.redspace.ironsspellbooks.player.ClientMagicData.getSpellSelectionManager()).getSpellCount()==0,"Inactive hotbar spells excluded");REPORT.addProperty("native_hud_filter",true);REPORT.addProperty("right_click_branches",true);REPORT.addProperty("theme_editor",true);levelSounds=0;mc.getConnection().sendCommand("mastery xp add @s mastery:fire 100");next("level_toast");}}
                case "level_toast" -> {if(age>25&&levelSounds>0){capture(mc,"10-level-up-toast.png");REPORT.addProperty("level_up_toast_sound",true);center(mc,NODE);next("zoom_out");}}
                case "zoom_out" -> {if(age>12){map.mouseScrolled((field("left")+field("right"))/2.0,(field("top")+field("bottom"))/2.0,0,-200);next("zoom_check");}}
                case "zoom_check" -> {if(age>15){
                    check(ClientState.view().zoom==ReadableZoom.MIN_ZOOM,"Deep zoom floor");
                    var positions=positions();var list=new ArrayList<>(positions.values());double zoom=ClientState.view().zoom;
                    double scale=Math.max(zoom,com.cappleapple.mastery.config.MasteryClientConfig.MINIMUM_NODE_WIDTH.get()/40.0);
                    for(int i=0;i<list.size();i++)for(int j=i+1;j<list.size();j++)check(Math.abs(list.get(i).x()-list.get(j).x())*zoom>=52*scale+3.99||Math.abs(list.get(i).y()-list.get(j).y())*zoom>=52*scale+3.99,"No node overlaps at distant zoom");
                    capture(mc,"13-deep-zoom.png");ClientState.view().selected=NODE;map.keyPressed(GLFW.GLFW_KEY_F,0,0);next("zoom_focus");
                }}
                case "zoom_focus" -> {if(age>20){point(NODE);check(x>=field("left")&&x<field("right")&&y>=field("top")&&y<field("bottom"),"Focused displayed node inside viewport");map.mouseClicked(x,y,0);map.mouseReleased(x,y,0);check(ClientState.view().selected.equals(NODE),"Deep zoom hit testing");capture(mc,"14-deep-zoom-focus.png");REPORT.addProperty("deep_zoom_no_overlap",true);REPORT.addProperty("visual_editor_round_trip",true);mc.getConnection().sendCommand("mastery edit_mode true");next("nested_editor");}}
                case "nested_editor" -> {if(ClientState.editMode()){map=new MasteryScreen();mc.setScreen(map);EditorClient.addChild(map,TREE);openField(mc,"dependencies");button(mc,"Add entry");prefixButton(mc,"Entry 1:");openField(mc,"node");button(mc,"Choose...");mc.screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow().setValue("mastery:fire/foundation");button(mc,"mastery:fire/foundation");button(mc,"Apply");editValue(mc,"rank","3");button(mc,"Done");button(mc,"Add entry");prefixButton(mc,"Entry 2:");editValue(mc,"node",NODE);button(mc,"Done");button(mc,"^");button(mc,"Add OR");prefixButton(mc,"Entry 3:");openField(mc,"or");prefixButton(mc,"Entry 1:");editValue(mc,"node","mastery:two_handed/foundation");button(mc,"Done");button(mc,"Add entry");prefixButton(mc,"Entry 2:");editValue(mc,"node","mastery:fire/foundation");button(mc,"Done");button(mc,"Done");button(mc,"Done");button(mc,"Done");button(mc,"Require Skill Book: Off");button(mc,"Copy book command");button(mc,"JSON editor");next("nested_json");}}
                case "nested_json" -> {if(age>6){
                    var text=mc.screen.children().stream().filter(MultiLineEditBox.class::isInstance).map(MultiLineEditBox.class::cast).findFirst().orElseThrow().getValue();var json=JsonParser.parseString(text).getAsJsonObject();
                    check(json.getAsJsonArray("dependencies").get(0).getAsJsonObject().get("node").getAsString().equals(NODE),"List reordering preserved");
                    check(json.getAsJsonArray("dependencies").get(1).getAsJsonObject().get("rank").getAsInt()==3,"Typed dependency ranks preserved");check(!json.get("book_token").getAsString().isBlank(),"Visual book toggle");
                    check(json.getAsJsonArray("dependencies").get(2).getAsJsonObject().getAsJsonArray("or").size()==2,"Visual OR prerequisite group preserved");REPORT.addProperty("grouped_prerequisite_editor",true);capture(mc,"15-nested-draft.png");button(mc,"Cancel");button(mc,"Yes");button(mc,"Definitions");next("library");
                }}
                case "library" -> {if(age>8){capture(mc,"16-definition-library.png");check(mc.screen instanceof EditorLibraryScreen,"Definition library opened");REPORT.addProperty("nested_editor_lists",true);
                    check(subtickFocus,"Focus moved between game ticks");check(subtickHold,"Hold fill moved between game ticks");
                    REPORT.addProperty("subtick_focus_motion",subtickFocus);REPORT.addProperty("subtick_hold_fill",subtickHold);REPORT.addProperty("tree_render_frames",renderFrames);
                    mc.getConnection().sendCommand("mastery edit_mode false");complete(mc,null);}}
            }
        }catch(Exception error){complete(mc,error);}
    }
    @SuppressWarnings("unchecked") private static void verifyUnseparatedAnimation()throws Exception {
        var field=MasteryScreen.class.getDeclaredField("animationPositions");field.setAccessible(true);
        var raw=(Map<String,GraphLayout.Point>)field.get(map);
        check(positions().equals(raw),"Expansion uses unseparated interpolated positions");
        var parent=raw.get(NODE);var child=raw.get("mastery:fireball/efficiency");
        check(parent!=null&&child!=null&&Math.hypot(parent.x()-child.x(),parent.y()-child.y())<40,"Expanding child starts overlapping its parent");
        REPORT.addProperty("expansion_overlap",true);
    }
    private static void center(Minecraft mc,String id) {
        ClientState.reveal(id);ClientState.view().expanded.add(id);ClientState.view().selected="";ClientState.view().zoom=.9;
        var p=ClientState.layout().anchors().get(id);ClientState.view().panX=-p.x()*.9;ClientState.view().panY=-p.y()*.9;ClientState.view().cameraInitialized=true;
        map=new MasteryScreen();mc.setScreen(map);
    }
    private static int field(String name)throws Exception{var field=MasteryScreen.class.getDeclaredField(name);field.setAccessible(true);return field.getInt(map);}
    private static void point(String id)throws Exception {
        var p=positions().getOrDefault(id,ClientState.layout().anchors().get(id));x=(field("left")+field("right"))/2.0+ClientState.view().panX+p.x()*ClientState.view().zoom;
        y=(field("top")+field("bottom"))/2.0+ClientState.view().panY+p.y()*ClientState.view().zoom;
    }
    @SuppressWarnings("unchecked") private static Map<String,GraphLayout.Point> positions()throws Exception{var field=MasteryScreen.class.getDeclaredField("displayPositions");field.setAccessible(true);return (Map<String,GraphLayout.Point>)field.get(map);}
    private static void openField(Minecraft mc,String key){
        mc.screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).max(Comparator.comparingInt(EditBox::getY)).orElseThrow().setValue(key);
        var b=mc.screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(v->v.getMessage().getString().startsWith(EditorSchema.label(key)+":")).findFirst().orElseThrow();
        mc.screen.mouseClicked(b.getX()+10,b.getY()+10,0);
    }
    private static void editValue(Minecraft mc,String key,String value){openField(mc,key);mc.screen.children().stream().filter(MultiLineEditBox.class::isInstance).map(MultiLineEditBox.class::cast).findFirst().orElseThrow().setValue(value);button(mc,"Apply");}
    private static void prefixButton(Minecraft mc,String prefix){var b=mc.screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(v->v.getMessage().getString().startsWith(prefix)).findFirst().orElseThrow();mc.screen.mouseClicked(b.getX()+10,b.getY()+10,0);}
    private static void button(Minecraft mc,String name) {
        var button=mc.screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(b->b.getMessage().getString().equals(name)).findFirst().orElseThrow(()->new IllegalStateException("Missing button: "+name));
        check(button.active,"Button active: "+name);mc.screen.mouseClicked(button.getX()+button.getWidth()/2.0,button.getY()+10,0);mc.screen.mouseReleased(button.getX()+button.getWidth()/2.0,button.getY()+10,0);
    }
    private static void capture(Minecraft mc,String file)throws Exception{try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(OUT.resolve(file));}}
    private static void next(String value)throws Exception{phase=value;age=0;Files.writeString(OUT.resolve("checkpoint.json"),"{\"phase\":\""+phase+"\"}");}
    private static void check(boolean valid,String message){if(!valid)throw new IllegalStateException(message);}
    private static void complete(Minecraft mc,Exception error) {
        done=true;REPORT.addProperty("success",error==null);REPORT.addProperty("phase",phase);REPORT.addProperty("ticks",ticks);REPORT.addProperty("muted",true);REPORT.addProperty("hidden",true);
        if(error!=null)REPORT.addProperty("error",error.toString());
        try{Files.writeString(OUT.resolve("result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(REPORT));if(error!=null)capture(mc,"failure.png");}catch(Exception ignored){}
        if(mc.player!=null)mc.disconnect(new TitleScreen());mc.stop();
    }
}
