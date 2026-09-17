package com.cappleapple.mastery.client.gui.editor;

import com.cappleapple.mastery.client.ClientState;
import com.cappleapple.mastery.client.gui.MasteryScreen;
import com.cappleapple.mastery.network.MasteryNetwork;
import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Exact datapack JSON, with server errors kept alongside the unsaved draft. */
public final class DefinitionEditorScreen extends Screen {
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final MasteryScreen parent;
    private final String kind, initialId, originalText;
    private final long revision;
    private final boolean creating;
    private EditBox id;
    private MultiLineEditBox definition;
    private Button save, requireBook, copyBook;
    private String bookToken = "";
    private TreeThemeEditor themeEditor;
    private UnlockPresentationEditor unlockEditor;
    private String draft, draftId, message = "Save applies the definition to this world's datapack for every player.";
    private boolean busy, error;
    private JsonObject formDraft;

    public DefinitionEditorScreen(MasteryScreen parent, String kind, String id, JsonObject definition, long revision, boolean creating) {
        super(Component.literal((creating ? "Add " : "Edit ") + (kind.equals("trees") ? "tree" : kind.equals("spells")?"spell upgrade":kind.equals("settings")?"global defaults":"node") + " definition"));
        this.parent = parent; this.kind = kind; this.initialId = id; this.draftId = id;
        this.draft = this.originalText = PRETTY.toJson(definition); this.revision = revision; this.creating = creating;
    }
    public String kind(){return kind;}
    public String formId(){return draftId;}
    public void formId(String value){draftId=value;}
    public boolean creating(){return creating;}
    public boolean busy(){return busy;}
    public boolean failed(){return error;}
    public String message(){return message;}
    public void returnToMap(){net.minecraft.client.Minecraft.getInstance().setScreen(parent);}
    public void openForm(){
        if(definition!=null){draft=definition.getValue();draftId=id.getValue();}
        try{formDraft=JsonParser.parseString(draft).getAsJsonObject();net.minecraft.client.Minecraft.getInstance().setScreen(ScriptSchema.scripted(kind)?new VisualScriptScreen(this,formDraft):new VisualDefinitionScreen(this,parent,formDraft,""));}
        catch(RuntimeException e){error=true;message="Fix the JSON syntax before switching to forms.";}
    }
    public void openJson(){draft=PRETTY.toJson(formDraft);definition=null;net.minecraft.client.Minecraft.getInstance().setScreen(this);}
    public void saveForm(){
        if(busy||!ClientState.editMode())return;
        try{validateNewId(draftId.strip());String payload=EditorDrafts.envelope(kind,draftId.strip(),formDraft.toString(),revision);MasteryNetwork.sendAction("editor_save",draftId.strip(),payload,0);busy=true;error=false;message="Validating and saving...";}
        catch(RuntimeException e){error=true;message=e.getMessage();}
    }
    private void validateNewId(String resourceId){
        if(creating&&ClientState.definitions().toJson().getAsJsonObject(kind.equals("synergies")?"nodes":kind).has(resourceId))
            throw new IllegalArgumentException("That ID already exists. Choose a new ID or edit the existing definition.");
    }
    public void closeForm(){
        if(busy)return;
        var mc=net.minecraft.client.Minecraft.getInstance();var current=mc.screen;
        if(!originalText.equals(PRETTY.toJson(formDraft))||!initialId.equals(draftId))mc.setScreen(new ConfirmScreen(discard->mc.setScreen(discard?parent:current),Component.literal("Discard unsaved changes?"),Component.literal("This definition has not been saved.")));
        else mc.setScreen(parent);
    }
    @Override protected void init() {
        if (definition != null) { draft = definition.getValue(); draftId = id.getValue(); }
        int contentWidth = Math.min(760, width - 32), left = (width - contentWidth) / 2;
        id = addRenderableWidget(new EditBox(font, left + 68, 34, contentWidth - 68, 20, Component.literal("Resource ID")));
        id.setMaxLength(256); id.setValue(draftId); id.setEditable(creating);
        int editorTop = kind.equals("trees")?158:kind.equals("nodes")?110:82;
        if (kind.equals("nodes")) {
            int toggleWidth = Math.min(172, contentWidth / 2 - 4);
            requireBook = addRenderableWidget(Button.builder(Component.literal("Require Skill Book: Off"), b -> toggleBook()).bounds(left, 70, toggleWidth, 20).build());
            copyBook = addRenderableWidget(Button.builder(Component.literal("Copy book command"), b -> copyBookCommand()).bounds(left + toggleWidth + 8, 70, Math.min(148, contentWidth - toggleWidth - 8), 20).build());
            requireBook.setTooltip(Tooltip.create(Component.literal("Require a consumed generic Skill Book before this node and its descendants become available. A new gate uses this node's resource ID.")));
        }
        definition = addRenderableWidget(new JsonCodeEditor(font,left,editorTop,contentWidth,Math.max(50,height-editorTop-96)));
        // Oversized definitions remain visible; Save reports the compact payload limit without truncating input.
        definition.setValue(draft);
        if (kind.equals("nodes")) { definition.setValueListener(this::syncBookControls); syncBookControls(draft); }
        if(kind.equals("trees")) {
            themeEditor=new TreeThemeEditor(font,left,contentWidth,()->definition.getValue(),
                    json->definition.setValue(PRETTY.toJson(json)),text->{error=true;message=text;});
            themeEditor.widgets().forEach(this::addRenderableWidget);
            unlockEditor=new UnlockPresentationEditor(font,left,contentWidth,()->definition.getValue(),
                    json->definition.setValue(PRETTY.toJson(json)),text->{error=true;message=text;});
            unlockEditor.widgets().forEach(this::addRenderableWidget);
            definition.setValueListener(text->{themeEditor.sync(text);unlockEditor.sync(text);});
            themeEditor.sync(draft);unlockEditor.sync(draft);
        }
        save = addRenderableWidget(Button.builder(Component.literal("Save to world"), b -> save()).bounds(width / 2 - 154, height - 28, 150, 20).build());
        save.active = !busy;
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(width / 2 + 4, height - 28, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Visual editor"),b->openForm()).bounds(width-128,8,112,20).build());
        addRenderableWidget(Button.builder(Component.literal("Format JSON"),b->{try{definition.setValue(PRETTY.toJson(JsonParser.parseString(definition.getValue())));error=false;message="Formatted JSON.";}catch(RuntimeException e){error=true;message=e.getMessage();}}).bounds(width-230,8,98,20).build());
        setInitialFocus(creating ? id : definition);
    }
    private void syncBookControls(String text) {
        try {
            bookToken = BookGateEditor.token(JsonParser.parseString(text).getAsJsonObject());
            boolean enabled = !bookToken.isBlank();
            requireBook.active = true;
            requireBook.setMessage(Component.literal("Require Skill Book: " + (enabled ? "On" : "Off")));
            copyBook.active = enabled && bookToken.matches("[a-z0-9_.-]+:[a-z0-9/._-]+");
        } catch (RuntimeException exception) {
            bookToken = ""; requireBook.active = false; copyBook.active = false;
            requireBook.setMessage(Component.literal("Require Skill Book: JSON?"));
        }
    }
    private void toggleBook() {
        try {
            JsonObject current = JsonParser.parseString(definition.getValue()).getAsJsonObject();
            JsonObject changed = BookGateEditor.toggle(current, id.getValue().strip(), BookGateEditor.token(current).isBlank());
            definition.setValue(PRETTY.toJson(changed));
            error = false;
            message = BookGateEditor.token(changed).isBlank() ? "Book requirement removed. Other prerequisites still apply."
                    : "This branch uses a generic Skill Book. Copy its give command or edit book_token in the JSON.";
        } catch (RuntimeException exception) { error = true; message = exception.getMessage(); }
    }
    private void copyBookCommand() {
        try {
            minecraft.keyboardHandler.setClipboard(BookGateEditor.giveCommand(BookGateEditor.token(JsonParser.parseString(definition.getValue()).getAsJsonObject())));
            error = false; message = "Copied the give command for this branch's generic Skill Book.";
        } catch (RuntimeException exception) { error = true; message = exception.getMessage(); }
    }
    private void save() {
        if (busy || !ClientState.editMode()) return;
        try {
            if(themeEditor!=null) themeEditor.validate();
            if(unlockEditor!=null) unlockEditor.validate();
            if(themeEditor!=null) themeEditor.commit();
            if(unlockEditor!=null) unlockEditor.commit();
            String resourceId = id.getValue().strip();
            validateNewId(resourceId);
            String payload = EditorDrafts.envelope(kind, resourceId, definition.getValue(), revision);
            MasteryNetwork.sendAction("editor_save", resourceId, payload, 0);
            busy = true; error = false; save.active = false; message = "Validating and saving on the server...";
        } catch (RuntimeException exception) {
            error = true; message = exception.getMessage() == null ? "Invalid definition JSON." : exception.getMessage();
        }
    }
    public void serverStatus(boolean success, String status) {
        busy = false; if(save!=null)save.active = true; error = !success; message = status;
        if (success) returnToMap();
    }
    @Override public void tick() {
        if (!ClientState.editMode()) minecraft.setScreen(parent);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        return busy || super.mouseClicked(x, y, button);
    }
    @Override public boolean charTyped(char character, int modifiers) {
        return busy || super.charTyped(character, modifiers);
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (busy) return true;
        if (hasControlDown() && key == GLFW.GLFW_KEY_S) { save(); return true; }
        return super.keyPressed(key, scanCode, modifiers);
    }
    @Override public void onClose() {
        if (busy) { message = "Waiting for the server's save result..."; return; }
        if (!originalText.equals(definition.getValue()) || !initialId.equals(id.getValue())) {
            minecraft.setScreen(new ConfirmScreen(discard -> minecraft.setScreen(discard ? parent : this),
                    Component.literal("Discard unsaved changes?"), Component.literal("Your definition has not been saved.")));
        } else minecraft.setScreen(parent);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF252522);
        int contentWidth = Math.min(760, width - 32), left = (width - contentWidth) / 2;
        graphics.drawString(font, title, left, 13, 0xFFE2B3);
        graphics.drawString(font, "Resource ID", left, 40, 0xDDDDDD);
        String hint = kind.equals("settings")?"Defaults: appearance, theme, unlock, charge, modifier_slots. Tree and node overrides take priority." : kind.equals("spells") ? "Native level: level | Charge ranks and behavior: charge | Durations use ticks (20 per second)." : kind.equals("trees") ? "Tree growth: section accepts compass directions, including northeast. Points: point_every."
                : "Prerequisites: dependencies. Hidden branches: book_token. Native spell: spell.";
        if(themeEditor!=null) {themeEditor.preview(graphics,font,left,contentWidth);unlockEditor.labels(graphics,font,left,contentWidth);}
        else graphics.drawString(font, font.plainSubstrByWidth(hint, contentWidth), left, 59, 0xAAAAAA);
        if (kind.equals("nodes")) graphics.drawString(font, font.plainSubstrByWidth(bookToken.isBlank() ? "Generic Skill Book item | Edit book_token in JSON to share an existing token." : "Book unlock token: " + bookToken, contentWidth), left, 97, 0xAAAAAA);
        var lines = font.split(Component.literal(message), contentWidth);
        for (int line = 0; line < Math.min(4, lines.size()); line++) graphics.drawString(font, lines.get(line), left, height - 84 + line * 11, error ? 0xFF9999 : 0xCFCFCF);
        graphics.drawString(font, "Ctrl+S to save | " + definition.getValue().length() + " characters", left, height - 39, 0x999999);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
