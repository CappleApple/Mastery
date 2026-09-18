package com.cappleapple.mastery.mixin.client;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.inventory.tooltip.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import java.util.List;
@Mixin(GuiGraphics.class)
public interface KeywordTooltipAccessor {
    @Invoker("renderTooltipInternal") void mastery$renderTooltip(Font font,List<ClientTooltipComponent> components,int x,int y,ClientTooltipPositioner positioner);
}
