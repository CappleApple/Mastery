package com.cappleapple.mastery.client.gui.editor;

import com.cappleapple.mastery.client.gui.IconResources;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.function.Consumer;

/** Searchable previews from all loaded resource namespaces, including resource packs. */
public final class IconPickerScreen extends Screen {
    private record Entry(String name,String value) {}
    private final Screen parent;
    private final Consumer<String> choose;
    private String mode="Textures",query="";
    private int page;
    private EditBox search;
    private List<Entry> entries=List.of(),visible=List.of();
    public IconPickerScreen(Screen parent,Consumer<String> choose){super(Component.literal("Choose skill icon"));this.parent=parent;this.choose=choose;}
    @Override protected void init() {
        if(entries.isEmpty())load();
        addRenderableWidget(Button.builder(Component.literal(mode),b->{mode=switch(mode){case "Textures"->"Items";case "Items"->"Spells";default->"Textures";};page=0;load();rebuildWidgets();}).bounds(16,35,100,20).build());
        search=addRenderableWidget(new EditBox(font,122,35,width-138,20,Component.literal("Find icon")));search.setMaxLength(1024);search.setHint(Component.literal("Search namespace or resource path..."));search.setValue(query);search.setResponder(value->{query=value;page=0;rebuildRows();});rebuildRows();setInitialFocus(search);
    }
    private void load() {
        List<Entry> result=new ArrayList<>();
        switch(mode) {
            case "Items" -> BuiltInRegistries.ITEM.keySet().forEach(id->result.add(new Entry(id.toString(),id.toString())));
            case "Spells" -> SpellRegistry.REGISTRY.keySet().forEach(id->{var spell=SpellRegistry.getSpell(id.toString());result.add(new Entry(id.toString(),spell.getSpellIconResource().toString()));});
            default -> minecraft.getResourceManager().listResources("textures",id->id.getPath().endsWith(".png")).keySet().forEach(id->result.add(new Entry(id.toString(),id.toString())));
        }
        entries=result.stream().sorted(Comparator.comparing(Entry::name)).toList();
    }
    private void rebuildRows() {
        var keep=children().stream().filter(widget->widget==search||widget instanceof Button button&&button.getY()==35).toList();clearWidgets();
        for(var child:keep)if(child instanceof AbstractWidget widget)addRenderableWidget(widget);
        var filtered=entries.stream().filter(entry->entry.name().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).toList();
        int rows=Math.max(1,(height-132)/29),pages=Math.max(1,(filtered.size()+rows-1)/rows);page=Math.min(page,pages-1);
        visible=filtered.subList(Math.min(page*rows,filtered.size()),Math.min((page+1)*rows,filtered.size()));
        for(int i=0;i<visible.size();i++) {
            Entry entry=visible.get(i);int y=65+i*29;
            var button=addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(entry.name(),width-80)),b->{choose.accept(entry.value());minecraft.setScreen(parent);}).bounds(43,y,width-59,24).build());
            button.setTooltip(Tooltip.create(Component.literal(entry.name())));
        }
        addRenderableWidget(Button.builder(Component.literal("Previous"),b->{page--;rebuildRows();}).bounds(16,height-32,82,20).build()).active=page>0;
        addRenderableWidget(Button.builder(Component.literal("Next"),b->{page++;rebuildRows();}).bounds(104,height-32,66,20).build()).active=page+1<pages;
        addRenderableWidget(Button.builder(Component.literal("Back"),b->onClose()).bounds(width-100,height-32,84,20).build());
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float tick){}
    @Override public void render(GuiGraphics g,int x,int y,float tick) {
        g.fill(0,0,width,height,0xFF20252D);g.drawString(font,title,16,14,0xFFE2B3);
        if(visible.isEmpty())g.drawString(font,"No matching "+mode.toLowerCase(Locale.ROOT)+". Clear or shorten the search.",16,73,0xBBBBBB);
        for(int i=0;i<visible.size();i++) {
            String value=visible.get(i).value();ResourceLocation id=ResourceLocation.parse(value);int rowY=69+i*29;
            if(BuiltInRegistries.ITEM.containsKey(id))g.renderItem(new ItemStack(BuiltInRegistries.ITEM.get(id)),19,rowY);
            else {ResourceLocation texture=IconResources.texture(value);if(texture!=null)g.blit(texture,19,rowY,0,0,16,16,16,16);}
        }
        g.drawString(font,font.plainSubstrByWidth(mode+" from loaded mods and resource packs. Select an icon, then Apply.",width-32),16,height-52,0xAABBCB);
        super.render(g,x,y,tick);
    }
}
