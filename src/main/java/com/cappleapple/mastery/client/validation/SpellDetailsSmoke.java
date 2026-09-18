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
public final class SpellDetailsSmoke {
    private static final String SPELL="irons_spellbooks:fireball",MOD="mastery:fireball/empowered";
    private static final Path OUT=Path.of("../build/spell-details-smoke").toAbsolutePath();
    private static final boolean TEMPO=net.neoforged.fml.ModList.get().isLoaded("temponottime");
    private static final JsonObject REPORT=new JsonObject();
    private static String phase="init";private static int ticks,age,baseCharges,rankOneCharges;private static boolean done;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("mastery.spellDetailsSmoke")||done)return;
        var mc=Minecraft.getInstance();ticks++;age++;
        try {
            if(ticks==1) {
                Files.createDirectories(OUT);mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);mc.options.pauseOnLostFocus=false;mc.options.framerateLimit().set(60);mc.options.guiScale().set(2);mc.resizeDisplay();
                GLFW.glfwSetWindowAttrib(mc.getWindow().getWindow(),GLFW.GLFW_FOCUS_ON_SHOW,GLFW.GLFW_FALSE);GLFW.glfwHideWindow(mc.getWindow().getWindow());
            }
            mc.mouseHandler.releaseMouse();mc.getToasts().clear();
            if(ticks>2400)throw new IllegalStateException("Timeout "+phase);
            if(mc.screen instanceof DisconnectedScreen)throw new IllegalStateException("Disconnected "+phase);
            switch(phase) {
                case "init" -> {if(age>30&&mc.getOverlay()==null){ConnectScreen.startConnecting(new TitleScreen(),mc,ServerAddress.parseString("127.0.0.1:25578"),new ServerData("Spell details validation","127.0.0.1:25578",ServerData.Type.OTHER),false,null);next("joined");}}
                case "joined" -> {if(age>20&&mc.player!=null&&ClientState.runtime().has("world_id")&&!ClassSelectionScreen.required()) {
                    command(mc,"mastery edit_mode false");command(mc,"mastery node unlock @s mastery:hud_capacity");command(mc,"mastery node rank @s mastery:fireball 1");
                    command(mc,"mastery node rank @s "+MOD+" 0");command(mc,"mastery node rank @s mastery:fireball/efficiency 0");command(mc,"mastery node rank @s mastery:fire/scorch 0");
                    command(mc,"attribute @s temponottime:casting_reserve base set 400");next("equip");
                }}
                case "equip" -> {if(age>20){check(ClientState.capacity()>0,"Capacity fixture");MasteryNetwork.sendAction("equip",SPELL,ClientState.context(),0);next("base");}}
                case "base" -> {if(age>25){checkLevel(mc,1);baseCharges=charges();check(baseCharges>0,"Tempo sees prepared spell before first cast");
                    check(SpellDetails.hideMana()==TEMPO,"Mana visibility follows optional Tempo mode");
                    var text=NodeDetails.describe("mastery:fireball").stream().map(c->c.getString()).toList();
                    check(text.stream().noneMatch(t->t.contains("Unlock Fireball")||t.contains("Modifier slots")||t.contains("Assigned spells")||t.equals("Enabled")||t.equals("Disabled")||TEMPO&&t.toLowerCase(Locale.ROOT).contains("mana")),"Redundant or mana text: "+text);
                    check(text.get(1).equals(net.minecraft.network.chat.Component.translatable("spell.irons_spellbooks.fireball.guide").getString()),"Scroll forge description");
                    if(TEMPO)check(ClientState.runtime().getAsJsonObject("spell_stats").getAsJsonObject(SPELL).get("cooldown_ticks").getAsInt()<500,"Tempo normalization reflected in spell stats");
                    else check(text.stream().anyMatch(t->t.toLowerCase(Locale.ROOT).contains("mana")),"Mana visible without Tempo");
                    REPORT.add("base_details",new Gson().toJsonTree(text));command(mc,"mastery node rank @s "+MOD+" 1");next("enabled");}}
                case "enabled" -> {if(age>25){checkLevel(mc,2);rankOneCharges=charges();check(!TEMPO||rankOneCharges<baseCharges,"Tempo capacity follows enabled level: "+baseCharges+" -> "+rankOneCharges);command(mc,"mastery node rank @s "+MOD+" 3");next("upgraded");}}
                case "upgraded" -> {if(age>25){checkLevel(mc,4);check(!TEMPO||charges()<rankOneCharges,"Tempo capacity follows upgraded level");REPORT.addProperty("upgraded_charges",charges());MasteryNetwork.sendAction("toggle",MOD,"",0);next("disabled");}}
                case "disabled" -> {if(age>25){checkLevel(mc,1);check(charges()==baseCharges,"Tempo restores base charges on disable");MasteryNetwork.sendAction("toggle",MOD,"",1);command(mc,"mastery node unlock @s mastery:fire/scorch");next("scorch");}}
                case "scorch" -> {if(age>25){checkLevel(mc,4);check(NodeDetails.describe("mastery:fireball").stream().anyMatch(c->c.getString().contains("Scorch on hit")),"Equipped Scorch appended to spell stats");
                    for(String id:ClientState.definitions().nodes().keySet())check(NodeDetails.describe(id).stream().noneMatch(c->c.getString().startsWith("Modifier slots:")||c.getString().startsWith("Assigned spells:")||c.getString().equals("Enabled")||c.getString().equals("Disabled")),"Redundant node text: "+id);
                    checkVisibility();ClientState.view().selected="mastery:fireball";ClientState.reveal("mastery:fireball");var anchor=ClientState.layout().anchors().get("mastery:fireball");ClientState.view().zoom=.7;ClientState.view().panX=-anchor.x()*.7;ClientState.view().panY=-anchor.y()*.7;ClientState.view().cameraInitialized=true;
                    mc.setScreen(new MasteryScreen());
                    if(TEMPO){MasteryNetwork.sendAction("press","","",0);next("cooldown");}else next("render");}}
                case "cooldown" -> {if(age>100){check(activeUses()>0,"Live cooldown fixture");MasteryNetwork.sendAction("toggle",MOD,"",0);next("cooldown_disabled");}}
                case "cooldown_disabled" -> {if(age>20){checkLevel(mc,1);check(activeUses()>0&&charges()==baseCharges,"Active cooldown must not overwrite current prepared level");REPORT.addProperty("active_cooldown_uses_current_prepared_level",true);MasteryNetwork.sendAction("toggle",MOD,"",1);next("render");}}
                case "render" -> {if(age>15){capture(mc,"spell-details.png");var lines=ScriptText.keyword("mastery:scorch");check(lines.stream().anyMatch(c->c.getString().contains("At 5 stacks")),"Keyword threshold explanation");
                    mc.setScreen(new KeywordScreen());next("keyword");}}
                case "keyword" -> {if(age>8){check(KeywordHover.keywordAt(22,22).equals("mastery:scorch"),"Rendered keyword hit detection");move(mc,23,23);next("keyword_capture");}}
                case "keyword_capture" -> {if(age>8){capture(mc,"keyword-tooltip.png");REPORT.addProperty("base_charges",baseCharges);REPORT.addProperty("rank_one_charges",rankOneCharges);REPORT.addProperty("native_hud_levels_and_modifier_toggles",true);REPORT.addProperty("keyword_hover",true);mc.setScreen(new NestedTooltipScreen());move(mc,100,100);next("nested_start");}}
                case "nested_start" -> {if(age>8){check(KeywordHover.keywordAt(114,90).equals("mastery:scorch"),"Keyword captured inside native tooltip");move(mc,114,90);next("nested_hover");}}
                case "nested_hover" -> {if(age>8){check(KeywordHover.keywordAt(114,90).equals("mastery:scorch"),"Tooltip stays fixed when pointer enters its keyword");capture(mc,"nested-keyword-tooltip.png");REPORT.addProperty("nested_keyword_tooltip",true);complete(mc,null);}}
            }
        }catch(Exception error){complete(mc,error);}
    }
    private static void checkVisibility() {
        var saved=ClientState.runtime().get("available_modifier_nodes");ClientState.view().expanded.add("mastery:fireball");
        try {
            var available=new JsonArray();available.add("mastery:fireball/efficiency");ClientState.runtime().add("available_modifier_nodes",available);
            check(ClientState.visible().contains("mastery:fireball/efficiency"),"Unbought modifier shown with free slot");
            ClientState.runtime().add("available_modifier_nodes",new JsonArray());
            check(!ClientState.visible().contains("mastery:fireball/efficiency"),"Unbought modifier hidden without free slot");
            check(ClientState.visible().contains(MOD),"Bought modifier remains visible");
            REPORT.addProperty("modifier_slot_visibility",true);
        }finally{ClientState.runtime().add("available_modifier_nodes",saved);}
    }
    private static void checkLevel(Minecraft mc,int level) {
        check(SpellDetails.level(SPELL)==level,"Snapshot level "+level+": "+ClientState.runtime().get("spell_levels"));
        NativeSpellHud.beginRender();try{check(io.redspace.ironsspellbooks.player.ClientMagicData.getSpellSelectionManager().getAllSpells().getFirst().spellData.getLevel()==level,"Actual native HUD spell level");}finally{NativeSpellHud.endRender();}
        var stats=SpellDetails.stats(SPELL);var expected=io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell(SPELL).getUniqueInfo(level,mc.player);
        check(stats.stream().map(c->c.getString()).toList().containsAll(expected.stream().map(c->c.getString()).toList()),"Native stats at effective level");
    }
    private static int charges()throws Exception {
        if(!TEMPO)return 1;
        var states=(Map<?,?>)Class.forName("com.cappleapple.temponottime.network.ClientCooldownState").getMethod("spells").invoke(null);var state=states.get(SPELL);
        return state==null?-1:(int)state.getClass().getMethod("maximumCharges").invoke(state);
    }
    private static int activeUses()throws Exception {
        var states=(Map<?,?>)Class.forName("com.cappleapple.temponottime.network.ClientCooldownState").getMethod("spells").invoke(null);var state=states.get(SPELL);
        return state==null?0:(int)state.getClass().getMethod("activeUses").invoke(state);
    }
    private static void command(Minecraft mc,String text){mc.getConnection().sendCommand(text);}
    private static void next(String value){phase=value;age=0;}
    private static void check(boolean value,String text){if(!value)throw new IllegalStateException(text);}
    private static void move(Minecraft mc,int x,int y)throws Exception{
        for(String name:List.of("xpos","ypos")){var field=MouseHandler.class.getDeclaredField(name);field.setAccessible(true);field.setDouble(mc.mouseHandler,(name.equals("xpos")?x:y)*mc.getWindow().getGuiScale());}
    }
    private static void capture(Minecraft mc,String name)throws Exception{try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(OUT.resolve(TEMPO?name:"without-tempo-"+name));}}
    private static void complete(Minecraft mc,Exception error){done=true;REPORT.addProperty("success",error==null);REPORT.addProperty("phase",phase);if(error!=null)REPORT.addProperty("error",error.toString());try{Files.writeString(OUT.resolve(TEMPO?"result.json":"without-tempo-result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(REPORT));}catch(Exception ignored){}if(mc.player!=null)mc.disconnect(new TitleScreen());mc.stop();}
    private static final class NestedTooltipScreen extends Screen {
        NestedTooltipScreen(){super(net.minecraft.network.chat.Component.literal("Nested tooltip validation"));}
        @Override public void render(net.minecraft.client.gui.GuiGraphics graphics,int x,int y,float delta){
            graphics.fill(0,0,width,height,0xff202020);
            if(x>=96&&x<=105&&y>=96&&y<=105)graphics.renderComponentTooltip(font,List.of(net.minecraft.network.chat.Component.literal("Scorch on hit")),100,100);
        }
        @Override public void renderBackground(net.minecraft.client.gui.GuiGraphics graphics,int x,int y,float delta){}
    }
    private static final class KeywordScreen extends Screen {
        KeywordScreen(){super(net.minecraft.network.chat.Component.literal("Keyword validation"));}
        @Override public void render(net.minecraft.client.gui.GuiGraphics graphics,int x,int y,float delta){graphics.fill(0,0,width,height,0xff202020);graphics.drawString(font,"Scorch on hit",20,20,0xffffff);}
        @Override public void renderBackground(net.minecraft.client.gui.GuiGraphics graphics,int x,int y,float delta){}
    }
}
