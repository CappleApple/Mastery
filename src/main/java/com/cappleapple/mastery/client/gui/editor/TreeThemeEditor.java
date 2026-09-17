package com.cappleapple.mastery.client.gui.editor;

import com.cappleapple.mastery.data.*;
import com.cappleapple.mastery.layout.ThemePalette;
import com.google.gson.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.function.*;

/** Theme controls edit the same JSON as the definition form. */
public final class TreeThemeEditor {
    private final EditBox inner, outer;
    private final GradientSlider gradient;
    private final Button useDefault;
    private boolean inheritedGradient=true;
    private final Supplier<String> source;
    private final Consumer<JsonObject> changed;
    private final Consumer<String> error;
    private boolean syncing;
    public TreeThemeEditor(Font font,int left,int width,Supplier<String> source,Consumer<JsonObject> changed,Consumer<String> error) {
        this.source=source;this.changed=changed;this.error=error;
        inner=new EditBox(font,left,70,80,20,Component.literal("Inner color"));
        outer=new EditBox(font,left+88,70,80,20,Component.literal("Outer color"));
        inner.setMaxLength(7);outer.setMaxLength(7);
        inner.setTooltip(Tooltip.create(Component.literal("Inner outline and connector center color, #RRGGBB.")));
        outer.setTooltip(Tooltip.create(Component.literal("Outer outline and connector edge color, #RRGGBB.")));
        gradient=new GradientSlider(left+176,70,Math.max(80,width-246));
        useDefault=Button.builder(Component.literal("Default"),button->{syncing=true;inner.setValue("default");outer.setValue("default");inheritedGradient=true;gradient.setAmount(1);syncing=false;apply();}).bounds(left+width-64,70,64,20).build();
        inner.setResponder(ignored->apply());outer.setResponder(ignored->apply());
    }
    public List<AbstractWidget> widgets() { return List.of(inner,outer,gradient,useDefault); }
    public void sync(String text) {
        if(syncing)return;
        syncing=true;
        try {
            var json=JsonParser.parseString(text).getAsJsonObject();
            var raw=json.has("theme")&&json.get("theme").isJsonObject()?json.getAsJsonObject("theme"):new JsonObject();
            inner.setValue(raw.has("inner_color")?raw.get("inner_color").getAsString():"default");
            outer.setValue(raw.has("outer_color")?raw.get("outer_color").getAsString():"default");
            inheritedGradient=!raw.has("gradient")||raw.get("gradient").isJsonPrimitive()&&raw.get("gradient").getAsString().equals("default");
            gradient.setAmount(inheritedGradient?1:raw.get("gradient").getAsDouble());
            widgets().forEach(widget->widget.active=true);
        } catch(RuntimeException invalid) { widgets().forEach(widget->widget.active=false); }
        finally {syncing=false;}
    }
    private JsonObject value(){JsonObject j=new JsonObject();j.addProperty("inner_color",inner.getValue());j.addProperty("outer_color",outer.getValue());if(inheritedGradient)j.addProperty("gradient","default");else j.addProperty("gradient",gradient.amount());return j;}
    private TreeTheme previewTheme(){return TreeTheme.parse(SettingsResolver.merge(TreeTheme.DEFAULT.toJson(),value()));}
    public void validate() {previewTheme();}
    public void commit() {
        validate();var json=JsonParser.parseString(source.get()).getAsJsonObject();
        json.add("theme",value());
        syncing=true;
        try {changed.accept(json);} finally {syncing=false;}
    }
    private void apply() {
        if(syncing)return;
        try {commit();} catch(RuntimeException invalid) {error.accept(invalid.getMessage());}
    }
    public void preview(GuiGraphics graphics,Font font,int left,int width) {
        graphics.drawString(font,"Inner",left,59,0xAAAAAA);graphics.drawString(font,"Outer",left+88,59,0xAAAAAA);
        try {
            var theme=previewTheme();
            for(int x=0;x<width;x++) graphics.fill(left+x,97,left+x+1,106,ThemePalette.color(theme,x/(double)Math.max(1,width-1),ThemePalette.State.ENABLED,1));
        } catch(RuntimeException ignored) {}
    }
    private final class GradientSlider extends AbstractSliderButton {
        GradientSlider(int x,int y,int width) {super(x,y,width,20,Component.empty(),1);updateMessage();}
        double amount(){return value;}
        void setAmount(double amount){value=amount;updateMessage();}
        @Override protected void updateMessage(){setMessage(Component.literal(inheritedGradient?"Gradient: default":"Gradient: "+Math.round(value*100)+"%"));}
        @Override protected void applyValue(){inheritedGradient=false;updateMessage();apply();}
    }
}
