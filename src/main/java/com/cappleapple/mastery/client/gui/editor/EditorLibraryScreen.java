package com.cappleapple.mastery.client.gui.editor;
import com.cappleapple.mastery.client.ClientState;
import com.cappleapple.mastery.client.gui.MasteryScreen;
import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Access to every definition kind, including XP rules and reusable effects and requirements. */
public final class EditorLibraryScreen extends Screen {
    private static final List<String> KINDS=List.of("trees","nodes","spells","settings","xp_sources","requirements","effects","triggers","keywords","elements","mob_types","weapon_types","contexts","groups");
    private final MasteryScreen parent;private String kind="trees",query="";private int page;
    public EditorLibraryScreen(MasteryScreen parent){super(Component.literal("Mastery definitions"));this.parent=parent;}
    @Override protected void init(){
        addRenderableWidget(Button.builder(Component.literal(EditorSchema.label(kind)),b->minecraft.setScreen(new EditorChoiceScreen(this,"Definition type",KINDS,value->{kind=value;query="";page=0;}))).bounds(16,36,145,20).build());
        var search=addRenderableWidget(new EditBox(font,167,36,width-183,20,Component.literal("Search definitions")));search.setValue(query);search.setResponder(value->{query=value;page=0;rebuildWidgets();});setInitialFocus(search);
        var entries=ClientState.definitions().toJson().getAsJsonObject(kind);var ids=entries.keySet().stream().sorted().filter(id->id.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).toList();
        int count=Math.max(1,(height-117)/25),pages=Math.max(1,(ids.size()+count-1)/count);page=Math.min(page,pages-1);
        for(int i=page*count;i<Math.min(ids.size(),(page+1)*count);i++){
            String id=ids.get(i);int y=65+(i%count)*25;
            addRenderableWidget(Button.builder(Component.literal(id),b->EditorClient.editDefinition(parent,kind,id)).bounds(16,y,width-112,21).build());
            addRenderableWidget(Button.builder(Component.literal("Delete"),b->EditorClient.deleteDefinition(parent,kind,id)).bounds(width-90,y,74,21).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Previous"),b->{page--;rebuildWidgets();}).bounds(16,height-32,76,20).build()).active=page>0;
        addRenderableWidget(Button.builder(Component.literal("Next"),b->{page++;rebuildWidgets();}).bounds(96,height-32,66,20).build()).active=page+1<pages;
        addRenderableWidget(Button.builder(Component.literal("New definition"),b->create()).bounds(168,height-32,126,20).build()).active=!kind.equals("settings");
        addRenderableWidget(Button.builder(Component.literal("Back"),b->onClose()).bounds(width-100,height-32,84,20).build());
    }
    private void create(){
        var draft=EditorSchema.fields(kind,"",new JsonObject());String id="mastery:new_"+kind;
        if(kind.equals("elements"))draft.remove("damage_type");
        if(kind.equals("trees"))draft=EditorDrafts.tree();
        if(kind.equals("nodes"))draft=EditorDrafts.node(ClientState.definitions().trees().keySet().stream().sorted().findFirst().orElse(""),"");
        if(kind.equals("requirements"))draft=JsonParser.parseString("{\"type\":\"mastery:world_tier\",\"tier\":0}").getAsJsonObject();
        if(kind.equals("effects"))draft=EditorSchema.entry("effects").getAsJsonObject();
        if(kind.equals("spells")){
            var spells=io.redspace.ironsspellbooks.api.registry.SpellRegistry.REGISTRY.keySet().stream().map(Object::toString).filter(value->!ClientState.definitions().spells().containsKey(value)).sorted().toList();
            minecraft.setScreen(new EditorChoiceScreen(this,"Existing Iron spell",spells,value->{
                var spell=EditorSchema.fields("spells","",new JsonObject());spell.addProperty("spell",value);spell.addProperty("tree",ClientState.definitions().trees().keySet().stream().sorted().findFirst().orElse(""));
                pending=new DefinitionEditorScreen(parent,"spells",value,spell,ClientState.definitionRevision(),true);
            }));return;
        }
        new DefinitionEditorScreen(parent,kind,id,draft,ClientState.definitionRevision(),true).openForm();
    }
    private DefinitionEditorScreen pending;
    @Override public void tick(){if(pending!=null){var next=pending;pending=null;next.openForm();}if(!ClientState.editMode())minecraft.setScreen(parent);}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float tick){}
    @Override public void render(GuiGraphics g,int x,int y,float tick){g.fill(0,0,width,height,0xFF20252D);g.drawString(font,title,16,14,0xFFE2B3);super.render(g,x,y,tick);}
}
