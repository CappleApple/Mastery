package com.cappleapple.mastery.client;

import com.cappleapple.mastery.layout.ChargeBar;
import com.google.gson.JsonParser;
import io.redspace.ironsspellbooks.player.ClientMagicData;
import io.redspace.ironsspellbooks.gui.overlays.CastBarOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Presentation for server-announced charge windows; native Iron casting remains authoritative. */
public final class ChargeHud {
    private static String spell="";
    private static ChargeBar window;
    private static java.util.List<Integer> costMarkers=java.util.List.of();
    private ChargeHud(){}
    public static void clear(){spell="";window=null;costMarkers=java.util.List.of();}
    public static void accept(String text){
        var json=JsonParser.parseString(text).getAsJsonObject();clear();
        if(json.has("spell")) {
            window=new ChargeBar(json.get("base").getAsInt(),json.get("ticks_per_level").getAsInt(),json.get("levels").getAsInt(),json.get("total").getAsInt());
            spell=json.get("spell").getAsString();
            if(json.has("charge_cost_markers"))costMarkers=json.getAsJsonArray("charge_cost_markers").asList().stream().map(com.google.gson.JsonElement::getAsInt).toList();
        }
    }
    public static boolean render(GuiGraphics graphics){
        if(window==null||!ClientMagicData.isCasting()||!spell.equals(ClientMagicData.getCastingSpellId()))return false;
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.options.hideGui||mc.player.isSpectator())return true;
        double progress=window.progress(ClientMagicData.getCastDurationRemaining());
        int width=Math.min(graphics.guiWidth()-12,Math.max(108,window.levels()*6+10));
        int x=(graphics.guiWidth()-width)/2,y=graphics.guiHeight()/2+graphics.guiHeight()/8;
        graphics.pose().pushPose();graphics.pose().translate(x,y,0);graphics.pose().scale(width/54f,1,1);
        graphics.blit(CastBarOverlay.TEXTURE,0,0,0,42,54,21,256,256);
        graphics.blit(CastBarOverlay.TEXTURE,0,0,0,63,5+(int)(44*progress),21);
        graphics.pose().popPose();
        for(double marker:window.markers()) {
            int mx=x+Math.round((float)(5+44*marker)*width/54f);
            graphics.fill(mx,y+5,mx+1,y+17,0xCC171717);
        }
        for(int tick:costMarkers) {
            int mx=x+Math.round((float)(5+44*tick/(double)window.total())*width/54f);
            graphics.fill(mx-1,y+3,mx+2,y+19,0xFFFFD54A);
        }
        graphics.drawCenteredString(mc.font,window.percentage(ClientMagicData.getCastDurationRemaining())+"%",graphics.guiWidth()/2,y+7,0xFFFFFF);
        return true;
    }
}
