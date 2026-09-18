package com.cappleapple.mastery.client.validation;

import com.cappleapple.mastery.client.ClientState;
import com.cappleapple.mastery.client.gui.ClassSelectionScreen;
import com.cappleapple.mastery.client.gui.MasteryScreen;
import com.cappleapple.mastery.client.gui.editor.*;
import com.cappleapple.mastery.data.PromotedTrees;
import com.cappleapple.mastery.network.MasteryNetwork;
import com.google.gson.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;
import java.nio.file.*;
import java.util.*;

/** Opt-in actual client class selection, promotion, visual authoring and reconnect checks; excluded from release JARs. */
@EventBusSubscriber(modid="mastery",value=Dist.CLIENT)
public final class ClassesClientSmoke {
    private static final boolean ENABLED=Boolean.getBoolean("mastery.classesSmoke");
    private static final boolean PREVIEW=Boolean.getBoolean("mastery.selectorPreviewSmoke");
    private static final boolean REOPEN=Boolean.getBoolean("mastery.reopenSmoke");
    private static final Path OUT=Path.of(REOPEN?"../build/reopen-smoke":"../build/classes-smoke").toAbsolutePath();
    private static final JsonObject REPORT=new JsonObject();
    private static String phase="init";
    private static int ticks,age,tempoCharges;
    private static boolean done;
    private static long revision;
    private ClassesClientSmoke() {}
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
            mc.mouseHandler.releaseMouse();mc.getToasts().clear();if(phase.startsWith("tempo"))mc.gui.getChat().clearMessages(true);
            if(ticks>3000)throw new IllegalStateException("Timeout: "+phase);
            if(mc.screen instanceof DisconnectedScreen)throw new IllegalStateException("Disconnected: "+phase);
            switch(phase) {
                case "init" -> {if(age>30&&mc.getOverlay()==null){connect(mc);next("select");}}
                case "select" -> {if(REOPEN&&age>20&&mc.player!=null&&ClientState.runtime().has("world_id")&&!ClassSelectionScreen.required()) {
                    check(!ClientState.editMode(),"Player view fixture");ClientState.reveal("mastery:fireball");ClientState.view().expanded.add("mastery:fireball");mc.setScreen(new MasteryScreen());checkSettled(mc);next("reopen_ready");
                }else if(PREVIEW&&age>20&&mc.player!=null&&ClientState.runtime().has("world_id")&&ClientState.progress().selectedClass().equals("mastery:mage")){
                    next("tempo_assign");
                }else if(mc.player!=null&&ClassSelectionScreen.required()&&mc.screen instanceof ClassSelectionScreen&&mc.player.isSpectator()){
                    check(ClientState.definitions().classes().size()>=(PREVIEW?20:3),"Bundled classes synchronized");button(mc,"Ranger");button(mc,"Mage");revision=ClientState.definitionRevision();if(PREVIEW)mc.getConnection().sendCommand("item replace entity @s armor.head with minecraft:leather_helmet");mc.getConnection().sendCommand("mastery reload");next("selector_reload");}}
                case "selector_reload" -> {if(age>10&&ClientState.definitionRevision()>revision&&mc.screen instanceof ClassSelectionScreen){REPORT.addProperty("pending_selector_reload",true);next("selector_capture");}}
                case "selector_capture" -> {if(age>10){capture(mc,"01-class-selector.png");REPORT.addProperty("spectator_during_selection",mc.player.isSpectator());if(PREVIEW){checkPreview(mc);hoverArmor(mc,0);next("preview_helmet");}else{button(mc,"Choose Mage");next("selected");}}}
                case "preview_helmet" -> {if(age>8){capture(mc,"09-preview-helmet.png");hoverArmor(mc,1);next("preview_chest");}}
                case "preview_chest" -> {if(age>8){capture(mc,"10-preview-chest.png");hoverArmor(mc,2);next("preview_legs");}}
                case "preview_legs" -> {if(age>8){capture(mc,"11-preview-legs.png");hoverArmor(mc,3);next("preview_boots");}}
                case "preview_boots" -> {if(age>8){capture(mc,"12-preview-boots.png");mc.options.guiScale().set(3);mc.resizeDisplay();next("preview_compact");}}
                case "preview_compact" -> {if(age>10){capture(mc,"13-preview-compact.png");mc.options.guiScale().set(2);mc.resizeDisplay();next("preview_choose");}}
                case "preview_choose" -> {if(age>8){button(mc,"Choose Mage");next("selected");}}
                case "selected" -> {if(age>10&&!ClassSelectionScreen.required()&&!mc.player.isSpectator()&&ClientState.progress().selectedClass().equals("mastery:mage")){
                    checkRewards(mc);if(PREVIEW){check(mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(net.minecraft.world.item.Items.DIAMOND_HELMET),"Starter helmet equipped");check(itemCount(mc,"minecraft:leather_helmet")==1,"Displaced helmet preserved");REPORT.addProperty("starter_armor_and_displaced_inventory",true);}check(ClientState.progress().tree("mastery:fire").points()==2,"Starting fire points");check(ClientState.progress().tree("mastery:evocation").points()==1,"Starting evocation points");check(ClientState.progress().rank("mastery:fire/foundation")==1,"Starting skill rank");
                    REPORT.addProperty("class_rewards_and_mode_restoration",true);
                    for(String command:List.of("mastery level set @s mastery:fire 3","mastery level set @s mastery:two_handed 3","mastery node unlock @s mastery:two_handed/foundation","mastery edit_mode false"))mc.getConnection().sendCommand(command);
                    next("promote");}}
                case "promote" -> {if(age>15&&ClientState.progress().rank("mastery:two_handed/foundation")>0){MasteryNetwork.sendAction("purchase","mastery:spellblade_practice","",0);next("promoted");}}
                case "promoted" -> {if(age>10&&ClientState.progress().rank("mastery:spellblade_practice")==1){
                    check(PromotedTrees.displayTree(ClientState.definitions(),"mastery:spellblade_practice").equals("mastery:flame_blade"),"Root maps to independent tree");
                    check(ClientState.definitions().nodes().get("mastery:flame_blade/flame_edge").tree().equals("mastery:flame_blade"),"Descendant independent currency");
                    mc.getConnection().sendCommand("mastery xp add @s mastery:flame_blade 12.5");next("outline");}}
                case "outline" -> {if(age>15&&Math.abs(ClientState.progress().tree("mastery:flame_blade").xp()-12.5)<0.00001){
                    var layout=ClientState.layout();var childBefore=layout.anchors().get("mastery:flame_blade/flame_edge");var rootBefore=layout.anchors().get("mastery:spellblade_practice");
                    ClientState.moveSubtree("mastery:spellblade_practice",14,8);var moved=ClientState.layout().anchors();var childAfter=moved.get("mastery:flame_blade/flame_edge");
                    check(Math.abs(childAfter.x()-childBefore.x()-14)<0.00001&&Math.abs(childAfter.y()-childBefore.y()-8)<0.00001,"Promoted descendants move without jumping");
                    check(moved.get("mastery:spellblade_practice").equals(moved.get("mastery:flame_blade")),"Hidden anchor stays aligned while dragging");
                    double distanceBefore=Math.hypot(childAfter.x()-moved.get("mastery:spellblade_practice").x(),childAfter.y()-moved.get("mastery:spellblade_practice").y());
                    ClientState.orientTree("mastery:spellblade_practice","east");var rotated=ClientState.layout().anchors();var childRotated=rotated.get("mastery:flame_blade/flame_edge");var rootRotated=rotated.get("mastery:spellblade_practice");
                    check(Math.abs(Math.hypot(childRotated.x()-rootRotated.x(),childRotated.y()-rootRotated.y())-distanceBefore)<0.00001,"Promoted rotation preserves child distance");
                    check(rootRotated.equals(rotated.get("mastery:flame_blade")),"Rotated root has aligned hidden anchor");REPORT.addProperty("promoted_drag_and_rotation",true);
                    layout=ClientState.layout();var position=layout.anchors().get("mastery:spellblade_practice");check(position!=null,"Promoted root visible");check(!ClientState.visible().contains("mastery:flame_blade"),"No duplicate synthetic root");
                    ClientState.view().zoom=1.6;ClientState.view().panX=-position.x()*1.6;ClientState.view().panY=-position.y()*1.6;ClientState.view().cameraInitialized=true;ClientState.view().selected="mastery:spellblade_practice";mc.setScreen(new MasteryScreen());next("root_capture");}}
                case "root_capture" -> {if(age>10){capture(mc,"02-flame-blade-root.png");REPORT.addProperty("independent_root_and_exact_admin_xp",true);mc.getConnection().sendCommand("mastery level set @s mastery:fire 0");next("root_gate");}}
                case "root_gate" -> {if(age>10&&ClientState.progress().tree("mastery:fire").level()==0){check(!ClientState.entries().containsKey("mastery:flame_blade/flame_edge"),"Losing retained prerequisite hides promoted descendants");check(ClientState.progress().rank("mastery:spellblade_practice")==1,"Root rank retained while blocked");mc.getConnection().sendCommand("mastery xp add @s mastery:flame_blade 2");next("root_xp_blocked");}}
                case "root_xp_blocked" -> {if(age>10){check(Math.abs(ClientState.progress().tree("mastery:flame_blade").xp()-12.5)<0.00001,"Blocked root did not earn XP");mc.getConnection().sendCommand("mastery level set @s mastery:fire 3");next("root_restored");}}
                case "root_restored" -> {if(age>10&&ClientState.progress().tree("mastery:fire").level()==3){check(ClientState.entries().containsKey("mastery:flame_blade/flame_edge"),"Restoring retained prerequisite restores branch");REPORT.addProperty("retained_prerequisite_suspends_and_restores_branch",true);mc.getConnection().sendCommand("mastery edit_mode true");next("class_editor");}}
                case "class_editor" -> {if(age>10&&ClientState.editMode()){
                    new DefinitionEditorScreen(new MasteryScreen(),"classes","mastery:mage",ClientState.definitions().classes().get("mastery:mage").deepCopy(),ClientState.definitionRevision(),false).openForm();next("class_editor_capture");}}
                case "class_editor_capture" -> {if(age>10){capture(mc,"03-class-editor.png");openField(mc,"starting_points");next("points_editor");}}
                case "points_editor" -> {if(age>6){capture(mc,"04-starting-points-editor.png");button(mc,"Done");button(mc,"Save to world");revision=ClientState.definitionRevision();next("class_saved");}}
                case "class_saved" -> {if(ClientState.definitionRevision()>revision&&mc.screen instanceof MasteryScreen){checkRewards(mc);REPORT.addProperty("class_visual_round_trip",true);
                    var json=ClientState.definitions().toJson().getAsJsonObject("nodes").getAsJsonObject("mastery:spellblade_practice");new DefinitionEditorScreen(new MasteryScreen(),"synergies","mastery:spellblade_practice",json,ClientState.definitionRevision(),false).openForm();openField(mc,"root_tree");next("root_editor");}}
                case "root_editor" -> {if(age>10){capture(mc,"05-root-editor.png");button(mc,"Done");revision=ClientState.definitionRevision();button(mc,"Save to world");next("root_saved");}}
                case "root_saved" -> {if(ClientState.definitionRevision()>revision&&mc.screen instanceof MasteryScreen){check(ClientState.definitions().trees().containsKey("mastery:flame_blade"),"Promoted tree persists visual save");REPORT.addProperty("promoted_root_visual_round_trip",true);checkGuide(true);openGuideEntry("editor_classes",4);next("guide_classes");}}
                case "guide_classes" -> {if(age>10){capture(mc,"06-class-workshop.png");openGuideEntry("editor_roots",6);next("guide_roots");}}
                case "guide_roots" -> {if(age>10){capture(mc,"07-root-workshop.png");openGuideEntry("editor_experience",0);next("guide_experience");}}
                case "guide_experience" -> {if(age>10){capture(mc,"08-experience-workshop.png");REPORT.addProperty("new_workshop_entries_loaded_and_opened",true);mc.getConnection().sendCommand("mastery edit_mode false");next("guide_hidden");}}
                case "guide_hidden" -> {if(age>15&&!ClientState.editMode()){checkGuide(false);REPORT.addProperty("workshop_hidden_outside_edit_mode",true);next("disconnect");}}
                case "disconnect" -> {if(age>15){mc.disconnect(new TitleScreen());next("reconnect_wait");}}
                case "reconnect_wait" -> {if(age>50){connect(mc);next("reconnected");}}
                case "reconnected" -> {if(age>20&&mc.player!=null&&ClientState.runtime().has("world_id")&&ClientState.progress().selectedClass().equals("mastery:mage")){
                    check(!ClassSelectionScreen.required()&&!(mc.screen instanceof ClassSelectionScreen)&&!mc.player.isSpectator(),"Selection not repeated after reconnect");checkRewards(mc);check(ClientState.progress().rank("mastery:spellblade_practice")==1,"Promoted root retained");REPORT.addProperty("reconnect_persistence_without_duplicate_rewards",true);if(PREVIEW){for(String command:List.of("mastery node unlock @s mastery:hud_capacity","mastery node unlock @s mastery:fireball","attribute @s temponottime:casting_reserve base set 400"))mc.getConnection().sendCommand(command);next("tempo_assign");}else complete(mc,null);}}
                case "tempo_assign" -> {if(age>15){check(ClientState.capacity()>0,"HUD fixture capacity");MasteryNetwork.sendAction("equip","irons_spellbooks:fireball",ClientState.context(),0);next("tempo_snapshot");}}
                case "tempo_snapshot" -> {if(age>35){
                    var cls=Class.forName("com.cappleapple.temponottime.network.ClientCooldownState");var states=(Map<?,?>)cls.getMethod("spells").invoke(null);var state=states.get("irons_spellbooks:fireball");check(state!=null,"Tempo discovers an uncast skill spell: "+ClientState.runtime()+" loadouts="+ClientState.progress().loadouts());int count=(int)state.getClass().getMethod("availableCharges").invoke(state);check(count>1,"Tempo multiple charges fixture");
                    var original=io.redspace.ironsspellbooks.player.ClientMagicData.getSpellSelectionManager();
                    com.cappleapple.mastery.client.NativeSpellHud.beginRender();try{check(io.redspace.ironsspellbooks.player.ClientMagicData.getSpellSelectionManager().getAllSpells().stream().anyMatch(o->o.spellData.getSpell().getSpellId().equals("irons_spellbooks:fireball")),"Generic render getter exposes virtual spell to addon overlays");}finally{com.cappleapple.mastery.client.NativeSpellHud.endRender();}
                    check(io.redspace.ironsspellbooks.player.ClientMagicData.getSpellSelectionManager()==original,"Native equipment view restored outside HUD");
                    tempoCharges=count;REPORT.addProperty("tempo_uncast_charges",count);REPORT.addProperty("generic_hud_selection_scope",true);mc.setScreen(null);io.redspace.ironsspellbooks.gui.overlays.SpellBarOverlay.fadeoutDelay=80;next("tempo_capture");}}
                case "tempo_capture" -> {if(age>8){capture(mc,"14-tempo-charges.png");MasteryNetwork.sendAction("press","","",0);next("tempo_spent");}}
                case "tempo_spent" -> {if(age>10&&tempoAvailable()<tempoCharges){io.redspace.ironsspellbooks.gui.overlays.SpellBarOverlay.fadeoutDelay=80;REPORT.addProperty("tempo_spent_charge",tempoAvailable());next("tempo_spent_capture");}else if(age>180)throw new IllegalStateException("Tempo charge was not spent: "+ClientState.runtime());}
                case "tempo_spent_capture" -> {if(age>6){capture(mc,"15-tempo-spent.png");next("tempo_recovered");}}
                case "tempo_recovered" -> {if(age>15&&tempoAvailable()==tempoCharges){REPORT.addProperty("tempo_charge_recovery",true);
                    var position=ClientState.layout().anchors().get("mastery:spellblade_practice");ClientState.reveal("mastery:spellblade_practice");ClientState.view().expanded.add("mastery:spellblade_practice");ClientState.view().selected="mastery:spellblade_practice";ClientState.view().zoom=1.1;ClientState.view().panX=-position.x()*1.1;ClientState.view().panY=-position.y()*1.1;mc.setScreen(new MasteryScreen());next("collapse_ready");}}
                case "collapse_ready" -> {if(age>12){capture(mc,"16-root-expanded.png");ClientState.view().expanded.remove("mastery:spellblade_practice");invoke(mc.screen,"rebuild");check(edgeTo(mc,"mastery:flame_blade/flame_edge"),"Outgoing edges retained immediately on collapse");next("collapse_moving");}}
                case "collapse_moving" -> {if(age==1){check(edgeTo(mc,"mastery:flame_blade/flame_edge"),"Outgoing edge visible during collapse");capture(mc,"17-root-collapsing.png");}if(age>12){check(!edgeTo(mc,"mastery:flame_blade/flame_edge"),"Outgoing edge hidden after collapse");capture(mc,"18-root-collapsed.png");REPORT.addProperty("collapse_edges_during_and_after",true);mc.getConnection().sendCommand("mastery edit_mode true");next("damage_editor");}}
                case "damage_editor" -> {if(age>10&&ClientState.editMode()){var draft=ScriptSchema.root("triggers");draft.getAsJsonArray("conditions").add(ScriptSchema.condition("damage"));draft.getAsJsonArray("actions").add(ScriptSchema.action("heal"));var owner=new DefinitionEditorScreen(new MasteryScreen(),"triggers","mastery:preview_damage",draft,ClientState.definitionRevision(),true);mc.setScreen(new VisualScriptScreen(owner,draft));next("damage_card");}}
                case "damage_card" -> {if(age>8){capture(mc,"19-damage-condition-card.png");var owner=(DefinitionEditorScreen)field(mc.screen,"owner");var draft=(JsonObject)field(mc.screen,"draft");mc.setScreen(new VisualDefinitionScreen(owner,mc.screen,draft.getAsJsonArray("conditions").get(0),"/conditions"));next("damage_fields");}}
                case "damage_fields" -> {if(age>8){capture(mc,"20-damage-filter-fields.png");REPORT.addProperty("damage_editor_forms",true);mc.getConnection().sendCommand("mastery edit_mode false");next("finish");}}
                case "reopen_ready" -> {if(age>8){
                    var screen=mc.screen;var positions=Map.copyOf((Map<?,?>)field(screen,"displayPositions"));mc.setScreen(null);mc.setScreen(new MasteryScreen());checkSettled(mc);check(positions.equals(field(mc.screen,"displayPositions")),"New screen restores identical saved positions");
                    screen=mc.screen;mc.setScreen(null);mc.setScreen(screen);checkSettled(mc);check(positions.equals(field(screen,"displayPositions")),"Returning to existing screen restores positions");
                    for(String id:List.of("mastery:fire/scorch","mastery:lightning/thunder"))check(com.cappleapple.mastery.data.NodeAppearance.parse(com.cappleapple.mastery.data.SettingsResolver.forNode(ClientState.definitions(),id).getAsJsonObject("appearance")).shape()==com.cappleapple.mastery.data.NodeAppearance.Shape.TRIANGLE,"Trigger modifier shape: "+id);
                    check(com.cappleapple.mastery.data.UnlockPresentation.parse(com.cappleapple.mastery.data.SettingsResolver.forNode(ClientState.definitions(),"mastery:fireball").getAsJsonObject("unlock")).holdDelayMs()==100,"Live default hold delay");
                    var styles=screen.getClass().getDeclaredMethod("connectionStyle",com.cappleapple.mastery.layout.GraphLayout.Edge.class);styles.setAccessible(true);
                    int parents=0,children=0;
                    for(var edge:ClientState.layout().edges()) {
                        if(edge.to().equals("mastery:spellblade_practice")){parents++;check(edge.synergy(),"Flame Blade prerequisite classification");check(styles.invoke(screen,edge)==com.cappleapple.mastery.data.ConnectionPresentation.Style.DASHED,"Dashed prerequisite line");}
                        if(edge.from().equals("mastery:spellblade_practice")&&!edge.to().equals("mastery:flame_blade")){children++;check(!edge.synergy(),"Flame Blade branch classification");check(styles.invoke(screen,edge)==com.cappleapple.mastery.data.ConnectionPresentation.Style.SOLID,"Solid child line");}
                    }
                    check(parents>=2&&children>=1,"Live Flame Blade connectors present");REPORT.addProperty("flame_blade_parent_and_child_lines",true);
                    for(String id:List.of("mastery:spellblade_practice","mastery:fire/foundation")) {
                        check(ClientState.progress().rank(id)>0,"Unlocked details fixture: "+id);
                        var details=com.cappleapple.mastery.client.gui.NodeDetails.describe(id);
                        check(details.stream().noneMatch(v->v.getString().startsWith("Cost:")||v.getString().startsWith("Requires ")),"Unlocked node hides initial unlock information: "+id);
                        check(details.stream().anyMatch(v->v.getString().startsWith("Rank ")),"Unlocked rank remains visible");
                    }
                    var locked=ClientState.definitions().nodes().values().stream().filter(n->ClientState.progress().rank(n.id())==0&&!n.dependencies().isEmpty()).findFirst().orElseThrow();
                    var lockedDetails=com.cappleapple.mastery.client.gui.NodeDetails.describe(locked.id());
                    check(lockedDetails.stream().anyMatch(v->v.getString().startsWith("Cost:")),"Locked node retains purchase cost");
                    check(lockedDetails.stream().anyMatch(v->v.getString().startsWith("Requires ")),"Locked node retains prerequisites");
                    check(com.cappleapple.mastery.client.gui.NodeDetails.tooltip("mastery:fire/foundation").stream().anyMatch(v->v.getString().startsWith("Next rank cost:")),"Unlocked upgradeable node retains hover cost");
                    check(com.cappleapple.mastery.client.gui.NodeDetails.tooltip("mastery:spellblade_practice").stream().noneMatch(v->v.getString().startsWith("Next rank cost:")),"Maxed node has no hover purchase cost");
                    REPORT.addProperty("unlocked_details_hide_initial_cost_and_requirements",true);
                    REPORT.addProperty("locked_details_and_upgrade_hover_costs_retained",true);
                    for(String id:List.of("mastery:fire/scorch","mastery:lightning/thunder")) {
                        var node=ClientState.definitions().nodes().get(id);
                        check(node.modifier().equals("mastery:native_spell")&&!node.spell().isBlank(),"Client received equipped modifier binding");
                        check(node.dependencyLeaves().stream().anyMatch(d->ClientState.definitions().nodes().get(d.node()).type()==com.cappleapple.mastery.data.NodeType.ACTIVE),"Modifier branches from an active skill");
                        if(ClientState.progress().activeModifiers().values().stream().noneMatch(ids->ids.contains(id)))check(!ClientState.nodeEnabled(id),"Unassigned modifier displays disabled");
                    }
                    REPORT.addProperty("scorch_and_thunder_spell_modifier_bindings",true);
                    REPORT.addProperty("new_and_returned_screen_snap",true);REPORT.addProperty("trigger_modifier_shapes",true);REPORT.addProperty("hold_delay_ms",100);
                    ClientState.view().expanded.remove("mastery:fireball");invoke(screen,"rebuild");check(!field(screen,"displayPositions").equals(field(screen,"projectedTargets")),"Manual collapse still animates");next("reopen_collapsing");}}
                case "reopen_collapsing" -> {if(age>12){checkSettled(mc);ClientState.view().expanded.add("mastery:fireball");invoke(mc.screen,"rebuild");check(!field(mc.screen,"displayPositions").equals(field(mc.screen,"projectedTargets")),"Manual expansion still animates");next("reopen_expanding");}}
                case "reopen_expanding" -> {if(age>12){checkSettled(mc);REPORT.addProperty("manual_collapse_and_expansion_animate",true);complete(mc,null);}}
                case "finish" -> {if(age>10)complete(mc,null);}
            }
        }catch(Exception error){complete(mc,error);}
    }
    private static void checkSettled(Minecraft mc)throws Exception{check(!((Map<?,?>)field(mc.screen,"projectedTargets")).isEmpty(),"Populated graph");check(field(mc.screen,"displayPositions").equals(field(mc.screen,"projectedTargets")),"Nodes are at their target positions immediately");}
    private static void invoke(Object object,String name)throws Exception{var method=object.getClass().getDeclaredMethod(name);method.setAccessible(true);method.invoke(object);}
    private static boolean edgeTo(Minecraft mc,String id)throws Exception{for(var value:(List<?>)field(mc.screen,"edges")){var edge=(com.cappleapple.mastery.layout.GraphLayout.Edge)recordValue(value.getClass(),value,"edge");if(edge.to().equals(id))return true;}return false;}
    private static int tempoAvailable()throws Exception{var cls=Class.forName("com.cappleapple.temponottime.network.ClientCooldownState");var states=(Map<?,?>)cls.getMethod("spells").invoke(null);var state=states.get("irons_spellbooks:fireball");return state==null?-1:(int)state.getClass().getMethod("availableCharges").invoke(state);}
    private static Object field(Object target,String name)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private static void checkPreview(Minecraft mc)throws Exception{
        var plan=(com.cappleapple.mastery.classes.ClassEquipment.Plan)field(mc.screen,"equipment");
        check(plan.armor().get(net.minecraft.world.entity.EquipmentSlot.HEAD).is(net.minecraft.world.item.Items.DIAMOND_HELMET),"Preview class helmet");
        check(mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(net.minecraft.world.item.Items.LEATHER_HELMET),"Preview preserves live armor");
        check(plan.inventory().stream().anyMatch(v->v.is(net.minecraft.world.item.Items.LEATHER_HELMET)),"Preview includes displaced gear");
        var buttons=mc.screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).toList();
        var previous=buttons.stream().filter(b->b.getMessage().getString().equals("Previous")).findFirst().orElseThrow();var next=buttons.stream().filter(b->b.getMessage().getString().equals("Next")).findFirst().orElseThrow();var choose=buttons.stream().filter(b->b.getMessage().getString().equals("Choose Mage")).findFirst().orElseThrow();var disconnect=buttons.stream().filter(b->b.getMessage().getString().equals("Disconnect")).findFirst().orElseThrow();
        check(previous.getX()<choose.getX()&&choose.getX()<next.getX()&&disconnect.getY()>previous.getY(),"Selector button layout");button(mc,">");check((int)field(mc.screen,"stripOffset")>0,"Class icon strip scrolls");button(mc,"<");
        var tooltip=com.cappleapple.mastery.client.gui.NodeDetails.tooltip("mastery:fire/foundation");check(tooltip.stream().anyMatch(v->v.getString().startsWith("Next rank cost:")),"Upgradeable passive shows cost");
        var maxed=ClientState.progress().copy();com.cappleapple.mastery.progression.ProgressionService.setRank(ClientState.definitions(),maxed,"mastery:fire/foundation",3,-1);
        check(com.cappleapple.mastery.client.gui.NodeDetails.tooltip("mastery:fire/foundation",maxed).stream().noneMatch(v->v.getString().startsWith("Next rank cost:")),"Maxed passive hides upgrade cost");
        check(com.cappleapple.mastery.client.gui.NodeDetails.describe("mastery:fire").stream().noneMatch(v->v.getString().startsWith("Drag to move")),"Tree help blurb removed");
        REPORT.addProperty("selector_projection_buttons_and_strip",true);
    }
    private static void hoverArmor(Minecraft mc,int index)throws Exception{
        var hits=(List<?>)field(mc.screen,"itemHits");Object hit=hits.get(index);Class<?> c=hit.getClass();
        double x=((Number)recordValue(c,hit,"x")).doubleValue()+((Number)recordValue(c,hit,"width")).doubleValue()/2;
        double y=((Number)recordValue(c,hit,"y")).doubleValue()+((Number)recordValue(c,hit,"height")).doubleValue()/2;
        for(var pair:List.of(Map.entry("xpos",x),Map.entry("ypos",y))){var f=mc.mouseHandler.getClass().getDeclaredField(pair.getKey());f.setAccessible(true);f.setDouble(mc.mouseHandler,pair.getValue()*mc.getWindow().getGuiScale());}
    }
    private static Object recordValue(Class<?> cls,Object object,String name)throws Exception{var method=cls.getDeclaredMethod(name);method.setAccessible(true);return method.invoke(object);}
    private static void checkGuide(boolean editor)throws Exception {
        if(!net.neoforged.fml.ModList.get().isLoaded("patchouli"))throw new IllegalStateException("Prepared client fixture needs Patchouli");
        Object contents=guideContents();check(!(boolean)contents.getClass().getMethod("isErrored").invoke(contents),"Guide parses");
        Map<?,?> entries=(Map<?,?>)contents.getClass().getField("entries").get(contents);
        for(String id:List.of("classes","experience_bonuses","promoted_roots"))check(entries.containsKey(ResourceLocation.parse("mastery:"+id)),"Player guide entry exists: "+id);
        for(String id:List.of("editor_classes","editor_experience","editor_roots"))check(entries.containsKey(ResourceLocation.parse("mastery:"+id))==editor,"Workshop visibility: "+id);
    }
    private static Object guideContents()throws Exception {Class<?> registry=Class.forName("vazkii.patchouli.common.book.BookRegistry");Object book=((Map<?,?>)registry.getField("books").get(registry.getField("INSTANCE").get(null))).get(ResourceLocation.parse("mastery:guide"));check(book!=null,"Guide registered");return book.getClass().getMethod("getContents").invoke(book);}
    private static void openGuideEntry(String id,int page)throws Exception {Minecraft.getInstance().gui.getChat().clearMessages(true);var api=Class.forName("vazkii.patchouli.api.PatchouliAPI").getMethod("get").invoke(null);Class.forName("vazkii.patchouli.api.PatchouliAPI$IPatchouliAPI").getMethod("openBookEntry",ResourceLocation.class,ResourceLocation.class,int.class).invoke(api,ResourceLocation.parse("mastery:guide"),ResourceLocation.parse("mastery:"+id),page);}
    private static void checkRewards(Minecraft mc){
        check(itemCount(mc,"irons_spellbooks:copper_spell_book")==1,"Exactly one starting copper spellbook");check(itemCount(mc,"minecraft:bread")==8,"Exactly eight starting bread");
        var attribute=BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.parse("mastery:experience_gain")).orElseThrow();check(Math.abs(mc.player.getAttributeValue(attribute)-1.1)<0.00001,"XP class attribute applied exactly once");
    }
    private static int itemCount(Minecraft mc,String id){var item=BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));return mc.player.getInventory().items.stream().filter(stack->stack.is(item)).mapToInt(net.minecraft.world.item.ItemStack::getCount).sum();}
    private static void connect(Minecraft mc){ConnectScreen.startConnecting(new TitleScreen(),mc,ServerAddress.parseString("127.0.0.1:25578"),new ServerData("Mastery classes validation","127.0.0.1:25578",ServerData.Type.OTHER),false,null);}
    private static void openField(Minecraft mc,String key){mc.screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).max(Comparator.comparingInt(EditBox::getY)).orElseThrow().setValue(key);var b=mc.screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(v->v.getMessage().getString().startsWith(EditorSchema.label(key)+":")).findFirst().orElseThrow();mc.screen.mouseClicked(b.getX()+10,b.getY()+10,0);}
    private static void button(Minecraft mc,String name){var button=mc.screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(b->b.getMessage().getString().equals(name)).findFirst().orElseThrow(()->new IllegalStateException("Missing button: "+name));check(button.active,"Button active: "+name);mc.screen.mouseClicked(button.getX()+button.getWidth()/2.0,button.getY()+10,0);mc.screen.mouseReleased(button.getX()+button.getWidth()/2.0,button.getY()+10,0);}
    private static void capture(Minecraft mc,String file)throws Exception{try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(OUT.resolve(file));}}
    private static void next(String value)throws Exception{phase=value;age=0;Files.writeString(OUT.resolve("checkpoint.json"),"{\"phase\":\""+phase+"\"}");}
    private static void check(boolean valid,String message){if(!valid)throw new IllegalStateException(message);}
    private static void complete(Minecraft mc,Exception error){done=true;REPORT.addProperty("success",error==null);REPORT.addProperty("phase",phase);REPORT.addProperty("ticks",ticks);REPORT.addProperty("muted",true);REPORT.addProperty("hidden",true);if(error!=null)REPORT.addProperty("error",error.toString());try{Files.writeString(OUT.resolve("result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(REPORT));if(error!=null)capture(mc,"failure.png");}catch(Exception ignored){}if(mc.player!=null)mc.disconnect(new TitleScreen());mc.stop();}
}
