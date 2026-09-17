package com.cappleapple.mastery.client.gui.editor;

import com.cappleapple.mastery.client.ClientState;
import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** A connected event/condition/action view over the same authoritative definition draft. */
public final class VisualScriptScreen extends Screen {
    private final DefinitionEditorScreen owner;
    private final JsonObject draft;
    private int page;
    private boolean threshold;
    private JsonObject pending;
    private String pendingPath;
    private int left, column, top, rows;

    public VisualScriptScreen(DefinitionEditorScreen owner, JsonObject draft) {
        super(Component.literal(owner.kind().equals("keywords") ? "Keyword script" : "Combat trigger script"));
        this.owner = owner; this.draft = draft;
    }
    private boolean keyword() { return owner.kind().equals("keywords"); }
    private String actionsKey() { return keyword() ? threshold ? "threshold_actions" : "tick_actions" : "actions"; }
    private JsonArray list(String key) { if(!draft.has(key))draft.add(key,new JsonArray());return draft.getAsJsonArray(key); }
    @Override protected void init() {
        left=14;column=(width-48)/3;top=108;rows=Math.max(1,(height-top-94)/31);
        var id=addRenderableWidget(new EditBox(font,88,34,width-102,20,Component.literal("Resource ID")));
        id.setMaxLength(256);id.setValue(owner.formId());id.setEditable(owner.creating());id.setResponder(owner::formId);
        addRenderableWidget(Button.builder(Component.literal("Settings"),b->minecraft.setScreen(new VisualDefinitionScreen(owner,this,draft,""))).bounds(left,65,90,20).build());
        if(keyword())addRenderableWidget(Button.builder(Component.literal(threshold?"Threshold actions":"Periodic actions"),b->{threshold=!threshold;page=0;rebuildWidgets();}).bounds(110,65,Math.min(150,width-234),20).build());
        addRenderableWidget(Button.builder(Component.literal("JSON editor"),b->owner.openJson()).bounds(width-118,65,104,20).build());
        String event=keyword()?threshold?"Stack threshold":"Periodic tick":draft.has("event")?draft.get("event").getAsString():"hit";
        addRenderableWidget(Button.builder(Component.literal(EditorSchema.label(event)),b->{
            if(keyword())openValue(threshold?"threshold":"tick_interval",new JsonPrimitive(threshold?5:20));
            else minecraft.setScreen(new EditorChoiceScreen(this,"When this happens",java.util.List.of("hit","kill","hurt","death"),v->draft.addProperty("event",v)));
        }).bounds(left+7,top+8,column-14,25).build());
        if(keyword()) {
            fieldButton("max_stacks",new JsonPrimitive(10),top+43);
            fieldButton("duration",new JsonPrimitive(100),top+68);
            if(threshold)fieldButton("consume_stacks",new JsonPrimitive(true),top+93);
        } else {
            fieldButton("chance",new JsonPrimitive(1),top+43);
            fieldButton("cooldown",new JsonPrimitive(0),top+68);
        }
        int conditionsX=left+column+10,actionsX=left+(column+10)*2;
        JsonArray conditions=keyword()?new JsonArray():list("conditions");
        JsonArray actions=list(actionsKey());
        int pages=Math.max(1,(Math.max(conditions.size(),actions.size())+rows-1)/rows);page=Math.min(page,pages-1);
        for(int row=0;row<rows;row++) {
            int index=page*rows+row,y=top+8+row*31;
            if(index<conditions.size())card(conditions, index, conditionsX,y,"conditions",false);
            if(index<actions.size())card(actions,index,actionsX,y,actionsKey(),true);
        }
        if(!keyword())addRenderableWidget(Button.builder(Component.literal("+ Condition"),b->minecraft.setScreen(new EditorChoiceScreen(this,"Condition type",java.util.List.of("health","keyword"),v->{pending=ScriptSchema.condition(v);pendingPath="conditions";conditions.add(pending);}))).bounds(conditionsX+5,height-78,column-10,20).build());
        else addRenderableWidget(Button.builder(Component.literal("Stack settings"),b->minecraft.setScreen(new VisualDefinitionScreen(owner,this,draft,""))).bounds(conditionsX+5,height-78,column-10,20).build());
        addRenderableWidget(Button.builder(Component.literal("+ Action"),b->minecraft.setScreen(new EditorChoiceScreen(this,"Action type",ScriptSchema.ACTIONS,v->{pending=ScriptSchema.action(v);pendingPath=actionsKey();actions.add(pending);}))).bounds(actionsX+5,height-78,column-10,20).build());
        addRenderableWidget(Button.builder(Component.literal("<"),b->{page--;rebuildWidgets();}).bounds(left,height-78,28,20).build()).active=page>0;
        addRenderableWidget(Button.builder(Component.literal(">"),b->{page++;rebuildWidgets();}).bounds(left+32,height-78,28,20).build()).active=page+1<pages;
        addRenderableWidget(Button.builder(Component.literal("Save to world"),b->owner.saveForm()).bounds(left,height-29,124,20).build()).active=!owner.busy();
        addRenderableWidget(Button.builder(Component.literal("Cancel"),b->owner.closeForm()).bounds(width-98,height-29,84,20).build());
    }
    private void fieldButton(String key,JsonPrimitive fallback,int y) {
        JsonElement value=draft.has(key)?draft.get(key):fallback;
        addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(EditorSchema.label(key)+": "+value.getAsString(),column-20)),b->openValue(key,fallback)).bounds(left+7,y,column-14,21).build());
    }
    private void openValue(String key,JsonPrimitive fallback) {
        minecraft.setScreen(new EditorValueScreen(this,owner.kind(),"/"+key,key,draft.has(key)?draft.get(key):fallback,v->draft.add(key,v)));
    }
    private void card(JsonArray list,int index,int x,int y,String path,boolean action) {
        JsonObject value=list.get(index).getAsJsonObject();
        String type=value.has("type")?value.get("type").getAsString():"condition";
        String target=value.has("target")?value.get("target").getAsString():"target";
        String label=(index+1)+". "+EditorSchema.label(type)+" / "+target;
        var button=addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(label,column-54)),b->minecraft.setScreen(new VisualDefinitionScreen(owner,this,value,"/"+path))).bounds(x+5,y,column-51,24).build());
        button.setTooltip(Tooltip.create(Component.literal(label+". Click to configure.")));
        addRenderableWidget(Button.builder(Component.literal("-"),b->{list.remove(index);rebuildWidgets();}).bounds(x+column-42,y,17,24).build());
        addRenderableWidget(Button.builder(Component.literal("^"),b->{var previous=list.get(index-1);list.set(index-1,list.get(index));list.set(index,previous);rebuildWidgets();}).bounds(x+column-23,y,17,24).build()).active=index>0;
    }
    public void serverStatus(boolean success,String message) {owner.serverStatus(success,message);if(!success)rebuildWidgets();}
    @Override public void tick() {
        if(!ClientState.editMode()){owner.returnToMap();return;}
        if(pending!=null){var next=pending;pending=null;minecraft.setScreen(new VisualDefinitionScreen(owner,this,next,"/"+pendingPath));}
    }
    @Override public boolean mouseClicked(double x,double y,int button){return owner.busy()||super.mouseClicked(x,y,button);}
    @Override public boolean keyPressed(int key,int scan,int mods){if(owner.busy())return true;if(hasControlDown()&&key==GLFW.GLFW_KEY_S){owner.saveForm();return true;}return super.keyPressed(key,scan,mods);}
    @Override public void onClose(){owner.closeForm();}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float tick){}
    @Override public void render(GuiGraphics g,int x,int y,float tick) {
        g.fill(0,0,width,height,0xFF20252D);g.drawString(font,title,left,14,0xFFE2B3);g.drawString(font,"Resource ID",left,41,0xCCCCCC);
        for(int i=0;i<3;i++){int x0=left+i*(column+10);g.fill(x0,top,x0+column,height-84,0xFF293442);g.drawString(font,i==0?"WHEN":i==1?keyword()?"STACK STATE":"IF (all match)":"THEN (in order)",x0+6,94,0xAAC9E9);if(i<2)g.drawString(font,">",x0+column+2,top+14,0xFFE2B3);}
        if(keyword())g.drawWordWrap(font,Component.literal(threshold?"Reaching the threshold runs this chain. Optional consumption removes the threshold stacks.":"Each interval runs this chain while the keyword remains active. Damage can scale with stacks."),left+column+18,top+10,column-16,0xC5CFDC);
        else if(list("conditions").isEmpty())g.drawWordWrap(font,Component.literal("No conditions: every matching event is eligible. Add health or keyword checks."),left+column+18,top+10,column-16,0xC5CFDC);
        g.drawString(font,font.plainSubstrByWidth(owner.message(),width-28),left,height-47,owner.failed()?0xFF9999:0xAABBCB);
        super.render(g,x,y,tick);
    }
}
