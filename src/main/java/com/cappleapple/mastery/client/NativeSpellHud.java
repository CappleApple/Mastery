package com.cappleapple.mastery.client;
import io.redspace.ironsspellbooks.api.magic.SpellSelectionManager;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.compat.Curios;
import io.redspace.ironsspellbooks.gui.overlays.SpellBarOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import java.util.*;

/** Read-only selection view used solely by Iron's existing HUD renderer. Equipment is never rewritten. */
public final class NativeSpellHud {
    private static HudSelection manager;
    private static Player owner;
    private static long revision=-1;
    private static String context="";
    private static List<SpellSelectionManager.SelectionOption> spells=List.of();
    public static String lastCast="";
    private NativeSpellHud(){}
    public static SpellSelectionManager view(SpellSelectionManager original) {
        var player=Minecraft.getInstance().player;if(player==null)return original;
        if(owner!=player){owner=player;manager=new HudSelection(player);revision=-1;}
        if(revision!=ClientState.revision()||!context.equals(ClientState.context())) {
            String previous=context;context=ClientState.context();revision=ClientState.revision();
            List<SpellSelectionManager.SelectionOption> result=new ArrayList<>();
            var slots=ClientState.progress().loadouts().getOrDefault(context,List.of());
            for(int index=0;index<Math.min(slots.size(),ClientState.capacity());index++) {
                String id=slots.get(index);var definition=ClientState.definitions().spells().get(id);
                if(definition!=null)result.add(new SpellSelectionManager.SelectionOption(new SpellData(SpellRegistry.getSpell(id),definition.level()),Curios.SPELLBOOK_SLOT,index,result.size()));
            }
            if(!previous.equals(context)||!same(spells,result))SpellBarOverlay.fadeoutDelay=80;
            spells=List.copyOf(result);
        }
        return manager;
    }
    private static boolean same(List<SpellSelectionManager.SelectionOption> a,List<SpellSelectionManager.SelectionOption> b) {
        return a.stream().map(s->s.spellData.getSpell().getSpellId()).toList().equals(b.stream().map(s->s.spellData.getSpell().getSpellId()).toList());
    }
    public static void clear(){manager=null;owner=null;spells=List.of();revision=-1;context="";lastCast="";}
    private static final class HudSelection extends SpellSelectionManager {
        HudSelection(Player player){super(player);}
        @Override public List<SelectionOption> getAllSpells(){return spells;}
        @Override public int getSpellCount(){return spells.size();}
        @Override public List<SelectionOption> getSpellsForSlot(String slot){return slot.equals(Curios.SPELLBOOK_SLOT)?spells:List.of();}
        @Override public int getGlobalSelectionIndex(){
            for(int i=0;i<spells.size();i++)if(spells.get(i).spellData.getSpell().getSpellId().equals(lastCast))return i;
            return spells.isEmpty()?-1:0;
        }
    }
}
