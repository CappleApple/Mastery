package com.cappleapple.mastery.client.gui.editor;

import com.cappleapple.mastery.data.UnlockPresentation;
import com.google.gson.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.function.*;

/** Inherited values are displayed as default, never replaced by their resolved values. */
public final class UnlockPresentationEditor {
    private final Button direction;
    private final EditBox progress,complete;
    private final Supplier<String> source;
    private final Consumer<JsonObject> changed;
    private final Consumer<String> error;
    private String fill="default";
    private boolean syncing;
    public UnlockPresentationEditor(Font font,int left,int width,Supplier<String> source,Consumer<JsonObject> changed,Consumer<String> error) {
        this.source=source;this.changed=changed;this.error=error;
        int part=(width-12)/3;
        direction=Button.builder(Component.literal("Fill: default"),button->{
            fill=switch(fill){case "default"->"vertical";case "vertical"->"horizontal";default->"default";};
            button.setMessage(Component.literal("Fill: "+fill));apply();
        }).bounds(left,127,part,20).build();
        progress=new EditBox(font,left+part+6,127,part,20,Component.literal("Progress sound"));
        complete=new EditBox(font,left+2*(part+6),127,part,20,Component.literal("Completion sound"));
        for(var box:List.of(progress,complete)) {
            box.setMaxLength(256);
            box.setTooltip(Tooltip.create(Component.literal("default, none, or a sound event ID. Defaults are in data/mastery/mastery/settings/defaults.json.")));
            box.setResponder(ignored->apply());
        }
    }
    public List<AbstractWidget> widgets(){return List.of(direction,progress,complete);}
    public void sync(String text) {
        if(syncing)return;syncing=true;
        try {
            var json=JsonParser.parseString(text).getAsJsonObject();
            var options=UnlockPresentation.parse(json.has("unlock")&&json.get("unlock").isJsonObject()?json.getAsJsonObject("unlock"):new JsonObject());
            fill=options.fillDirection();direction.setMessage(Component.literal("Fill: "+fill));
            progress.setValue(options.progressSound());complete.setValue(options.completeSound());
            widgets().forEach(widget->widget.active=true);
        } catch(RuntimeException invalid){widgets().forEach(widget->widget.active=false);}
        finally{syncing=false;}
    }
    public void validate() {new UnlockPresentation(fill,progress.getValue(),complete.getValue());}
    public void commit() {
        var json=JsonParser.parseString(source.get()).getAsJsonObject();
        var previous=json.has("unlock")&&json.get("unlock").isJsonObject()?json.getAsJsonObject("unlock").deepCopy():new JsonObject();
        previous.addProperty("fill_direction",fill);previous.addProperty("progress_sound",progress.getValue());previous.addProperty("complete_sound",complete.getValue());
        json.add("unlock",previous);
        syncing=true;try{changed.accept(json);}finally{syncing=false;}
    }
    private void apply(){if(!syncing)try{commit();}catch(RuntimeException invalid){error.accept(invalid.getMessage());}}
    public void labels(GuiGraphics graphics,Font font,int left,int width) {
        int part=(width-12)/3;
        graphics.drawString(font,"Unlock fill",left,116,0xAAAAAA);
        graphics.drawString(font,"Progress sound",left+part+6,116,0xAAAAAA);
        graphics.drawString(font,"Complete sound",left+2*(part+6),116,0xAAAAAA);
    }
}
