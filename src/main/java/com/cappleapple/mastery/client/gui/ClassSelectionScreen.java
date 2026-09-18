package com.cappleapple.mastery.client.gui;

import com.cappleapple.mastery.classes.*;
import com.cappleapple.mastery.client.ClientState;
import com.cappleapple.mastery.data.*;
import com.cappleapple.mastery.layout.BonusText;
import com.cappleapple.mastery.network.MasteryNetwork;
import com.cappleapple.mastery.progression.PlayerProgress;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Read-only equipment/progression projection. The server validates and commits the selected class. */
public final class ClassSelectionScreen extends Screen {
    private String selected="",previewError="";
    private List<String> ids=List.of();
    private int stripOffset,skillScroll,inventoryScroll,attributeScroll,age;
    private long definitionRevision=-1,requestRevision,progressRevision=-1;
    private boolean awaiting,ready;
    private Button confirm;
    private RemotePlayer mannequin;
    private ClassEquipment.Plan equipment;
    private PlayerProgress projected=new PlayerProgress();
    private List<SkillIcon> skills=List.of();
    private List<FormattedCharSequence> attributes=List.of();
    private final List<ItemHit> itemHits=new ArrayList<>();
    private final List<NodeHit> nodeHits=new ArrayList<>();
    private int skillHeight,inventoryHeight;
    private record SkillIcon(String id,String icon,int x,int y,String badge,boolean root) {}
    private record ItemHit(int x,int y,int width,int height,ItemStack stack) {
        boolean contains(double mx,double my){return mx>=x&&my>=y&&mx<x+width&&my<y+height;}
    }
    private record NodeHit(int x,int y,String id,NodeAppearance.Shape shape,double scale) {
        boolean contains(double mx,double my){return shape.contains((mx-x)/scale,(my-y)/scale,14);}
    }
    public ClassSelectionScreen(){super(Component.literal("Choose your class"));}
    public static boolean required(){
        var state=ClientState.runtime().getAsJsonObject("class_selection");
        return state!=null&&state.has("required")&&state.get("required").getAsBoolean();
    }
    public static void enforce(){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.player.isDeadOrDying())return;
        if(required()&&!ClientState.definitions().classes().isEmpty()){
            if(!(mc.screen instanceof ClassSelectionScreen))mc.setScreen(new ClassSelectionScreen());
            mc.mouseHandler.releaseMouse();
        }else if(mc.screen instanceof ClassSelectionScreen)mc.setScreen(null);
    }
    private static boolean canSelect(){var state=ClientState.runtime().getAsJsonObject("class_selection");return state!=null&&state.has("can_select")&&state.get("can_select").getAsBoolean();}
    private int sideWidth(){return Math.max(86,(width-48)/3);}
    private int bodyTop(){return height<300?82:98;}
    private int stripY(){return height<300?24:36;}
    private int bodyBottom(){return Math.max(bodyTop()+35,height-74);}
    private int stripCapacity(){return Math.max(1,(width-84)/32);}
    private int attributeTop(){return Math.max(bodyTop()+40,height-(height<300?104:116));}
    private static String name(String id){var data=ClientState.definitions().classes().get(id);return data==null?"class":text(data,"name",id);}
    private static String text(JsonObject data,String key,String fallback){return data.has(key)&&data.get(key).isJsonPrimitive()?data.get(key).getAsString():fallback;}
    @Override protected void init(){refreshClasses(true);}
    private void refreshClasses(boolean reveal){
        ids=ClientState.definitions().classes().keySet().stream().sorted(Comparator.comparing(ClassSelectionScreen::name).thenComparing(id->id)).toList();
        if(!ids.contains(selected))selected=ids.isEmpty()?"":ids.getFirst();
        if(reveal)revealSelection();
        definitionRevision=ClientState.definitionRevision();ready=canSelect();rebuildPreview();buttons();
    }
    private void revealSelection(){int index=ids.indexOf(selected),count=stripCapacity();if(index<stripOffset)stripOffset=Math.max(0,index);if(index>=stripOffset+count)stripOffset=index-count+1;stripOffset=Math.clamp(stripOffset,0,Math.max(0,ids.size()-count));}
    private void choose(String id){if(awaiting||!ids.contains(id))return;selected=id;skillScroll=inventoryScroll=attributeScroll=0;revealSelection();rebuildPreview();buttons();}
    private void step(int direction){if(!ids.isEmpty())choose(ids.get(Math.floorMod(ids.indexOf(selected)+direction,ids.size())));}
    private void scrollStrip(int direction){stripOffset=Math.clamp(stripOffset+direction,0,Math.max(0,ids.size()-stripCapacity()));buttons();}
    private void buttons(){
        clearWidgets();int count=Math.min(stripCapacity(),ids.size()),start=(width-count*32)/2;
        if(ids.size()>stripCapacity()){
            addRenderableWidget(Button.builder(Component.literal("<"),b->scrollStrip(-1)).bounds(14,stripY()+2,20,24).build()).active=stripOffset>0;
            addRenderableWidget(Button.builder(Component.literal(">"),b->scrollStrip(1)).bounds(width-34,stripY()+2,20,24).build()).active=stripOffset+count<ids.size();
        }
        for(int i=0;i<count;i++)addRenderableWidget(new ClassButton(ids.get(stripOffset+i),start+i*32,stripY())).active=!awaiting;
        int margin=16,gap=8,outer=Math.min(130,(width-margin*2-gap*2)/3),middle=width-margin*2-gap*2-outer*2,y=height-58;
        addRenderableWidget(Button.builder(Component.literal("Previous"),b->step(-1)).bounds(margin,y,outer,22).build()).active=ids.size()>1&&!awaiting;
        confirm=addRenderableWidget(Button.builder(Component.literal(awaiting?"Selecting...":"Choose "+name(selected)),b->{awaiting=true;requestRevision=ClientState.revision();MasteryNetwork.sendAction("class_select",selected,"",0);buttons();}).bounds(margin+outer+gap,y,middle,22).build());
        confirm.active=!awaiting&&ready&&previewError.isBlank()&&ids.contains(selected);
        addRenderableWidget(Button.builder(Component.literal("Next"),b->step(1)).bounds(width-margin-outer,y,outer,22).build()).active=ids.size()>1&&!awaiting;
        addRenderableWidget(Button.builder(Component.literal("Disconnect"),b->minecraft.disconnect(new TitleScreen())).bounds(margin,height-32,outer,20).build());
    }
    private final class ClassButton extends Button {
        private final String id;
        ClassButton(String id,int x,int y){super(x,y,28,28,Component.literal(name(id)),b->choose(id),DEFAULT_NARRATION);this.id=id;var data=ClientState.definitions().classes().get(id);setTooltip(Tooltip.create(Component.literal(name(id)+"\n"+text(data,"description",""))));}
        @Override protected void renderWidget(GuiGraphics g,int x,int y,float tick){
            boolean chosen=id.equals(selected);int left=getX(),top=getY();g.fill(left,top,left+width,top+height,chosen?0xFFE2B871:isHoveredOrFocused()?0xFF728396:0xFF374350);g.fill(left+1,top+1,left+width-1,top+height-1,chosen?0xFF3C3528:0xFF202A36);
            var data=ClientState.definitions().classes().get(id);if(data!=null)icon(g,text(data,"icon","minecraft:book"),left+6,top+6);
        }
    }
    private void rebuildPreview(){
        previewError="";equipment=null;skills=List.of();attributes=List.of();progressRevision=ClientState.revision();
        var raw=ClientState.definitions().classes().get(selected);if(raw==null||minecraft.player==null||minecraft.level==null)return;
        try {
            var definition=ClassDefinition.parse(selected,raw);projected=ClassRewards.prepare(ClientState.definitions(),ClientState.progress(),definition);
            var stacks=new ArrayList<ItemStack>();var ops=RegistryOps.create(JsonOps.INSTANCE,minecraft.level.registryAccess());
            for(var item:definition.startingInventory())stacks.add(ItemStack.CODEC.parse(ops,item).getOrThrow());
            equipment=ClassEquipment.prepare(minecraft.player,stacks,true);
            if(mannequin==null||mannequin.level()!=minecraft.level){
                mannequin=new RemotePlayer(minecraft.level,minecraft.player.getGameProfile()){
                    @Override public boolean isSpectator(){return false;}
                    @Override public boolean isModelPartShown(PlayerModelPart part){return Minecraft.getInstance().player!=null&&Minecraft.getInstance().player.isModelPartShown(part);}
                };
                // This detached entity is never registered in the world; keep its name tag outside camera range.
                mannequin.setPos(0,-10000,0);
            }
            equipment.armor().forEach((slot,stack)->mannequin.setItemSlot(slot,stack.copy()));
            var lines=new ArrayList<FormattedCharSequence>();int available=Math.max(90,width-2*(sideWidth()+24));
            for(var attribute:definition.attributes()){
                var effect=new JsonObject();effect.addProperty("attribute",attribute.attribute());effect.addProperty("amount",attribute.amount());effect.addProperty("operation",attribute.operation());
                for(String line:BonusText.describe(effect,1,ClassSelectionScreen::attributeName,ClassSelectionScreen::percentageAttribute))lines.addAll(font.split(Component.literal(line),available));
            }
            attributes=List.copyOf(lines);buildSkills(definition);
        }catch(RuntimeException error){previewError=Objects.toString(error.getMessage(),"Class preview unavailable");}
        clampScrolls();
    }
    private static String attributeName(String id){var attribute=BuiltInRegistries.ATTRIBUTE.get(ResourceLocation.parse(id));return attribute==null?id:Component.translatable(attribute.getDescriptionId()).getString();}
    private static boolean percentageAttribute(String id){var attribute=BuiltInRegistries.ATTRIBUTE.get(ResourceLocation.parse(id));return attribute instanceof io.redspace.ironsspellbooks.api.attribute.MagicPercentAttribute||attribute instanceof net.neoforged.neoforge.common.PercentageAttribute;}
    private void buildSkills(ClassDefinition definition){
        var roots=new TreeMap<String,List<String>>();definition.startingPoints().keySet().forEach(id->roots.put(id,new ArrayList<>()));
        definition.startingSkills().keySet().stream().sorted().forEach(id->{var node=ClientState.definitions().nodes().get(id);if(node!=null)roots.computeIfAbsent(node.tree(),k->new ArrayList<>()).add(id);});
        var result=new ArrayList<SkillIcon>();int y=34,columns=Math.max(1,(sideWidth()-52)/34);
        for(var entry:roots.entrySet()){
            var tree=ClientState.definitions().trees().get(entry.getKey());if(tree==null)continue;
            var root=PromotedTrees.root(ClientState.definitions(),tree.id());String rootId=root==null?tree.id():root.id();
            int points=projected.tree(tree.id()).points();result.add(new SkillIcon(rootId,root==null?tree.icon():root.icon(),18,y,points>0?Integer.toString(points):"",true));
            int index=0;for(String id:entry.getValue()){
                var node=ClientState.definitions().nodes().get(id);result.add(new SkillIcon(id,node.icon(),56+(index%columns)*34,y+(index/columns)*42,projected.rank(id)+"/"+node.maxRank(),false));index++;
            }
            y+=Math.max(1,(index+columns-1)/columns)*42+14;
        }
        skills=List.copyOf(result);skillHeight=y;
    }
    private void clampScrolls(){
        int span=bodyBottom()-bodyTop();skillScroll=Math.clamp(skillScroll,0,Math.max(0,skillHeight-span));
        inventoryHeight=180+(minecraft.player!=null&&!minecraft.player.getOffhandItem().isEmpty()?30:0)+(equipment!=null&&!equipment.overflow().isEmpty()?22+((equipment.overflow().size()+2)/3)*20:0);
        inventoryScroll=Math.clamp(inventoryScroll,0,Math.max(0,inventoryHeight-span));attributeScroll=Math.clamp(attributeScroll,0,Math.max(0,attributes.size()-Math.max(1,(height-74-attributeTop())/11)));
    }
    @Override public void tick(){
        if(!required()){minecraft.setScreen(null);return;}age++;
        if(awaiting&&ClientState.revision()>requestRevision){awaiting=false;refreshClasses(false);}
        else if(definitionRevision!=ClientState.definitionRevision())refreshClasses(true);
        else if(ready!=canSelect()){ready=canSelect();buttons();}
        else if(progressRevision!=ClientState.revision()||age%20==0){rebuildPreview();confirm.active=!awaiting&&ready&&previewError.isBlank()&&ids.contains(selected);}
    }
    @Override public boolean shouldCloseOnEsc(){return false;}
    @Override public void onClose(){}
    @Override public boolean isPauseScreen(){return false;}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        double delta=horizontal!=0?horizontal:-vertical;if(delta==0)return false;
        if(y<stripY()+36){scrollStrip(delta>0?1:-1);return true;}
        if(x<sideWidth()+16)skillScroll+=(delta>0?1:-1)*26;
        else if(x>width-sideWidth()-16)inventoryScroll+=(delta>0?1:-1)*20;
        else if(y>=attributeTop())attributeScroll+=delta>0?1:-1;
        else return false;
        clampScrolls();return true;
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT){step(-1);return true;}if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT){step(1);return true;}return super.keyPressed(key,scan,modifiers);}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float tick){}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float tick){
        itemHits.clear();nodeHits.clear();g.fill(0,0,width,height,0xFF171C24);g.drawCenteredString(font,title,width/2,height<300?8:12,0xFFE2B3);
        g.drawCenteredString(font,name(selected),width/2,bodyTop()-22,0xFFE2B3);g.drawCenteredString(font,"Starting skills",16+sideWidth()/2,bodyTop()-16,0xB8C8D8);g.drawCenteredString(font,"Inventory",width-16-sideWidth()/2,bodyTop()-16,0xB8C8D8);
        renderSkills(g);renderEquipment(g);renderInventory(g);renderAttributes(g);
        String error=!previewError.isBlank()?previewError:ClientState.runtime().has("last_error")?ClientState.runtime().get("last_error").getAsString():"";
        if(!error.isBlank())g.drawString(font,font.plainSubstrByWidth(error,width-32),16,height-70,0xFF9999);
        super.render(g,mouseX,mouseY,tick);
        for(var hit:itemHits)if(hit.contains(mouseX,mouseY)&&!hit.stack().isEmpty()){g.renderTooltip(font,hit.stack(),mouseX,mouseY);return;}
        for(var hit:nodeHits)if(hit.contains(mouseX,mouseY)){g.renderComponentTooltip(font,NodeDetails.tooltip(hit.id(),projected),mouseX,mouseY);return;}
    }
    private void renderSkills(GuiGraphics g){
        g.enableScissor(14,bodyTop(),18+sideWidth(),bodyBottom());
        if(skills.isEmpty())g.drawString(font,"No starting skills",18,bodyTop()+8,0x8292A5);
        for(var skill:skills){int x=18+skill.x(),y=bodyTop()+skill.y()-skillScroll;if(y+26<bodyTop()||y-23>bodyBottom())continue;
            var look=NodeAppearance.parse(SettingsResolver.forNode(ClientState.definitions(),skill.id()).getAsJsonObject("appearance"));var shape=look.shape();float scale=(float)look.scaleFor(skill.root());
            g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(scale,scale,1);NodeShapes.fill(g,shape,14,skill.root()?0xFFE2B871:0xFF8696A7);NodeShapes.fill(g,shape,12,0xFF283341);g.flush();icon(g,skill.icon(),-8,-8+shape.iconOffsetY(12));g.pose().popPose();
            if(!skill.badge().isBlank())g.drawCenteredString(font,skill.badge(),x,skill.root()?y-(int)Math.ceil(14*scale)-12:y+(int)Math.ceil(14*scale)+2,skill.root()?0xFFFFDC88:0xD8E5EE);
            if(y-14>=bodyTop()&&y+14<=bodyBottom())nodeHits.add(new NodeHit(x,y,skill.id(),shape,scale));
        }
        g.disableScissor();scrollbar(g,16+sideWidth(),skillScroll,skillHeight);
    }
    private void renderEquipment(GuiGraphics g){
        if(mannequin==null||equipment==null)return;int left=24+sideWidth(),right=width-24-sideWidth(),top=bodyTop()-4,bottom=attributeTop()-6;
        int scale=Math.max(14,(int)Math.min((bottom-top-6)/1.875,(right-left-8)*.85));
        InventoryScreen.renderEntityInInventoryFollowsAngle(g,left,top,right,bottom,scale,0.0625F,0,0,mannequin);
        double head=(top+bottom)/2.0-.9125*scale,cx=width/2.0;
        armorHit(EquipmentSlot.HEAD,cx-.28*scale,head-2,.56*scale,.5*scale+2);
        armorHit(EquipmentSlot.CHEST,cx-.52*scale,head+.5*scale,1.04*scale,.75*scale);
        armorHit(EquipmentSlot.LEGS,cx-.28*scale,head+1.25*scale,.56*scale,.375*scale);
        armorHit(EquipmentSlot.FEET,cx-.28*scale,head+1.625*scale,.56*scale,.25*scale+2);
    }
    private void armorHit(EquipmentSlot slot,double x,double y,double w,double h){var stack=equipment.armor().get(slot);if(stack!=null&&!stack.isEmpty())itemHits.add(new ItemHit((int)x,(int)y,(int)Math.ceil(w),(int)Math.ceil(h),stack));}
    private void renderInventory(GuiGraphics g){
        if(equipment==null)return;int left=width-16-sideWidth()+(sideWidth()-88)/2,top=bodyTop()-inventoryScroll;
        g.enableScissor(width-18-sideWidth(),bodyTop(),width-12,bodyBottom());
        // Vanilla's three storage rows become three columns; the hotbar is a fourth vertical column.
        for(int index=0;index<27;index++)item(g,equipment.inventory().get(index+9),left+(index/9)*20,top+(index%9)*20);
        for(int index=0;index<9;index++)item(g,equipment.inventory().get(index),left+70,top+index*20);
        int y=top+182;if(!minecraft.player.getOffhandItem().isEmpty()){g.drawString(font,"Offhand",left,y+4,0x8292A5);item(g,minecraft.player.getOffhandItem(),left+70,y);y+=30;}
        if(!equipment.overflow().isEmpty()){g.drawString(font,"Awaiting space",left,y,0xD4AE71);y+=18;int index=0;for(var stack:equipment.overflow()){item(g,stack,left+(index%3)*24,y+(index/3)*20);index++;}}
        g.disableScissor();scrollbar(g,width-14,inventoryScroll,inventoryHeight);
    }
    private void item(GuiGraphics g,ItemStack stack,int x,int y){if(stack.isEmpty()||y+16<bodyTop()||y>bodyBottom())return;g.renderItem(stack,x,y);g.renderItemDecorations(font,stack,x,y);if(y>=bodyTop()&&y+16<=bodyBottom())itemHits.add(new ItemHit(x,y,18,18,stack));}
    private void renderAttributes(GuiGraphics g){
        int left=22+sideWidth(),right=width-left,top=attributeTop();g.enableScissor(left,top,right,height-74);
        if(attributes.isEmpty())g.drawCenteredString(font,"No class attributes",width/2,top,0x8292A5);
        for(int i=attributeScroll;i<attributes.size();i++){int y=top+(i-attributeScroll)*11;if(y+9>height-74)break;g.drawCenteredString(font,attributes.get(i),width/2,y,0xCFDCED);}
        g.disableScissor();
    }
    private void scrollbar(GuiGraphics g,int x,int scroll,int content){int span=bodyBottom()-bodyTop();if(content<=span)return;int bar=Math.max(12,span*span/content),y=bodyTop()+(span-bar)*scroll/(content-span);g.fill(x,y,x+2,y+bar,0xFF65758A);}
    private static void icon(GuiGraphics g,String id,int x,int y){var key=ResourceLocation.tryParse(id);if(key!=null&&BuiltInRegistries.ITEM.containsKey(key))g.renderItem(new ItemStack(BuiltInRegistries.ITEM.get(key)),x,y);else{var texture=IconResources.texture(id);if(texture!=null)g.blit(texture,x,y,0,0,16,16,16,16);}}
}
