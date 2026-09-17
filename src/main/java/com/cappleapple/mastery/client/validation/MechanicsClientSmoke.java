package com.cappleapple.mastery.client.validation;

import com.cappleapple.mastery.client.ClientState;
import com.cappleapple.mastery.client.gui.MasteryScreen;
import com.cappleapple.mastery.client.gui.editor.*;
import com.cappleapple.mastery.network.MasteryNetwork;
import com.google.gson.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;
import java.nio.file.*;
import java.util.*;

/** Opt-in actual client card authoring and Patchouli visibility check; excluded from release JARs. */
@EventBusSubscriber(modid="mastery",value=Dist.CLIENT)
public final class MechanicsClientSmoke {
    private static final boolean ENABLED=Boolean.getBoolean("mastery.mechanicsSmoke");
    private static final Path OUT=Path.of("../build/mechanics-smoke").toAbsolutePath();
    private static final String TRIGGER="mastery:smoke/visual_trigger",KEYWORD="mastery:smoke/visual_keyword",COST="mastery:smoke/visual_cost";
    private static final JsonObject REPORT=new JsonObject();
    private static String phase="init";
    private static int ticks,age;
    private static long revision;
    private static boolean done;
    private static JsonObject originalTree;
    private static double outlineXp;
    private MechanicsClientSmoke() {}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!ENABLED||done)return;
        Minecraft mc=Minecraft.getInstance();ticks++;age++;
        try {
            if(ticks==1) {
                Files.createDirectories(OUT);mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
                mc.options.pauseOnLostFocus=false;mc.options.framerateLimit().set(60);mc.options.guiScale().set(2);mc.resizeDisplay();mc.options.save();
                GLFW.glfwSetWindowAttrib(mc.getWindow().getWindow(),GLFW.GLFW_FOCUS_ON_SHOW,GLFW.GLFW_FALSE);
                GLFW.glfwHideWindow(mc.getWindow().getWindow());
            }
            mc.mouseHandler.releaseMouse();mc.getToasts().clear();
            if(ticks>3000)throw new IllegalStateException("Timeout: "+phase);
            if(mc.screen instanceof DisconnectedScreen)throw new IllegalStateException("Disconnected: "+phase);
            switch(phase) {
                case "init" -> {if(age>30&&mc.getOverlay()==null){var address=ServerAddress.parseString("127.0.0.1:25578");ConnectScreen.startConnecting(new TitleScreen(),mc,address,new ServerData("Mastery mechanics validation","127.0.0.1:25578",ServerData.Type.OTHER),false,null);next("connect");}}
                case "connect" -> {if(mc.player!=null&&ClientState.runtime().has("world_id")){if(mc.player.isDeadOrDying()){mc.player.respawn();break;}mc.getConnection().sendCommand("mastery edit_mode false");next("ordinary_guide");}}
                case "ordinary_guide" -> {if(age>15&&!ClientState.editMode()){checkGuide(false);mc.getConnection().sendCommand("mastery edit_mode true");next("enable");}}
                case "enable" -> {if(age>15&&ClientState.editMode()){
                    checkGuide(true);REPORT.addProperty("guide_editor_entries",true);
                    if(ClientState.definitions().triggers().containsKey(TRIGGER)){MasteryNetwork.sendAction("editor_delete",TRIGGER,"triggers",0);next("cleanup_old");}
                    else next("cleanup_old");
                }}
                case "cleanup_old" -> {if(age>10&&!ClientState.definitions().triggers().containsKey(TRIGGER)){
                    if(ClientState.definitions().keywords().containsKey(KEYWORD))MasteryNetwork.sendAction("editor_delete",KEYWORD,"keywords",0);
                    if(ClientState.definitions().nodes().containsKey(COST))MasteryNetwork.sendAction("editor_delete",COST,"nodes",0);
                    next("create_trigger");
                }}
                case "create_trigger" -> {if(age>10&&!ClientState.definitions().keywords().containsKey(KEYWORD)){
                    new DefinitionEditorScreen(new MasteryScreen(),"triggers",TRIGGER,ScriptSchema.root("triggers"),ClientState.definitionRevision(),true).openForm();
                    button(mc,"+ Condition");button(mc,"health");next("condition");
                }}
                case "condition" -> {if(age>4&&mc.screen instanceof VisualDefinitionScreen){editValue(mc,"max","0.35");editValue(mc,"target","self");button(mc,"Done");button(mc,"+ Action");button(mc,"heal");next("action");}}
                case "action" -> {if(age>4&&mc.screen instanceof VisualDefinitionScreen){editValue(mc,"amount","3");editValue(mc,"target","self");button(mc,"Done");next("trigger_canvas");}}
                case "trigger_canvas" -> {if(age>6){check(mc.screen instanceof VisualScriptScreen,"Trigger card canvas");capture(mc,"01-trigger-cards.png");button(mc,"Settings");editValue(mc,"chance","0.25");editValue(mc,"cooldown","20");button(mc,"Back");button(mc,"JSON editor");next("trigger_json");}}
                case "trigger_json" -> {if(age>5){var json=currentJson(mc);check(json.getAsJsonArray("conditions").get(0).getAsJsonObject().get("max").getAsDouble()==0.35,"Health threshold preserved");check(json.getAsJsonArray("actions").get(0).getAsJsonObject().get("amount").getAsInt()==3,"Action amount preserved");check(json.get("chance").getAsDouble()==0.25,"Chance preserved");capture(mc,"02-trigger-json.png");button(mc,"Visual editor");revision=ClientState.definitionRevision();button(mc,"Save to world");next("saved_trigger");}}
                case "saved_trigger" -> {if(ClientState.definitionRevision()>revision&&mc.screen instanceof MasteryScreen){check(ClientState.definitions().triggers().get(TRIGGER).get("cooldown").getAsInt()==20,"Trigger saved and synchronized");REPORT.addProperty("trigger_visual_round_trip",true);
                    new DefinitionEditorScreen(new MasteryScreen(),"keywords",KEYWORD,ScriptSchema.root("keywords"),ClientState.definitionRevision(),true).openForm();button(mc,"+ Action");button(mc,"damage");next("keyword_tick");}}
                case "keyword_tick" -> {if(age>4&&mc.screen instanceof VisualDefinitionScreen){editValue(mc,"amount","0.5");openField(mc,"per_stack");button(mc,"Disabled");button(mc,"Apply");button(mc,"Done");button(mc,"Periodic actions");button(mc,"+ Action");button(mc,"particles");next("keyword_threshold");}}
                case "keyword_threshold" -> {if(age>4&&mc.screen instanceof VisualDefinitionScreen){editValue(mc,"particle","minecraft:explosion");button(mc,"Done");next("keyword_canvas");}}
                case "keyword_canvas" -> {if(age>6){capture(mc,"03-keyword-threshold.png");button(mc,"Threshold actions");next("keyword_periodic");}}
                case "keyword_periodic" -> {if(age>6){capture(mc,"04-keyword-periodic.png");revision=ClientState.definitionRevision();button(mc,"Save to world");next("saved_keyword");}}
                case "saved_keyword" -> {if(ClientState.definitionRevision()>revision&&mc.screen instanceof MasteryScreen){var keyword=ClientState.definitions().keywords().get(KEYWORD);check(keyword.getAsJsonArray("tick_actions").size()==1&&keyword.getAsJsonArray("threshold_actions").size()==1,"Both keyword lanes saved");REPORT.addProperty("keyword_visual_round_trip",true);
                    originalTree=ClientState.definitions().toJson().getAsJsonObject("trees").getAsJsonObject("mastery:fire").deepCopy();
                    new DefinitionEditorScreen(new MasteryScreen(),"trees","mastery:fire",originalTree,ClientState.definitionRevision(),false).openForm();
                    openField(mc,"icon");button(mc,"Choose...");check(mc.screen instanceof IconPickerScreen,"Texture browser opened");
                    mc.screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow().setValue("mastery:textures/gui/sprites/graph/rune.png");next("icon_picker");}}
                case "icon_picker" -> {if(age>6){capture(mc,"07-icon-browser.png");button(mc,"mastery:textures/gui/sprites/graph/rune.png");button(mc,"Apply");openField(mc,"appearance");editValue(mc,"shape","hexagon");button(mc,"Done");revision=ClientState.definitionRevision();button(mc,"Save to world");next("icon_saved");}}
                case "icon_saved" -> {if(ClientState.definitionRevision()>revision&&mc.screen instanceof MasteryScreen){check(ClientState.definitions().trees().get("mastery:fire").icon().equals("mastery:textures/gui/sprites/graph/rune.png"),"Texture icon saved");REPORT.addProperty("texture_icon_browser",true);outlineXp=ClientState.definitions().trees().get("mastery:fire").xpForLevel(0)/2.0;for(String command:List.of("mastery reset tree @s mastery:fire","mastery points add @s mastery:fire 1","mastery xp add @s mastery:fire "+Double.toString(outlineXp),"mastery edit_mode false"))mc.getConnection().sendCommand(command);next("xp_outline");}}
                case "xp_outline" -> {if(age>15&&!ClientState.editMode()&&Math.abs(ClientState.progress().trees().get("mastery:fire").xp()-outlineXp)<0.000001){
                    var position=ClientState.layout().anchors().get("mastery:fire");ClientState.view().zoom=2;ClientState.view().panX=-position.x()*2;ClientState.view().panY=-position.y()*2;ClientState.view().cameraInitialized=true;ClientState.view().selected="mastery:fire";mc.setScreen(new MasteryScreen());next("xp_capture");}}
                case "xp_capture" -> {if(age>8){capture(mc,"08-hexagon-xp-outline.png");var tree=ClientState.definitions().trees().get("mastery:fire");var progress=ClientState.progress().trees().get("mastery:fire");double fraction=com.cappleapple.mastery.layout.ShapeOutline.experience(progress.xp(),tree.xpForLevel(progress.level()),progress.level(),tree.levelCap(ClientState.worldTier()));check(fraction>0&&fraction<1,"Partial XP outline");REPORT.addProperty("root_xp_outline_fraction",fraction);REPORT.addProperty("root_xp_outline_shape","hexagon");mc.getConnection().sendCommand("mastery edit_mode true");next("restore_tree");}}
                case "restore_tree" -> {if(age>10&&ClientState.editMode()){revision=ClientState.definitionRevision();MasteryNetwork.sendAction("editor_save","mastery:fire",EditorDrafts.envelope("trees","mastery:fire",originalTree.toString(),revision),0);next("restored_tree");}}
                case "restored_tree" -> {if(ClientState.definitionRevision()>revision){
                    var costDraft=EditorDrafts.node("mastery:fire","");costDraft.addProperty("costs","default");
                    new DefinitionEditorScreen(new MasteryScreen(),"nodes",COST,costDraft,ClientState.definitionRevision(),true).openForm();
                    openField(mc,"costs");button(mc,"Use AND");openField(mc,"and");prefixButton(mc,"Entry 1:");editValue(mc,"amount","2");editValue(mc,"tree","mastery:fire");button(mc,"Done");
                    button(mc,"Add OR");prefixButton(mc,"Entry 2:");openField(mc,"or");prefixButton(mc,"Entry 1:");editValue(mc,"type","experience");editValue(mc,"amount","50");button(mc,"Done");
                    button(mc,"Add entry");prefixButton(mc,"Entry 2:");editValue(mc,"type","item");editValue(mc,"item","minecraft:diamond");editValue(mc,"amount","1");button(mc,"Done");next("cost_group");}}
                case "cost_group" -> {if(age>6){capture(mc,"09-cost-alternatives.png");button(mc,"Done");button(mc,"Done");button(mc,"Done");button(mc,"Done");editValue(mc,"cost_depth_percent","0.1");button(mc,"JSON editor");next("cost_json");}}
                case "cost_json" -> {if(age>6){var json=currentJson(mc);var costs=json.getAsJsonObject("costs").getAsJsonArray("and");check(costs.get(0).getAsJsonObject().get("amount").getAsInt()==2,"Points cost authored");check(costs.get(1).getAsJsonObject().getAsJsonArray("or").get(0).getAsJsonObject().get("type").getAsString().equals("experience"),"XP alternative authored");check(json.get("cost_depth_percent").getAsDouble()==0.1,"Depth scaling authored");capture(mc,"10-cost-json.png");button(mc,"Visual editor");revision=ClientState.definitionRevision();button(mc,"Save to world");next("cost_saved");}}
                case "cost_saved" -> {if(ClientState.definitionRevision()>revision&&mc.screen instanceof MasteryScreen){check(ClientState.definitions().nodes().containsKey(COST),"Cost node saved and synchronized");REPORT.addProperty("nested_cost_editor",true);openGuide();next("guide_open");}}
                case "guide_open" -> {if(age>10){if(ModList.get().isLoaded("patchouli")){capture(mc,"05-editor-guide.png");openWorkshopEntry("mastery:editor_elements",8);}next("workshop_elements");}}
                case "workshop_elements" -> {if(age>10){if(ModList.get().isLoaded("patchouli")){check(mc.screen.getClass().getName().equals("vazkii.patchouli.client.book.gui.GuiBookEntry"),"Workshop entry screen opened");capture(mc,"11-workshop-elements.png");openWorkshopEntry("mastery:editor_costs",2);}next("workshop_costs");}}
                case "workshop_costs" -> {if(age>10){if(ModList.get().isLoaded("patchouli")){check(mc.screen.getClass().getName().equals("vazkii.patchouli.client.book.gui.GuiBookEntry"),"Costs entry screen opened");capture(mc,"12-workshop-costs.png");REPORT.addProperty("guide_workshop_pages_captured",true);}mc.getConnection().sendCommand("mastery edit_mode false");next("guide_hidden");}}
                case "guide_hidden" -> {if(age>15&&!ClientState.editMode()){checkGuide(false);REPORT.addProperty("guide_hidden_after_disable",true);if(ModList.get().isLoaded("patchouli"))capture(mc,"06-player-guide.png");mc.getConnection().sendCommand("mastery edit_mode true");next("delete_trigger");}}
                case "delete_trigger" -> {if(age>10&&ClientState.editMode()){MasteryNetwork.sendAction("editor_delete",TRIGGER,"triggers",0);next("delete_keyword");}}
                case "delete_keyword" -> {if(age>10&&!ClientState.definitions().triggers().containsKey(TRIGGER)){MasteryNetwork.sendAction("editor_delete",KEYWORD,"keywords",0);next("delete_cost");}}
                case "delete_cost" -> {if(age>10&&!ClientState.definitions().keywords().containsKey(KEYWORD)){MasteryNetwork.sendAction("editor_delete",COST,"nodes",0);next("finish");}}
                case "finish" -> {if(age>10&&!ClientState.definitions().nodes().containsKey(COST)){mc.getConnection().sendCommand("mastery edit_mode false");complete(mc,null);}}
            }
        }catch(Exception error){complete(mc,error);}
    }
    private static void checkGuide(boolean expected)throws Exception {
        if(!ModList.get().isLoaded("patchouli")){REPORT.addProperty("patchouli_loaded",false);return;}
        REPORT.addProperty("patchouli_loaded",true);
        Class<?> registry=Class.forName("vazkii.patchouli.common.book.BookRegistry");
        Object book=((Map<?,?>)registry.getField("books").get(registry.getField("INSTANCE").get(null))).get(ResourceLocation.parse("mastery:guide"));
        check(book!=null,"Mastery guide registered");Object contents=book.getClass().getMethod("getContents").invoke(book);
        check(!(boolean)contents.getClass().getMethod("isErrored").invoke(contents),"Guide content parses");
        Map<?,?> categories=(Map<?,?>)contents.getClass().getField("categories").get(contents);
        Map<?,?> entries=(Map<?,?>)contents.getClass().getField("entries").get(contents);
        check(categories.containsKey(ResourceLocation.parse("mastery:editor"))==expected,"Guide category visibility matches edit mode");
        check(entries.containsKey(ResourceLocation.parse("mastery:editor_triggers"))==expected,"Editor entry visibility matches edit mode");
    }
    private static void openGuide()throws Exception {if(!ModList.get().isLoaded("patchouli"))return;var api=Class.forName("vazkii.patchouli.api.PatchouliAPI").getMethod("get").invoke(null);Class.forName("vazkii.patchouli.api.PatchouliAPI$IPatchouliAPI").getMethod("openBookGUI",ResourceLocation.class).invoke(api,ResourceLocation.parse("mastery:guide"));}
    private static void openWorkshopEntry(String entryId,int page)throws Exception {
        var bookId=ResourceLocation.parse("mastery:guide");var id=ResourceLocation.parse(entryId);
        Class<?> registry=Class.forName("vazkii.patchouli.common.book.BookRegistry");
        Object book=((Map<?,?>)registry.getField("books").get(registry.getField("INSTANCE").get(null))).get(bookId);
        Object contents=book.getClass().getMethod("getContents").invoke(book);
        Object entry=((Map<?,?>)contents.getClass().getField("entries").get(contents)).get(id);
        check(entry!=null,"Workshop entry registered: "+entryId);
        check(((List<?>)entry.getClass().getMethod("getPages").invoke(entry)).size()>page,"Workshop page exists: "+entryId+"/"+page);
        Object api=Class.forName("vazkii.patchouli.api.PatchouliAPI").getMethod("get").invoke(null);
        Class.forName("vazkii.patchouli.api.PatchouliAPI$IPatchouliAPI").getMethod("openBookEntry",ResourceLocation.class,ResourceLocation.class,int.class).invoke(api,bookId,id,page);
    }
    private static JsonObject currentJson(Minecraft mc){return JsonParser.parseString(mc.screen.children().stream().filter(MultiLineEditBox.class::isInstance).map(MultiLineEditBox.class::cast).findFirst().orElseThrow().getValue()).getAsJsonObject();}
    private static void openField(Minecraft mc,String key){mc.screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).max(Comparator.comparingInt(EditBox::getY)).orElseThrow().setValue(key);var b=mc.screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(v->v.getMessage().getString().startsWith(EditorSchema.label(key)+":")).findFirst().orElseThrow();mc.screen.mouseClicked(b.getX()+10,b.getY()+10,0);}
    private static void editValue(Minecraft mc,String key,String value){openField(mc,key);mc.screen.children().stream().filter(MultiLineEditBox.class::isInstance).map(MultiLineEditBox.class::cast).findFirst().orElseThrow().setValue(value);button(mc,"Apply");}
    private static void prefixButton(Minecraft mc,String prefix){var button=mc.screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(b->b.getMessage().getString().startsWith(prefix)).findFirst().orElseThrow(()->new IllegalStateException("Missing button prefix: "+prefix));mc.screen.mouseClicked(button.getX()+10,button.getY()+10,0);}
    private static void button(Minecraft mc,String name){var button=mc.screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(b->b.getMessage().getString().equals(name)).findFirst().orElseThrow(()->new IllegalStateException("Missing button: "+name));check(button.active,"Button active: "+name);mc.screen.mouseClicked(button.getX()+button.getWidth()/2.0,button.getY()+10,0);mc.screen.mouseReleased(button.getX()+button.getWidth()/2.0,button.getY()+10,0);}
    private static void capture(Minecraft mc,String file)throws Exception{try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(OUT.resolve(file));}}
    private static void next(String value)throws Exception{phase=value;age=0;Files.writeString(OUT.resolve("checkpoint.json"),"{\"phase\":\""+phase+"\"}");}
    private static void check(boolean valid,String message){if(!valid)throw new IllegalStateException(message);}
    private static void complete(Minecraft mc,Exception error){done=true;REPORT.addProperty("success",error==null);REPORT.addProperty("phase",phase);REPORT.addProperty("ticks",ticks);REPORT.addProperty("muted",true);REPORT.addProperty("hidden",true);if(error!=null)REPORT.addProperty("error",error.toString());try{Files.writeString(OUT.resolve("result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(REPORT));if(error!=null)capture(mc,"failure.png");}catch(Exception ignored){}if(mc.player!=null)mc.disconnect(new TitleScreen());mc.stop();}
}
