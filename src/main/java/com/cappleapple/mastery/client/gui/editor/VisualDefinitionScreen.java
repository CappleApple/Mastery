package com.cappleapple.mastery.client.gui.editor;

import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Recursive forms edit one shared draft. Only Save submits it to the authoritative server. */
public final class VisualDefinitionScreen extends Screen {
    final DefinitionEditorScreen owner;
    private final Screen parent;private final JsonElement container;private final String path;
    private int page;private String filter="";private List<String> visibleKeys=List.of();
    private EditBox search;private int rowCount;
    public VisualDefinitionScreen(DefinitionEditorScreen owner,Screen parent,JsonElement container,String path){
        super(Component.literal(path.isBlank()?"Edit "+EditorSchema.label(owner.kind()):EditorSchema.label(path.substring(path.lastIndexOf('/')+1))));
        this.owner=owner;this.parent=parent;this.container=container;this.path=path;
    }
    @Override protected void init(){
        boolean root=path.isBlank();
        if(root){
            var id=addRenderableWidget(new EditBox(font,95,35,width-115,20,Component.literal("Resource ID")));id.setMaxLength(256);id.setValue(owner.formId());id.setEditable(owner.creating());id.setResponder(owner::formId);
        }
        search=addRenderableWidget(new EditBox(font,16,root?64:36,width-32,20,Component.literal("Find a setting")));search.setHint(Component.literal("Find a setting..."));search.setValue(filter);
        search.setResponder(value->{filter=value;page=0;refresh();});refresh();
    }
    private void refresh(){
        var persistent=children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).toList();clearWidgets();persistent.forEach(this::addRenderableWidget);
        boolean root=path.isBlank();int start=root?94:66;rowCount=Math.max(1,(height-start-86)/28);
        LinkedHashSet<String> keys=new LinkedHashSet<>();
        JsonObject schema=container.isJsonObject()?EditorSchema.fields(owner.kind(),path,container.getAsJsonObject()):new JsonObject();
        if(container.isJsonObject()){keys.addAll(schema.keySet());keys.addAll(container.getAsJsonObject().keySet());}
        else for(int i=0;i<container.getAsJsonArray().size();i++)keys.add(Integer.toString(i));
        visibleKeys=keys.stream().filter(key->EditorSchema.label(key).toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT))||key.contains(filter)).toList();
        int pages=Math.max(1,(visibleKeys.size()+rowCount-1)/rowCount);page=Math.min(page,pages-1);
        for(int i=page*rowCount;i<Math.min(visibleKeys.size(),(page+1)*rowCount);i++){
            String key=visibleKeys.get(i);JsonElement current=get(key);JsonElement seed=schema.has(key)?schema.get(key):new JsonPrimitive("");int y=start+(i%rowCount)*28;
            String label=container.isJsonArray()?"Entry "+(Integer.parseInt(key)+1):EditorSchema.label(key);
            String summary=current==null?"Default":current.isJsonObject()?current.getAsJsonObject().size()+" settings":current.isJsonArray()?current.getAsJsonArray().size()+" entries":current.isJsonNull()?"None":current.getAsString();
            var edit=addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(label+": "+summary,width-124)),b->edit(key,current,seed)).bounds(16,y,width-112,22).build());edit.setTooltip(Tooltip.create(Component.literal(EditorSchema.hint(key))));
            addRenderableWidget(Button.builder(Component.literal(container.isJsonArray()?"-":"Reset"),b->{remove(key);refresh();}).bounds(width-90,y,54,22).build()).active=current!=null;
            if(container.isJsonArray()&&Integer.parseInt(key)>0)addRenderableWidget(Button.builder(Component.literal("^"),b->{int index=Integer.parseInt(key);var a=container.getAsJsonArray();var previous=a.get(index-1);a.set(index-1,a.get(index));a.set(index,previous);refresh();}).bounds(width-34,y,18,22).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Previous"),b->{page--;refresh();}).bounds(16,height-78,72,20).build()).active=page>0;
        addRenderableWidget(Button.builder(Component.literal("Next"),b->{page++;refresh();}).bounds(92,height-78,62,20).build()).active=page+1<pages;
        addRenderableWidget(Button.builder(Component.literal(container.isJsonArray()?"Add entry":"Add field"),b->add()).bounds(160,height-78,88,20).build());
        if(container.isJsonArray()&&(path.contains("dependencies")||path.contains("costs"))) {
            addRenderableWidget(Button.builder(Component.literal("Add AND"),b->addGroup("and")).bounds(254,height-78,78,20).build());
            addRenderableWidget(Button.builder(Component.literal("Add OR"),b->addGroup("or")).bounds(338,height-78,78,20).build());
        }
        if(path.equals("/costs")&&parent instanceof VisualDefinitionScreen enclosing) {
            var legacy=addRenderableWidget(Button.builder(Component.literal("Legacy point cost"),b->{enclosing.put("costs",JsonNull.INSTANCE);minecraft.setScreen(parent);}).bounds(width-150,10,134,20).build());
            legacy.setTooltip(Tooltip.create(Component.literal("Override inherited custom costs with the node's original numeric point cost.")));
        }
        if(container.isJsonObject()&&path.contains("costs")) {
            addRenderableWidget(Button.builder(Component.literal("Use AND"),b->costGroup("and")).bounds(254,height-78,78,20).build());
            addRenderableWidget(Button.builder(Component.literal("Use OR"),b->costGroup("or")).bounds(338,height-78,78,20).build());
        }
        if(root&&owner.kind().equals("nodes")){
            boolean gated=container.getAsJsonObject().has("book_token")&&!container.getAsJsonObject().get("book_token").getAsString().isBlank();
            addRenderableWidget(Button.builder(Component.literal("Require Skill Book: "+(gated?"On":"Off")),b->{container.getAsJsonObject().addProperty("book_token",gated?"":owner.formId());refresh();}).bounds(254,height-78,Math.min(164,width-270),20).build());
            if(gated)addRenderableWidget(Button.builder(Component.literal("Copy book command"),b->minecraft.keyboardHandler.setClipboard(BookGateEditor.giveCommand(container.getAsJsonObject().get("book_token").getAsString()))).bounds(width-176,12,160,20).build());
        }
        addRenderableWidget(Button.builder(Component.literal(root?"Save to world":"Done"),b->{if(root)owner.saveForm();else onClose();}).bounds(16,height-30,126,20).build()).active=!owner.busy();
        addRenderableWidget(Button.builder(Component.literal("JSON editor"),b->owner.openJson()).bounds(148,height-30,116,20).build());
        addRenderableWidget(Button.builder(Component.literal(root&&!(parent instanceof VisualScriptScreen)?"Cancel":"Back"),b->{if(root&&!(parent instanceof VisualScriptScreen))owner.closeForm();else onClose();}).bounds(width-106,height-30,90,20).build());
    }
    private JsonElement get(String key){return container.isJsonObject()?container.getAsJsonObject().get(key):container.getAsJsonArray().get(Integer.parseInt(key));}
    private void put(String key,JsonElement value){if(container.isJsonObject())container.getAsJsonObject().add(key,value);else container.getAsJsonArray().set(Integer.parseInt(key),value);}
    private void remove(String key){if(container.isJsonObject())container.getAsJsonObject().remove(key);else container.getAsJsonArray().remove(Integer.parseInt(key));}
    private void edit(String key,JsonElement current,JsonElement seed){
        String nextPath=container.isJsonArray()?path:path+"/"+key;
        JsonElement value=current==null?seed.deepCopy():current;
        if(seed.isJsonObject()&&value.isJsonNull())value=seed.deepCopy();
        if(seed.isJsonObject()&&value.isJsonPrimitive()&&value.getAsString().equals("default"))value=key.equals("costs")?seed.deepCopy():new JsonObject();
        if(key.equals("modifier_slots")&&value.isJsonPrimitive()&&value.getAsJsonPrimitive().isNumber()) {var slots=new JsonObject();slots.add("base",value);value=slots;}
        if(key.equals("root_tree")&&value.isJsonPrimitive()&&value.getAsJsonPrimitive().isBoolean()){var root=new JsonObject();root.addProperty("enabled",value.getAsBoolean());value=root;}
        if(container.isJsonArray()&&value.isJsonPrimitive()&&value.getAsJsonPrimitive().isString()) {
            if(path.contains("dependencies")){var dependency=new JsonObject();dependency.addProperty("node",value.getAsString());dependency.addProperty("rank",1);value=dependency;}
            else if(path.endsWith("/requirements")||path.endsWith("/effects")){var reference=new JsonObject();reference.addProperty("ref",value.getAsString());value=reference;}
        }
        if(value.isJsonObject()||value.isJsonArray()){
            put(key,value);minecraft.setScreen(new VisualDefinitionScreen(owner,this,value,nextPath));
        }else minecraft.setScreen(new EditorValueScreen(this,owner.kind(),nextPath,container.isJsonArray()?path.substring(path.lastIndexOf('/')+1):key,value,v->put(key,v)));
    }
    private void costGroup(String operator) {
        var object=container.getAsJsonObject();JsonArray entries;
        if(object.has("and"))entries=object.getAsJsonArray("and");
        else if(object.has("or"))entries=object.getAsJsonArray("or");
        else {entries=new JsonArray();entries.add(object.deepCopy());}
        for(String key:new ArrayList<>(object.keySet()))object.remove(key);
        object.add(operator,entries);filter="";page=0;refresh();
    }
    private void addGroup(String operator){
        var group=new JsonObject();var children=new JsonArray();children.add(EditorSchema.entry(path));group.add(operator,children);
        container.getAsJsonArray().add(group);page=Math.max(0,(container.getAsJsonArray().size()-1)/rowCount);refresh();
    }
    private void add(){
        if(container.isJsonArray()){container.getAsJsonArray().add(EditorSchema.entry(path));page=Math.max(0,(container.getAsJsonArray().size()-1)/rowCount);refresh();}
        else if(path.endsWith("starting_points")||path.endsWith("starting_skills")) {
            var choices=path.endsWith("starting_points")?com.cappleapple.mastery.client.ClientState.definitions().trees().keySet():com.cappleapple.mastery.client.ClientState.definitions().nodes().keySet();
            minecraft.setScreen(new EditorChoiceScreen(this,path.endsWith("starting_points")?"Specialization":"Starting skill",choices.stream().filter(id->!container.getAsJsonObject().has(id)).sorted().toList(),id->{container.getAsJsonObject().addProperty(id,1);filter=id;page=0;}));
        }
        else if(path.endsWith("damage_modifiers")) {
            var groups=com.cappleapple.mastery.client.ClientState.definitions().toJson().getAsJsonObject("mob_types");
            minecraft.setScreen(new EditorChoiceScreen(this,"Mob group",groups.keySet().stream().filter(id->!container.getAsJsonObject().has(id)).sorted().toList(),id->{container.getAsJsonObject().addProperty(id,0);filter=id;page=0;}));
        }
        else minecraft.setScreen(new EditorValueScreen(this,owner.kind(),path,"Field name",new JsonPrimitive(""),value->{String key=value.getAsString().strip();if(key.isEmpty()||container.getAsJsonObject().has(key))throw new IllegalArgumentException("Choose a new nonempty field name.");container.getAsJsonObject().add(key,path.endsWith("point_milestones")?new JsonPrimitive(1):path.endsWith("damage_modifiers")?new JsonPrimitive(0):new JsonPrimitive(""));filter=key;page=0;}));
    }
    public void serverStatus(boolean success,String message){owner.serverStatus(success,message);if(!success)refresh();}
    @Override public void tick(){if(!com.cappleapple.mastery.client.ClientState.editMode())owner.returnToMap();}
    @Override public boolean mouseClicked(double x,double y,int button){return owner.busy()||super.mouseClicked(x,y,button);}
    @Override public boolean charTyped(char character,int modifiers){return owner.busy()||super.charTyped(character,modifiers);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(owner.busy())return true;if(hasControlDown()&&key==GLFW.GLFW_KEY_S){owner.saveForm();return true;}return super.keyPressed(key,scan,modifiers);}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){if(search.isFocused())search.setFocused(false);page=Math.max(0,page+(dy<0?1:-1));refresh();return true;}
    @Override public void onClose(){if(path.isBlank()&&!(parent instanceof VisualScriptScreen))owner.closeForm();else minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float tick){}
    @Override public void render(GuiGraphics g,int x,int y,float tick){
        g.fill(0,0,width,height,0xFF20252D);g.drawString(font,title,16,14,0xFFE2B3);if(path.isBlank())g.drawString(font,"Resource ID",16,41,0xCCCCCC);
        g.drawString(font,font.plainSubstrByWidth(owner.message(),width-32),16,height-49,owner.failed()?0xFF9999:0xAABBCB);super.render(g,x,y,tick);
    }
}
