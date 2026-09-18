package com.cappleapple.mastery.client.validation;

import com.cappleapple.mastery.client.*;
import com.cappleapple.mastery.client.gui.*;
import com.cappleapple.mastery.network.MasteryNetwork;
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

@EventBusSubscriber(modid="mastery",value=Dist.CLIENT)
public final class ExtraChargesSmoke {
    private static final String SPELL="irons_spellbooks:fireball",MOD="mastery:extra_charges_fixture";
    private static final Path OUT=Path.of("../build/extra-charges-smoke").toAbsolutePath();
    private static final JsonObject REPORT=new JsonObject();
    private static JsonObject definitionSnapshot;
    private static String phase="init";private static int ticks,age,base,raised;private static boolean done;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("mastery.extraChargesSmoke")||done)return;
        var mc=Minecraft.getInstance();ticks++;age++;
        try {
            if(ticks==1) {
                Files.createDirectories(OUT);mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);mc.options.pauseOnLostFocus=false;mc.options.framerateLimit().set(60);mc.options.guiScale().set(2);mc.resizeDisplay();
                GLFW.glfwSetWindowAttrib(mc.getWindow().getWindow(),GLFW.GLFW_FOCUS_ON_SHOW,GLFW.GLFW_FALSE);GLFW.glfwHideWindow(mc.getWindow().getWindow());
            }
            mc.mouseHandler.releaseMouse();mc.getToasts().clear();
            if(ticks>1800)throw new IllegalStateException("Timeout "+phase);
            if(mc.screen instanceof DisconnectedScreen)throw new IllegalStateException("Disconnected "+phase);
            switch(phase) {
                case "init" -> {if(age>30&&mc.getOverlay()==null){ConnectScreen.startConnecting(new TitleScreen(),mc,ServerAddress.parseString("127.0.0.1:25578"),new ServerData("Extra charges validation","127.0.0.1:25578",ServerData.Type.OTHER),false,null);next("joined");}}
                case "joined" -> {if(age>20&&mc.player!=null&&ClientState.runtime().has("world_id")&&!ClassSelectionScreen.required()) {
                    for(String command:List.of("mastery edit_mode false","mastery node unlock @s mastery:hud_capacity","mastery node rank @s mastery:fireball 1","mastery node rank @s mastery:fireball/empowered 0","mastery node rank @s mastery:fireball/efficiency 0","mastery node rank @s mastery:fire/scorch 0","mastery node rank @s "+MOD+" 0"))command(mc,command);
                    next("equip");
                }}
                case "equip" -> {if(age>20){MasteryNetwork.sendAction("equip",SPELL,ClientState.context(),0);next("base");}}
                case "base" -> {if(age>20){base=charges();check(base>0,"Tempo baseline");REPORT.addProperty("base_charges",base);ClientState.view().selected="mastery:fireball";mc.setScreen(new MasteryScreen());command(mc,"mastery node rank @s "+MOD+" 1");next("rank_one");}}
                case "rank_one" -> {if(age>20){check(charges()==base+2,"Rank one charges must update before casting");check(SpellDetails.level(SPELL)==1,"Charge modifier leaves native level unchanged");REPORT.addProperty("rank_one_charges",charges());command(mc,"mastery node rank @s "+MOD+" 3");next("rank_three");}}
                case "rank_three" -> {if(age>20){check(charges()==base+6,"Rank three charges refresh without a spell-level change");check(NodeDetails.describe("mastery:fireball").stream().anyMatch(c->c.getString().equals("+6 Spell Charges")),"Current charge bonus displayed");REPORT.addProperty("rank_three_charges",charges());MasteryNetwork.sendAction("toggle",MOD,"",0);next("disabled");}}
                case "disabled" -> {if(age>20){check(charges()==base,"Disable restores original capacity");MasteryNetwork.sendAction("toggle",MOD,"",1);next("reenabled");}}
                case "reenabled" -> {if(age>20){check(charges()==base+6,"Re-enable restores bonus");command(mc,"mastery node rank @s mastery:fireball 3");mc.setScreen(null);next("hold_ready");}}
                case "hold_ready" -> {if(age>20){MasteryNetwork.sendAction("press","","",0);next("casting");}}
                case "casting" -> {if(age==55){
                    var field=com.cappleapple.mastery.client.ChargeHud.class.getDeclaredField("costMarkers");field.setAccessible(true);
                    check(!((java.util.List<?>)field.get(null)).isEmpty(),"Tempo charge-cost marker received");
                    try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(OUT.resolve("held-charge-yellow-marker.png"));}
                }if(age>115){check(uses()>1,"Held native levels must consume additional Tempo charges");REPORT.addProperty("held_cast_charge_uses",uses());MasteryNetwork.sendAction("toggle",MOD,"",0);next("cooldown_disabled");}}
                case "cooldown_disabled" -> {if(age>20){check(uses()>0&&charges()==base,"Charge capacity updates during active recharge");MasteryNetwork.sendAction("toggle",MOD,"",1);ClientState.reveal(MOD);ClientState.view().selected="mastery:fireball";var point=ClientState.layout().anchors().get(MOD);ClientState.view().zoom=1;ClientState.view().panX=-point.x();ClientState.view().panY=-point.y();ClientState.view().cameraInitialized=true;mc.setScreen(new MasteryScreen());next("capture");}}
                case "removed_node" -> {if(age>20){check(!ClientState.definitions().nodes().containsKey(MOD),"Fixture node removed during open-map reload");ClientState.acceptDefinitions(definitionSnapshot.toString());REPORT.addProperty("node_removal_reload",true);command(mc,"attribute @s temponottime:casting_reserve base set 4096");next("attribute_change");}}
                case "attribute_change" -> {if(age>25){raised=charges();check(raised>base+6,"Attribute changes refresh max charges");MasteryNetwork.sendAction("equip","",ClientState.context(),0);next("unslotted");}}
                case "unslotted" -> {if(age>25){check(!ClientState.boundSpell(0).equals(SPELL),"Unslotted fixture");checkDisplayed(raised);REPORT.addProperty("unslotted_and_attribute_refresh",true);REPORT.addProperty("charges_after_attribute_change",raised);command(mc,"attribute @s temponottime:casting_reserve base set 400");
                    for(String command:List.of("mastery level set @s mastery:fire 3","mastery level set @s mastery:two_handed 3","mastery node rank @s mastery:fire/foundation 1","mastery node rank @s mastery:two_handed/foundation 1","mastery node rank @s mastery:spellblade_practice 1","mastery node rank @s mastery:flame_blade/flaming_strike 1"))command(mc,command);
                    next("root_ready");}}
                case "root_ready" -> {if(age>25){
                    ClientState.reveal("mastery:flame_blade/flaming_strike");ClientState.view().selected="mastery:flame_blade/flaming_strike";
                    var point=ClientState.layout().anchors().get("mastery:spellblade_practice");ClientState.view().zoom=1;ClientState.view().panX=-point.x();ClientState.view().panY=-point.y();ClientState.view().cameraInitialized=true;
                    mc.setScreen(new MasteryScreen());next("root_capture");
                }}
                case "root_capture" -> {if(age>20){
                    capture(mc,"flame-blade-root-and-flaming-strike.png");
                    check(NodeDetails.describe("mastery:spellblade_practice").stream().anyMatch(c->c.getString().equals("+10% Two-Handed Weapon Fire Damage")),"Flame Blade root effect label");
                    REPORT.addProperty("flaming_strike_school",io.redspace.ironsspellbooks.api.registry.SchoolRegistry.REGISTRY.getKey(io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell("irons_spellbooks:flaming_strike").getSchoolType()).toString());
                    command(mc,"mastery edit_mode true");next("editor_ready");
                }}
                case "editor_ready" -> {if(age>20&&ClientState.editMode()){
                    var draft=com.cappleapple.mastery.client.gui.editor.ScriptSchema.root("keywords");draft.addProperty("decay_delay",60);draft.addProperty("decay_interval",20);draft.addProperty("decay_stacks",1);
                    draft.getAsJsonArray("stacks_lost_actions").add(com.cappleapple.mastery.client.gui.editor.ScriptSchema.action("particles"));
                    var owner=new com.cappleapple.mastery.client.gui.editor.DefinitionEditorScreen(new MasteryScreen(),"keywords","mastery:validation_decay",draft,ClientState.definitionRevision(),true);
                    var screen=new com.cappleapple.mastery.client.gui.editor.VisualScriptScreen(owner,draft);
                    var field=screen.getClass().getDeclaredField("keywordEvent");field.setAccessible(true);field.setInt(screen,2);mc.setScreen(screen);next("editor_capture");
                }}
                case "editor_capture" -> {if(age>15){
                    capture(mc,"keyword-stack-loss-editor.png");
                    var screen=(com.cappleapple.mastery.client.gui.editor.VisualScriptScreen)mc.screen;
                    var field=screen.getClass().getDeclaredField("keywordEvent");field.setAccessible(true);check(field.getInt(screen)==2,"Stack-loss lane remained selected");
                    var button=screen.children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance).map(net.minecraft.client.gui.components.Button.class::cast).filter(b->b.getMessage().getString().equals("Stacks lost actions")).findFirst().orElseThrow();button.onPress();
                    next("all_lost_capture");
                }}
                case "all_lost_capture" -> {if(age>15){capture(mc,"keyword-all-stacks-lost-editor.png");REPORT.addProperty("keyword_loss_editor",true);command(mc,"mastery edit_mode false");complete(mc,null);}}
                case "capture" -> {if(age>15){try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(OUT.resolve("triangle-charge-modifier.png"));}REPORT.addProperty("toggle_and_active_recharge_sync",true);
                    definitionSnapshot=ClientState.definitions().toJson();definitionSnapshot.addProperty("definition_revision",ClientState.definitionRevision());
                    var removed=definitionSnapshot.deepCopy();removed.getAsJsonObject("nodes").remove(MOD);ClientState.acceptDefinitions(removed.toString());next("removed_node");}}
            }
        }catch(Exception error){complete(mc,error);}
    }
    private static void capture(Minecraft mc,String name)throws Exception {try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(OUT.resolve(name));}}
    private static Object state()throws Exception{return ((Map<?,?>)Class.forName("com.cappleapple.temponottime.network.ClientCooldownState").getMethod("spells").invoke(null)).get(SPELL);}
    private static int charges()throws Exception{var state=state();int maximum=state==null?0:(int)state.getClass().getMethod("maximumCharges").invoke(state);checkDisplayed(maximum);return maximum;}
    private static void checkDisplayed(int expected)throws Exception {
        String line="Charges: "+expected;
        check(SpellDetails.stats(SPELL).stream().anyMatch(c->c.getString().equals(line)),"Current max charges in spell stats: "+line);
        if(Minecraft.getInstance().screen instanceof MasteryScreen screen) {
            var field=MasteryScreen.class.getDeclaredField("detailLines");field.setAccessible(true);
            boolean found=false;
            for(var value:(java.util.List<?>)field.get(screen)) {
                var text=new StringBuilder();((net.minecraft.util.FormattedCharSequence)value).accept((index,style,code)->{text.appendCodePoint(code);return true;});
                if(text.toString().equals(line))found=true;
            }
            check(found,"Open details panel refreshed: "+line);
        }
    }
    private static int uses()throws Exception{var state=state();return state==null?0:(int)state.getClass().getMethod("activeUses").invoke(state);}
    private static void command(Minecraft mc,String text){mc.getConnection().sendCommand(text);}
    private static void next(String value){phase=value;age=0;}
    private static void check(boolean value,String text){if(!value)throw new IllegalStateException(text);}
    private static void complete(Minecraft mc,Exception error){done=true;REPORT.addProperty("success",error==null);REPORT.addProperty("phase",phase);if(error!=null)REPORT.addProperty("error",error.toString());try{Files.writeString(OUT.resolve("result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(REPORT));}catch(Exception ignored){}if(mc.player!=null)mc.disconnect(new TitleScreen());mc.stop();}
}
