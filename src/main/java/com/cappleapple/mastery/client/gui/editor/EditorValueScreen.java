package com.cappleapple.mastery.client.gui.editor;
import com.cappleapple.mastery.client.ClientState;
import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;

/** Typed scalar editing with selectors, boolean toggles, numeric steps, and color previews. */
public final class EditorValueScreen extends Screen {
    private final Screen parent;private final String kind,path,key;private final Consumer<JsonElement> accept;
    private JsonElement value;private String text,error="",type;private MultiLineEditBox input;
    public EditorValueScreen(Screen parent,String kind,String path,String key,JsonElement value,Consumer<JsonElement> accept){
        super(Component.literal(EditorSchema.label(key)));this.parent=parent;this.kind=kind;this.path=path;this.key=key;this.value=value.deepCopy();this.accept=accept;
        type=value.isJsonObject()?"Object":value.isJsonArray()?"List":value.isJsonNull()?"Null":value.getAsJsonPrimitive().isBoolean()?"Boolean":value.getAsJsonPrimitive().isNumber()?"Number":"Text";
        text=value.isJsonPrimitive()?value.getAsString():"";
    }
    @Override protected void init(){
        input=addRenderableWidget(new MultiLineEditBox(font,20,92,width-40,Math.max(45,height-205),Component.literal("Value"),title));input.setValue(text);input.setValueListener(v->text=v);
        input.visible=type.equals("Text")||type.equals("Number");
        addRenderableWidget(Button.builder(Component.literal("Value type: "+type),b->{var types=List.of("Text","Number","Boolean","Object","List","Null");type=types.get((types.indexOf(type)+1)%types.size());clearWidgets();init();}).bounds(20,62,160,20).build());
        var choices=options();
        addRenderableWidget(Button.builder(Component.literal("Choose..."),b->{if(key.equals("icon"))minecraft.setScreen(new IconPickerScreen(this,v->{text=v;type="Text";}));else minecraft.setScreen(new EditorChoiceScreen(this,"Choose "+EditorSchema.label(key),choices,v->{text=v;type="Text";}));}).bounds(188,62,100,20).build()).active=!choices.isEmpty();
        if(type.equals("Boolean"))addRenderableWidget(Button.builder(Component.literal(Boolean.parseBoolean(text)?"Enabled":"Disabled"),b->{text=Boolean.toString(!Boolean.parseBoolean(text));b.setMessage(Component.literal(Boolean.parseBoolean(text)?"Enabled":"Disabled"));}).bounds(20,100,160,20).build());
        if(type.equals("Number"))for(int delta:new int[]{-1,1})addRenderableWidget(Button.builder(Component.literal(delta<0?"-":"+"),b->{try{input.setValue(new BigDecimal(text).add(BigDecimal.valueOf(delta)).toPlainString());}catch(Exception ignored){error="Enter a number first.";}}).bounds(width-100+(delta>0?40:0),height-94,36,20).build());
        addRenderableWidget(Button.builder(Component.literal("Use default"),b->{accept.accept(new JsonPrimitive("default"));minecraft.setScreen(parent);}).bounds(20,height-94,140,20).build()).active=path.endsWith("effect_context")||path.contains("appearance")||path.contains("theme")||path.contains("unlock")||path.contains("charge")||path.contains("modifier_slots");
        addRenderableWidget(Button.builder(Component.literal("Apply"),b->apply()).bounds(width/2-154,height-32,150,20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"),b->onClose()).bounds(width/2+4,height-32,150,20).build());
        if(input.visible)setInitialFocus(input);
    }
    private List<String> options(){
        var result=new TreeSet<>(EditorSchema.choices(kind,path,key));
        if(kind.equals("classes")&&path.contains("starting_inventory")&&key.equals("id"))BuiltInRegistries.ITEM.keySet().forEach(id->result.add(id.toString()));
        switch(key){
            case "categories" -> result.addAll(com.cappleapple.mastery.mechanics.DamageContext.CATEGORIES);
            case "damage_tags" -> {if(minecraft.level!=null)minecraft.level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).getTagNames().forEach(id->result.add(id.location().toString()));}
            case "tree" -> result.addAll(ClientState.definitions().trees().keySet());
            case "node","exclusions" -> result.addAll(ClientState.definitions().nodes().keySet());
            case "spell" -> {result.addAll(ClientState.definitions().spells().keySet());io.redspace.ironsspellbooks.api.registry.SpellRegistry.REGISTRY.keySet().forEach(id->result.add(id.toString()));}
            case "context","contexts" -> result.addAll(ClientState.definitions().contexts().keySet());
            case "ref" -> result.addAll(path.contains("effects")||kind.equals("effects")?ClientState.definitions().effects().keySet():ClientState.definitions().requirements().keySet());
            case "icon","item","items","offhand" -> BuiltInRegistries.ITEM.keySet().forEach(id->result.add(id.toString()));
            case "element","elements" -> {var map=ClientState.definitions().toJson().getAsJsonObject("elements");if(map!=null)result.addAll(map.keySet());io.redspace.ironsspellbooks.api.registry.SchoolRegistry.REGISTRY.keySet().forEach(id->result.add(id.toString()));}
            case "entities","entity" -> BuiltInRegistries.ENTITY_TYPE.keySet().forEach(id->result.add(id.toString()));
            case "damage_type","damage_types" -> {if(minecraft.level!=null)minecraft.level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE).keySet().forEach(id->result.add(id.toString()));}
            case "trigger","keyword" -> {var map=ClientState.definitions().toJson().getAsJsonObject(key.equals("trigger")?"triggers":"keywords");if(map!=null)result.addAll(map.keySet());}
            case "school" -> io.redspace.ironsspellbooks.api.registry.SchoolRegistry.REGISTRY.keySet().forEach(id->result.add(id.toString()));
            case "particle" -> BuiltInRegistries.PARTICLE_TYPE.entrySet().stream().filter(e->e.getValue() instanceof net.minecraft.core.particles.SimpleParticleType).forEach(e->result.add(e.getKey().location().toString()));
            case "block" -> BuiltInRegistries.BLOCK.keySet().forEach(id->result.add(id.toString()));
            case "enchantment" -> {if(minecraft.level!=null)minecraft.level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).keySet().forEach(id->result.add(id.toString()));}
            case "attribute","xp_attribute","weapon_attribute","power_attribute","conversion_attribute","attunement_attribute","potency_attribute","mitigation_attribute" -> BuiltInRegistries.ATTRIBUTE.keySet().forEach(id->result.add(id.toString()));
            case "progress_sound","complete_sound" -> {result.add("default");result.add("none");BuiltInRegistries.SOUND_EVENT.keySet().forEach(id->result.add(id.toString()));}
            case "effect","added_effect" -> BuiltInRegistries.MOB_EFFECT.keySet().forEach(id->result.add(id.toString()));
        }
        return List.copyOf(result);
    }
    private void apply(){try{
        JsonElement next=switch(type){case "Number"->new JsonPrimitive(new BigDecimal(text.strip()));case "Boolean"->new JsonPrimitive(Boolean.parseBoolean(text));case "Object"->value.isJsonObject()?value:new JsonObject();case "List"->value.isJsonArray()?value:new JsonArray();case "Null"->JsonNull.INSTANCE;default->new JsonPrimitive(text);};
        if(key.endsWith("_color")&&!text.equals("default")&&!text.matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException("Use a six-digit color, such as #55CCFF.");
        accept.accept(next);minecraft.setScreen(parent);
    }catch(RuntimeException e){error=e.getMessage()==null?"Invalid value":e.getMessage();}}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float tick){}
    @Override public void render(GuiGraphics g,int x,int y,float tick){
        g.fill(0,0,width,height,0xFF20252D);g.drawString(font,title,20,14,0xFFE2B3);g.drawWordWrap(font,Component.literal(EditorSchema.hint(key)),20,34,width-40,0xBBBBBB);
        if(key.endsWith("_color"))try{g.fill(width-82,64,width-24,82,0xFF000000|Integer.parseInt(text.substring(1),16));}catch(Exception ignored){}
        if(type.equals("Object")||type.equals("List"))g.drawWordWrap(font,Component.literal("Apply to create this group, then open it to edit its contents."),20,100,width-40,0xBBBBBB);
        g.drawWordWrap(font,Component.literal(error),20,height-67,width-40,0xFF9999);super.render(g,x,y,tick);
    }
}
