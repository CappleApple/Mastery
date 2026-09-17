package com.cappleapple.mastery.client.gui.editor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;

/** Searchable picker for enum values, registry IDs, and reusable definitions. */
public final class EditorChoiceScreen extends Screen {
    private final Screen parent;private final List<String> values;private final Consumer<String> choose;
    private String query="";private int page;private EditBox search;
    public EditorChoiceScreen(Screen parent,String title,Collection<String> values,Consumer<String> choose){super(Component.literal(title));this.parent=parent;this.values=List.copyOf(values);this.choose=choose;}
    @Override protected void init(){
        search=addRenderableWidget(new EditBox(font,16,35,width-32,20,Component.literal("Search options")));search.setMaxLength(1024);search.setValue(query);
        search.setResponder(value->{query=value;page=0;rebuildRows();});rebuildRows();setInitialFocus(search);
    }
    private void rebuildRows(){
        var keep=search;clearWidgets();addRenderableWidget(keep);
        var filtered=values.stream().filter(v->v.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).toList();
        int count=Math.max(1,(height-115)/24),pages=Math.max(1,(filtered.size()+count-1)/count);page=Math.min(page,pages-1);
        for(int i=page*count;i<Math.min(filtered.size(),(page+1)*count);i++){
            String value=filtered.get(i);addRenderableWidget(Button.builder(Component.literal(value.isEmpty()?"None":value),b->{choose.accept(value);if(minecraft.screen==this)minecraft.setScreen(parent);}).bounds(16,64+(i%count)*24,width-32,20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Previous"),b->{page=Math.max(0,page-1);rebuildRows();}).bounds(16,height-32,90,20).build()).active=page>0;
        addRenderableWidget(Button.builder(Component.literal("Next"),b->{page++;rebuildRows();}).bounds(110,height-32,90,20).build()).active=page+1<pages;
        addRenderableWidget(Button.builder(Component.literal("Back"),b->onClose()).bounds(width-106,height-32,90,20).build());
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float tick){}
    @Override public void render(GuiGraphics g,int x,int y,float tick){g.fill(0,0,width,height,0xFF20252D);g.drawString(font,title,16,14,0xFFE2B3);super.render(g,x,y,tick);}
}
