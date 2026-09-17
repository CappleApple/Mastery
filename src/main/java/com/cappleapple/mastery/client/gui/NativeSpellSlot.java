package com.cappleapple.mastery.client.gui;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Uses Iron's own slot atlas and native spell icons; no copied textures. */
public final class NativeSpellSlot extends AbstractButton {
    private static final ResourceLocation ATLAS=ResourceLocation.fromNamespaceAndPath("irons_spellbooks","textures/gui/icons.png");
    private final String spell;private final Runnable action;
    public NativeSpellSlot(int x,int y,String spell,Component description,Runnable action) {
        super(x,y,22,22,description);this.spell=spell;this.action=action;setTooltip(Tooltip.create(description));
    }
    public static void draw(GuiGraphics graphics,int x,int y,String spell,boolean selected) {
        graphics.blit(ATLAS,x,y,66,84,22,22);
        graphics.blit(ATLAS,x,y,selected?0:22,84,22,22);
        if(!spell.isBlank())graphics.blit(SpellRegistry.getSpell(spell).getSpellIconResource(),x+3,y+3,0,0,16,16,16,16);
    }
    @Override protected void renderWidget(GuiGraphics graphics,int mouseX,int mouseY,float delta){draw(graphics,getX(),getY(),spell,isHoveredOrFocused()&&active);if(!active)graphics.fill(getX(),getY(),getX()+22,getY()+22,0x99000000);}
    @Override protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output){defaultButtonNarrationText(output);}
    @Override public void onPress(){action.run();}
}
