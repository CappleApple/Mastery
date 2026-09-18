package com.cappleapple.mastery.client.gui;

import com.cappleapple.mastery.client.ClientState;
import com.cappleapple.mastery.network.MasteryNetwork;
import com.cappleapple.mastery.spells.*;
import com.cappleapple.mastery.progression.ProgressionService;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Capacity-aware layout selection and native spell slot grid. Server validates every mutation. */
public final class SpellBindingsScreen extends Screen {
    private final Screen parent;
    private String suggested,context;
    private int hotbar,slotPage,pickerPage,selectedSlot=-1;
    private long revision=-1;
    private List<Integer> visible=List.of();
    public SpellBindingsScreen(Screen parent,String suggested){super(Component.literal("Spell bindings"));this.parent=parent;this.suggested=suggested;}
    @Override protected void init(){if(context==null)hotbar=minecraft.player==null?0:minecraft.player.getInventory().selected;rebuild();}
    private void rebuild() {
        clearWidgets();revision=ClientState.revision();
        String mode=ClientState.progress().bindingMode();context=mode.equals("quick_cast")?BindingSlots.QUICK:BindingSlots.hotbar(hotbar);
        addRenderableWidget(Button.builder(Component.literal(mode.equals("hotbar")?"Mode: Hotbar sets":"Mode: Quick cast"),b->{
            selectedSlot=-1;slotPage=pickerPage=0;MasteryNetwork.sendAction("binding_mode",mode.equals("hotbar")?"quick_cast":"hotbar","",0);
        }).bounds(12,32,160,20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"),b->onClose()).bounds(width-72,12,60,20).build());
        if(mode.equals("hotbar"))for(int i=0;i<9;i++) {
            int index=i;var button=addRenderableWidget(Button.builder(Component.literal(Integer.toString(i+1)),b->{hotbar=index;slotPage=0;selectedSlot=-1;rebuild();})
                    .bounds(width/2-130+i*29,61,26,20).build());button.active=i!=hotbar;
        }
        visible=BindingSlots.visible(ClientState.progress().loadouts(),mode,context,ClientState.capacity());
        boolean hotbarMode=mode.equals("hotbar");
        int pages=hotbarMode?1:Math.max(1,(visible.size()+15)/16);
        slotPage=Math.clamp(slotPage,0,pages-1);
        if(selectedSlot>=0&&!visible.contains(selectedSlot))selectedSlot=-1;
        int origin=width/2-124;
        for(int cell=0;cell<(hotbarMode?4:Math.min(16,visible.size()-slotPage*16));cell++) {
            int slot=hotbarMode?cell:visible.get(slotPage*16+cell);
            String spell=BindingSlots.get(ClientState.progress().loadouts(),context,slot);
            boolean available=visible.contains(slot);
            var widget=new NativeSpellSlot(hotbarMode?width/2-86+cell*50:origin+(cell%8)*32,101+(cell/8)*35,spell,
                    Component.literal(available?"Slot "+(slot+1)+": "+(spell.isBlank()?"Empty":ClientState.name(spell)):"No available spell capacity"),()->{
                if(!suggested.isBlank()) {MasteryNetwork.sendAction("equip",suggested,context,slot);suggested="";}
                else {selectedSlot=slot;pickerPage=0;rebuild();}
            });
            widget.active=available;addRenderableWidget(widget);
        }
        if(!hotbarMode&&pages>1) {
            addRenderableWidget(Button.builder(Component.literal("<"),b->{slotPage--;selectedSlot=-1;rebuild();}).bounds(origin-28,117,22,20).build()).active=slotPage>0;
            addRenderableWidget(Button.builder(Component.literal(">"),b->{slotPage++;selectedSlot=-1;rebuild();}).bounds(origin+256,117,22,20).build()).active=slotPage+1<pages;
        }
        if(selectedSlot>=0) {
            var spells=ClientState.definitions().spells().keySet().stream().filter(id->ProgressionService.ownsSpell(ClientState.definitions(),ClientState.progress(),id))
                    .filter(id->!ClientState.progress().loadouts().getOrDefault(context,List.of()).contains(id)||id.equals(BindingSlots.get(ClientState.progress().loadouts(),context,selectedSlot)))
                    .sorted().toList();
            pickerPage=Math.clamp(pickerPage,0,Math.max(0,(spells.size()-1)/24));
            for(int index=pickerPage*24;index<Math.min(spells.size(),pickerPage*24+24);index++) {
                String spell=spells.get(index);int cell=index%24;
                addRenderableWidget(new NativeSpellSlot(origin+(cell%8)*32,208+(cell/8)*26,spell,Component.literal(ClientState.name(spell)),()->{
                    MasteryNetwork.sendAction("equip",spell,context,selectedSlot);selectedSlot=-1;
                }));
            }
            addRenderableWidget(Button.builder(Component.literal("Clear slot"),b->{MasteryNetwork.sendAction("equip","",context,selectedSlot);selectedSlot=-1;})
                    .bounds(12,height-28,88,20).build());
            if(spells.size()>24) {
                addRenderableWidget(Button.builder(Component.literal("<"),b->{pickerPage--;rebuild();}).bounds(origin-28,235,22,20).build()).active=pickerPage>0;
                addRenderableWidget(Button.builder(Component.literal(">"),b->{pickerPage++;rebuild();}).bounds(origin+256,235,22,20).build()).active=(pickerPage+1)*24<spells.size();
            }
        }
    }
    @Override public void tick(){if(ClientState.revision()!=revision)rebuild();}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public void renderBackground(GuiGraphics graphics,int mx,int my,float delta){}
    @Override public void render(GuiGraphics graphics,int mx,int my,float delta) {
        graphics.fill(0,0,width,height,0xFF252522);graphics.drawString(font,title,12,13,0xFFE2B3);
        String mode=ClientState.progress().bindingMode();
        int used=LoadoutRules.equipped(BindingSlots.modeLoadouts(ClientState.progress().loadouts(),mode));
        graphics.drawString(font,"Bound: "+used+" / "+ClientState.capacity(),182,38,0xDDDDDD);
        String heading=mode.equals("hotbar")?"Hotbar position "+(hotbar+1):"Shared quick-cast slots";
        graphics.drawCenteredString(font,heading,width/2,87,0xDDDDDD);
        if(visible.isEmpty())graphics.drawCenteredString(font,ClientState.capacity()==0?"No spell slots available. Equip a spellbook or unlock more slots.":"All spell slots are assigned to other hotbar sets.",width/2,153,0xAAAAAA);
        if(mode.equals("hotbar")) {
            for(int cell=0;cell<4;cell++)graphics.drawCenteredString(font,com.cappleapple.mastery.client.MasteryClient.ACTIVE_SLOTS[cell].getTranslatedKeyMessage(),width/2-75+cell*50,127,visible.contains(cell)?0xDDDDDD:0x666666);
        } else for(int index=slotPage*16;index<Math.min(visible.size(),slotPage*16+16);index++) {
            int cell=index%16;graphics.drawString(font,Integer.toString(visible.get(index)+1),width/2-124+(cell%8)*32,125+(cell/8)*35,0xAAAAAA);
        }
        if(!suggested.isBlank())graphics.drawCenteredString(font,"Choose a slot for "+ClientState.name(suggested),width/2,180,0xFFE2B3);
        else graphics.drawCenteredString(font,selectedSlot>=0?"Choose a spell for slot "+(selectedSlot+1):"Click a slot to assign, replace, or clear its spell.",width/2,180,0xDDDDDD);
        if(mode.equals("quick_cast")&&selectedSlot<0)graphics.drawCenteredString(font,"Four cast keys per page. Use [ and ] to change pages while playing.",width/2,height-43,0x999999);
        super.render(graphics,mx,my,delta);
    }
}
