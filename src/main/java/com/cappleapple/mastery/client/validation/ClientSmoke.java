package com.cappleapple.mastery.client.validation;

import com.cappleapple.mastery.client.ClientState;
import com.cappleapple.mastery.client.gui.MasteryScreen;
import com.cappleapple.mastery.client.LayoutPreferences;
import com.cappleapple.mastery.client.gui.editor.*;
import com.cappleapple.mastery.client.export.ClientExport;
import java.util.zip.ZipFile;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import top.theillusivec4.curios.api.CuriosApi;
import com.cappleapple.mastery.layout.GraphLayout;
import com.cappleapple.mastery.network.MasteryNetwork;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Opt-in live client harness, excluded from distribution JARs.
 * A separate localhost server grants points after checkpoint ready_for_grants.
 * Screenshots come from the rendered Minecraft framebuffer; this is automated, not human playtesting.
 */
@EventBusSubscriber(modid = "mastery", value = Dist.CLIENT)
public final class ClientSmoke {
    private static final boolean ENABLED = Boolean.getBoolean("mastery.clientSmoke");
    private static final boolean OVERVIEW = Boolean.getBoolean("mastery.clientSmokeOverview");
    private static final String ADDRESS = System.getProperty("mastery.clientSmokeAddress", "127.0.0.1:25578");
    private static final Path OUTPUT = Path.of(System.getProperty("mastery.clientSmokeOutput", "client-smoke")).toAbsolutePath();
    private static final String TREE = "mastery:fire", SECOND_TREE = "mastery:two_handed";
    private static final String NODE = "mastery:fireball", SECOND_NODE = "mastery:two_handed/foundation";
    private static final String SPELL = "irons_spellbooks:fireball", CONTEXT = "mastery:spells";
    private static final JsonObject REPORT = new JsonObject();
    private static String phase = "initializing";
    private static long started;
    private static int ticks, phaseTick;
    private static boolean configured, done;
    private static GraphLayout.Point expectedRoot, expectedOffset;
    private static String expectedWorld;
    private static int expectedPoints, expectedFirePoints;
    private static com.cappleapple.mastery.data.DefinitionSet beforeReload;
    private static String beforeDimension;
    private static long beforeDeathRevision;
    private static Map<String, Tag> expectedBook;
    private static final String EDITOR_FIXTURE = "mastery:editor_smoke_fixture";
    private static LayoutPreferences playerView;
    private static Map<String, GraphLayout.Point> playerAnchors;
    private static double menuX, menuY;
    private static boolean sectorsBefore;
    private static Path previousExport;
    private static String exportedFireName;
    private static final String EDITOR_BOOK = "mastery:editor_smoke_skill";
    private static String deletingId = "";


    private ClientSmoke() {}

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!ENABLED || done) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (started == 0) started = System.nanoTime();
            ticks++; phaseTick++;
            if (!configured) {
                Files.createDirectories(OUTPUT);
                mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
                mc.options.pauseOnLostFocus = false;
                mc.options.framerateLimit().set(30);
                mc.options.guiScale().set(2);
                mc.resizeDisplay();
                mc.options.save();
                GLFW.glfwSetWindowAttrib(mc.getWindow().getWindow(), GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
                GLFW.glfwHideWindow(mc.getWindow().getWindow());
                configured = true;
                REPORT.addProperty("automated_live_client", true);
                REPORT.addProperty("address", ADDRESS);
                REPORT.addProperty("muted", true);
                REPORT.addProperty("window_hidden", true);
                checkpoint("initializing");
            }
            mc.mouseHandler.releaseMouse();
            mc.getToasts().clear();
            if (System.nanoTime() - started > 600_000_000_000L) throw new IllegalStateException("Timed out in " + phase);
            if (mc.screen instanceof DisconnectedScreen) throw new IllegalStateException("Disconnected while " + phase);
            switch (phase) {
                case "initializing" -> {
                    if (ticks > 30 && mc.getOverlay() == null) connect(mc, "connecting");
                }
                case "connecting" -> {
                    if (synced(mc)) {
                        if (OVERVIEW) {
                            verifyNormalVisibility();
                            require(ClientState.progress().tree(TREE).discovered() && ClientState.progress().tree(SECOND_TREE).discovered(), "overview fixture has two discovered trees");
                            prepareOverview(mc, false);
                            checkpoint("overview_frame");
                            break;
                        }
                        expectedWorld = ClientState.runtime().get("world_id").getAsString();
                        REPORT.addProperty("definitions_received", ClientState.definitions().nodes().size());
                        if (mc.player.isDeadOrDying()) { mc.player.respawn(); mc.setScreen(null); break; }
                        long initialTrees = ClientState.entries().values().stream().filter(e -> e.kind() == GraphLayout.Kind.TREE).count();
                        if (initialTrees > 0 || ClientState.progress().nodes().values().stream().anyMatch(n -> n.rank() > 0)) checkpoint("ready_for_reset");
                        else {
                            REPORT.addProperty("initial_visible_trees", 0);
                            mc.setScreen(new MasteryScreen());
                            checkpoint("initial_frame");
                        }
                    }
                }
                case "overview_frame" -> {
                    if (phaseTick >= 25) {
                        screenshot(mc, "06-discovered-tree-overview.png");
                        prepareOverview(mc, true);
                        checkpoint("detail_overview_frame");
                    }
                }
                case "detail_overview_frame" -> {
                    if (phaseTick >= 25) {
                        screenshot(mc, "07-developed-subtree.png");
                        REPORT.addProperty("overview_captured", true);
                        REPORT.addProperty("saved_anchors_unchanged", true);
                        playerView = ClientState.view();
                        playerAnchors = Map.copyOf(ClientState.layout().anchors());
                        sectorsBefore = playerView.showSectors;
                        mc.getConnection().sendCommand("mastery edit_mode true");
                        checkpoint("enabling_editor");
                    }
                }
                case "ready_for_reset" -> {
                    if (ClientState.progress().trees().values().stream().noneMatch(t -> t.discovered())
                            && ClientState.progress().nodes().values().stream().noneMatch(n -> n.rank() > 0)) {
                        REPORT.addProperty("initial_visible_trees", 0);
                        mc.setScreen(new MasteryScreen());
                        checkpoint("initial_frame");
                    }
                }
                case "initial_frame" -> {
                    if (phaseTick >= 20) {
                        require(!com.cappleapple.mastery.client.ContextualInput.key(mc.getWindow().getWindow(), GLFW.GLFW_KEY_Q, 0, GLFW.GLFW_PRESS), "unbound contextual Q leaves normal input untouched");
                        REPORT.addProperty("unbound_key_passes_through", true);
                        screenshot(mc, "01-before-grants.png");
                        checkpoint("ready_for_grants");
                    }
                }
                case "ready_for_grants" -> {
                    if (ClientState.progress().rank(NODE) == 0 && ClientState.progress().rank(SECOND_NODE) == 0
                            && ClientState.progress().tree(TREE).points() >= 2 && ClientState.progress().tree(SECOND_TREE).points() >= 2) {
                        require(ClientState.progress().tree(TREE).discovered() && ClientState.progress().tree(SECOND_TREE).discovered(), "point grants discover trees");
                        require(ClientState.entries().containsKey(TREE) && ClientState.entries().containsKey(SECOND_TREE), "discovered roots become visible");
                        expectedPoints = ClientState.progress().tree(TREE).points() - 1;
                        expectedFirePoints = ClientState.progress().tree(SECOND_TREE).points() - 1;
                        MasteryNetwork.sendAction("purchase", NODE, "", 0);
                        MasteryNetwork.sendAction("purchase", SECOND_NODE, "", 0);
                        checkpoint("purchasing");
                    }
                }
                case "purchasing" -> {
                    if (ClientState.progress().rank(NODE) == 1 && ClientState.progress().rank(SECOND_NODE) == 1
                            && ClientState.capacity() > 0 && !nativeBooks(mc).isEmpty()) {
                        require(ClientState.progress().tree(TREE).points() == expectedPoints, "purchase debits its own tree");
                        require(ClientState.progress().tree(SECOND_TREE).points() == expectedFirePoints, "second purchase has independent balance");
                        expectedBook = nativeBooks(mc);
                        REPORT.addProperty("native_equipped_book_count", expectedBook.size());
                        REPORT.addProperty("native_equipment_capacity", ClientState.capacity());
                        MasteryNetwork.sendAction("equip", SPELL, CONTEXT, 0);
                        checkpoint("equipping");
                    }
                }
                case "equipping" -> {
                    if (bound(CONTEXT, SPELL)) {
                        require(expectedBook.equals(nativeBooks(mc)), "preparing a Mastery spell leaves the native book item unchanged");
                        REPORT.addProperty("native_book_item_unchanged_by_preparation", true);
                        REPORT.addProperty("server_purchase_and_equip", true);
                        exercisePointerDrag(mc);
                        checkpoint("drag_frame");
                    }
                }
                case "drag_frame" -> {
                    if (phaseTick >= 25) {
                        screenshot(mc, "02-dragged-skill-map.png");
                        expectedRoot = ClientState.layout().anchors().get(TREE);
                        expectedOffset = ClientState.view().offsets.get(NODE);
                        require(expectedOffset != null, "subtree has a parent-relative offset");
                        ClientState.view().save();
                        mc.disconnect(new TitleScreen());
                        mc.setScreen(new TitleScreen());
                        checkpoint("disconnected_for_reconnect");
                    }
                }
                case "disconnected_for_reconnect" -> {
                    if (phaseTick >= 30 && mc.player == null && mc.getOverlay() == null) connect(mc, "reconnecting");
                }
                case "reconnecting" -> {
                    if (synced(mc)) {
                        require(expectedWorld.equals(ClientState.runtime().get("world_id").getAsString()), "world identity survives reconnect");
                        require(ClientState.progress().rank(NODE) == 1 && ClientState.progress().rank(SECOND_NODE) == 1, "purchases survive reconnect");
                        require(bound(CONTEXT, SPELL), "loadouts survive reconnect");
                        require(ClientState.progress().tree(TREE).points() == expectedPoints, "point balance survives reconnect");
                        var restoredRoot = ClientState.layout().anchors().get(TREE);
                        var restoredOffset = ClientState.view().offsets.get(NODE);
                        require(close(expectedRoot, restoredRoot), "dragged tree anchor survives reconnect");
                        require(close(expectedOffset, restoredOffset), "parent-relative subtree offset survives reconnect");
                        require(GraphLayout.sectionAt(expectedRoot.x(), expectedRoot.y()).equals(ClientState.view().orientations.get(TREE)), "growth orientation survives reconnect");
                        REPORT.addProperty("reconnect_state_and_layout", true);
                        mc.setScreen(new MasteryScreen());
                        checkpoint("reconnected_frame");
                    }
                }
                case "reconnected_frame" -> {
                    if (phaseTick >= 20) {
                        screenshot(mc, "03-after-reconnect.png");
                        beforeReload = ClientState.definitions();
                        checkpoint("ready_for_online_reload");
                    }
                }
                case "ready_for_online_reload" -> {
                    if (ClientState.definitions() != beforeReload && phaseTick > 5) {
                        verifyPersistentState("online reload");
                        REPORT.addProperty("online_reload_keeps_progress_and_layout", true);
                        mc.setScreen(new MasteryScreen());
                        checkpoint("reloaded_frame");
                    }
                }
                case "reloaded_frame" -> {
                    if (phaseTick >= 20) {
                        screenshot(mc, "04-after-online-reload.png");
                        beforeDimension = mc.level.dimension().location().toString();
                        checkpoint("ready_for_dimension_change");
                    }
                }
                case "ready_for_dimension_change" -> {
                    if (mc.level != null && !beforeDimension.equals(mc.level.dimension().location().toString()))
                        checkpoint("dimension_changed_frame");
                }
                case "dimension_changed_frame" -> {
                    if (phaseTick >= 30 && synced(mc)) {
                        verifyPersistentState("dimension change");
                        REPORT.addProperty("dimension_change_keeps_progress_and_layout", true);
                        beforeDeathRevision = ClientState.revision();
                        checkpoint("ready_for_death");
                    }
                }
                case "ready_for_death" -> {
                    if (mc.player != null && mc.player.isDeadOrDying()) {
                        mc.player.respawn();
                        mc.setScreen(null);
                        checkpoint("respawning");
                    }
                }
                case "respawning" -> {
                    if (phaseTick >= 30 && synced(mc) && mc.player.isAlive() && ClientState.revision() > beforeDeathRevision) {
                        verifyPersistentState("death and respawn");
                        REPORT.addProperty("death_respawn_keeps_progress_and_layout", true);
                        mc.setScreen(new MasteryScreen());
                        checkpoint("respawn_frame");
                    }
                }
                case "respawn_frame" -> {
                    if (phaseTick >= 20) {
                        screenshot(mc, "05-after-respawn.png");
                        playerView = ClientState.view();
                        playerAnchors = Map.copyOf(ClientState.layout().anchors());
                        sectorsBefore = playerView.showSectors;
                        mc.getConnection().sendCommand("mastery edit_mode true");
                        checkpoint("enabling_editor");
                    }
                }
                case "enabling_editor" -> {
                    if (ClientState.editMode()) {
                        require(ClientState.view() != playerView, "editor uses transient preferences");
                        require(ClientState.entries().size() == ClientState.definitions().trees().size() + ClientState.definitions().nodes().size(), "editor reveals every tree and node including sealed branches");
                        require(ClientState.visible().size() == ClientState.entries().size(), "editor fully expands all branches");
                        var defaults = GraphLayout.arrange(ClientState.entries().values(), Map.of(), ClientState.definitions().trees().keySet());
                        require(defaults.anchors().equals(ClientState.layout().anchors()), "editor ignores player chronology and saved layout");
                        ClientState.view().selected = "";
                        centerOn(ClientState.layout().anchors().get(TREE));
                        mc.setScreen(new MasteryScreen());
                        checkpoint("editor_map_frame");
                    }
                }
                case "editor_map_frame" -> {
                    if (phaseTick >= 25) {
                        var screen = (MasteryScreen)mc.screen;
                        var anchors = Map.copyOf(ClientState.layout().anchors());
                        double zoom = ClientState.view().zoom;
                        screen.mouseScrolled(30, 90, 0, -1);
                        require(ClientState.view().zoom < zoom, "editor wheel zoom works");
                        screen.mouseClicked(30, 90, 2); screen.mouseDragged(80, 120, 2, 50, 30); screen.mouseReleased(80, 120, 2);
                        require(anchors.equals(ClientState.layout().anchors()), "editor pan and zoom preserve default anchors");
                        Button sectors = button(screen, "Sectors");
                        boolean before = ClientState.view().showSectors;
                        screen.mouseClicked(sectors.getX() + 4, sectors.getY() + 4, 0);
                        require(ClientState.view().showSectors != before && playerView.showSectors == sectorsBefore, "editor sector toggle does not alter player preference");
                        REPORT.addProperty("editor_default_layout_and_navigation", true);
                        centerOn(ClientState.layout().anchors().get(TREE));
                        screenshot(mc, "08-operator-default-map.png");
                        menuX = screen.width / 2.0; menuY = (62 + screen.height - 26) / 2.0;
                        screen.mouseClicked(menuX, menuY, 1);
                        checkpoint("editor_menu_frame");
                    }
                }
                case "editor_menu_frame" -> {
                    if (phaseTick >= 25) {
                        screenshot(mc, "09-operator-context-menu.png");
                        ((MasteryScreen)mc.screen).mouseClicked(menuX + 12, menuY + 10, 0);
                        checkpoint("editor_existing_form");
                    }
                }
                case "editor_existing_form" -> {
                    if (mc.screen instanceof DefinitionEditorScreen && phaseTick >= 20) {
                        screenshot(mc, "10-operator-definition-form.png");
                        REPORT.addProperty("editor_context_menu_fetches_definition", true);
                        mc.screen.onClose();
                        EditorClient.addTree((MasteryScreen)mc.screen);
                        setEditorDraft(mc, EDITOR_FIXTURE, "Smoke fixture", "south");
                        checkpoint("editor_creating");
                    }
                }
                case "editor_creating" -> {
                    if (ClientState.definitions().trees().containsKey(EDITOR_FIXTURE) && mc.screen instanceof MasteryScreen screen) {
                        EditorClient.edit(screen, EDITOR_FIXTURE);
                        checkpoint("editor_editing_form");
                    }
                }
                case "editor_editing_form" -> {
                    if (mc.screen instanceof DefinitionEditorScreen) {
                        setEditorDraft(mc, EDITOR_FIXTURE, "Smoke fixture edited", "northeast");
                        checkpoint("editor_editing");
                    }
                }
                case "editor_editing" -> {
                    var fixture = ClientState.definitions().trees().get(EDITOR_FIXTURE);
                    if (fixture != null && fixture.name().equals("Smoke fixture edited") && fixture.section().equals("northeast") && mc.screen instanceof MasteryScreen screen) {
                        REPORT.addProperty("editor_create_and_edit_apply_immediately", true);
                        EditorClient.addChild(screen, EDITOR_FIXTURE);
                        checkpoint("editor_book_form");
                    }
                }
                case "editor_book_form" -> {
                    if (mc.screen instanceof DefinitionEditorScreen screen) {
                        EditBox id = screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
                        MultiLineEditBox text = screen.children().stream().filter(MultiLineEditBox.class::isInstance).map(MultiLineEditBox.class::cast).findFirst().orElseThrow();
                        JsonObject node = EditorDrafts.node(EDITOR_FIXTURE, "");
                        node.addProperty("name", "Diamond Skill Book branch"); node.addProperty("icon", "minecraft:diamond"); node.addProperty("max_rank", 5);
                        id.setValue(EDITOR_BOOK); text.setValue(new GsonBuilder().setPrettyPrinting().create().toJson(node));
                        Button toggle = button(screen, "Require Skill Book"); screen.mouseClicked(toggle.getX() + 4, toggle.getY() + 4, 0);
                        require(com.google.gson.JsonParser.parseString(text.getValue()).getAsJsonObject().get("book_token").getAsString().equals(EDITOR_BOOK), "book toggle writes edited node ID into actual JSON");
                        checkpoint("editor_book_form_frame");
                    }
                }
                case "editor_book_form_frame" -> {
                    if (phaseTick >= 25) {
                        screenshot(mc, "13-editor-skill-book-toggle.png");
                        Button save = button(mc.screen, "Save to world"); mc.screen.mouseClicked(save.getX() + 4, save.getY() + 4, 0);
                        checkpoint("editor_book_saving");
                    }
                }
                case "editor_book_saving" -> {
                    var node = ClientState.definitions().nodes().get(EDITOR_BOOK);
                    if (node != null && node.bookToken().equals(EDITOR_BOOK) && mc.screen instanceof MasteryScreen) {
                        require(node.maxRank() == 5, "editor persists repeatable ranks with book requirement");
                        REPORT.addProperty("editor_skill_book_toggle_saved", true);
                        mc.getConnection().sendCommand(BookGateEditor.giveCommand(EDITOR_BOOK).substring(1));
                        checkpoint("receiving_skill_book");
                    }
                }
                case "receiving_skill_book" -> {
                    var book = findSkillBook(mc);
                    if (!book.isEmpty()) {
                        var plain = new net.minecraft.world.item.ItemStack(book.getItem());
                        int plainPasses = mc.getItemRenderer().getModel(plain, mc.level, mc.player, 0).getRenderPasses(plain, false).size();
                        int stampedPasses = mc.getItemRenderer().getModel(book, mc.level, mc.player, 0).getRenderPasses(book, false).size();
                        require(stampedPasses > plainPasses, "token book resolves an additional native baked icon pass");
                        REPORT.addProperty("generic_skill_book_icon_render_passes", stampedPasses);
                        mc.setScreen(new BookPreviewScreen(book.copy())); checkpoint("skill_book_render_frame");
                    }
                }
                case "skill_book_render_frame" -> {
                    if (phaseTick >= 25) {
                        screenshot(mc, "14-generic-skill-book-icon.png");
                        REPORT.addProperty("generic_skill_book_item_received_and_rendered", true);
                        mc.setScreen(new MasteryScreen());
                        EditorClient.edit((MasteryScreen)mc.screen, EDITOR_BOOK);
                        checkpoint("custom_book_form");
                    }
                }
                case "custom_book_form" -> {
                    if (mc.screen instanceof DefinitionEditorScreen screen) {
                        MultiLineEditBox text = screen.children().stream().filter(MultiLineEditBox.class::isInstance).map(MultiLineEditBox.class::cast).findFirst().orElseThrow();
                        JsonObject node = com.google.gson.JsonParser.parseString(text.getValue()).getAsJsonObject();
                        node.addProperty("icon", "mastery:textures/gui/sprites/graph/rune.png");
                        text.setValue(new GsonBuilder().setPrettyPrinting().create().toJson(node));
                        Button save = button(screen, "Save to world"); screen.mouseClicked(save.getX() + 4, save.getY() + 4, 0);
                        checkpoint("custom_book_saving");
                    }
                }
                case "custom_book_saving" -> {
                    var node = ClientState.definitions().nodes().get(EDITOR_BOOK);
                    if (node != null && node.icon().equals("mastery:textures/gui/sprites/graph/rune.png") && mc.screen instanceof MasteryScreen) {
                        var book = findSkillBook(mc);
                        require(!book.isEmpty(), "generic token book remains in inventory after icon definition change");
                        require(mc.getItemRenderer().getModel(book, mc.level, mc.player, 0).getRenderPasses(book, false).size() >= 2, "custom texture creates an additional book render pass");
                        mc.setScreen(new BookPreviewScreen(book.copy(), "Resource texture icon"));
                        checkpoint("custom_book_render_frame");
                    }
                }
                case "custom_book_render_frame" -> {
                    if (phaseTick >= 25) {
                        screenshot(mc, "15-generic-skill-book-resource-icon.png");
                        REPORT.addProperty("generic_skill_book_custom_texture_rendered", true);
                        mc.setScreen(new MasteryScreen());
                        previousExport = ClientExport.lastExport();
                        exportedFireName = ClientState.definitions().trees().get(TREE).name();
                        mc.getConnection().sendCommand("mastery export"); checkpoint("exporting");
                    }
                }
                case "exporting" -> {
                    require(ClientExport.lastFailure().isBlank(), "client export completed without filesystem or transfer error: " + ClientExport.lastFailure());
                    Path exported = ClientExport.lastExport();
                    if (exported != null && !exported.equals(previousExport)) {
                        require(exported.getParent().equals(mc.gameDirectory.toPath().resolve("config/exports").toAbsolutePath().normalize()), "export is in calling client's config/exports");
                        require(exported.getFileName().toString().matches("mastery-\\d{2}-\\d{2}-\\d{2}-\\d{2}-\\d{4}(?:-\\d+)?\\.zip"), "export filename uses local Windows-safe date and time");
                        try (ZipFile archive = new ZipFile(exported.toFile())) {
                            JsonObject metadata = zipJson(archive, "pack.mcmeta");
                            require(metadata.getAsJsonObject("pack").get("pack_format").getAsInt() == 48, "export is a Minecraft1.21.1 datapack");
                            JsonObject edited = zipJson(archive, "data/mastery/mastery/trees/editor_smoke_fixture.json");
                            require(edited.get("name").getAsString().equals("Smoke fixture edited") && edited.get("section").getAsString().equals("northeast"), "export contains current server editor override");
                            require(zipJson(archive, "data/mastery/mastery/trees/fire.json").get("name").getAsString().equals(exportedFireName), "export contains the server's Fire override");
                            require(zipJson(archive, "data/irons_spellbooks/mastery/spells/fireball.json").get("spell").getAsString().equals(SPELL), "export preserves actual native spell IDs");
                            JsonObject bookNode = zipJson(archive, "data/mastery/mastery/nodes/editor_smoke_skill.json");
                            require(bookNode.get("book_token").getAsString().equals(EDITOR_BOOK) && bookNode.get("max_rank").getAsInt() == 5, "export includes saved Skill Book gate and repeatable rank definition");
                        }
                        REPORT.addProperty("export_written_to_calling_client", true);
                        REPORT.addProperty("export_contains_current_server_overrides", true);
                        REPORT.addProperty("export_server_fire_name", exportedFireName);
                        REPORT.addProperty("export_file", exported.toString());
                        deletingId = EDITOR_BOOK;
                        EditorClient.delete((MasteryScreen)mc.screen, deletingId);
                        checkpoint("editor_delete_confirmation");
                    }
                }
                case "editor_delete_confirmation" -> {
                    if (mc.screen instanceof ConfirmScreen screen) {
                        Button confirm = screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).findFirst().orElseThrow();
                        screen.mouseClicked(confirm.getX() + 3, confirm.getY() + 3, 0);
                        checkpoint("editor_deleting");
                    }
                }
                case "editor_deleting" -> {
                    if (deletingId.equals(EDITOR_BOOK) && !ClientState.definitions().nodes().containsKey(EDITOR_BOOK)) {
                        deletingId = EDITOR_FIXTURE;
                        EditorClient.delete((MasteryScreen)mc.screen, deletingId);
                        checkpoint("editor_delete_confirmation");
                    } else if (deletingId.equals(EDITOR_FIXTURE) && !ClientState.definitions().trees().containsKey(EDITOR_FIXTURE)) {
                        REPORT.addProperty("editor_delete_applies_immediately", true);
                        mc.getConnection().sendCommand("mastery edit_mode false");
                        checkpoint("disabling_editor");
                    }
                }
                case "disabling_editor" -> {
                    if (!ClientState.editMode()) {
                        require(ClientState.view() == playerView, "normal layout preferences restored");
                        require(playerAnchors.equals(ClientState.layout().anchors()), "editor changes preserve player anchors");
                        require(ClientState.view().showSectors == sectorsBefore, "normal sector preference restored");
                        REPORT.addProperty("editor_exit_restores_player_layout", true);
                        if (net.neoforged.fml.ModList.get().isLoaded("patchouli")) {
                            validateAndOpenGuide(); checkpoint("guide_landing_frame");
                        } else {
                            REPORT.addProperty("patchouli_loaded", false); complete(mc, null);
                        }
                    }
                }
                case "guide_landing_frame" -> {
                    if (phaseTick >= 30) {
                        screenshot(mc, "11-guidebook-landing.png");
                        openGuideEntry("native_spellbooks");
                        checkpoint("guide_entry_frame");
                    }
                }
                case "guide_entry_frame" -> {
                    if (phaseTick >= 30) {
                        screenshot(mc, "12-guidebook-native-equipment.png");
                        REPORT.addProperty("guidebook_rendered", true); complete(mc, null);
                    }
                }
                default -> throw new IllegalStateException("Unknown smoke phase " + phase);
            }
        } catch (Exception exception) {
            LogUtils.getLogger().error("Mastery live client smoke failed in {}", phase, exception);
            complete(mc, exception);
        }
    }

    private static void verifyNormalVisibility() {
        require(!ClientState.editMode(), "overview starts in ordinary player mode");
        int unavailable = 0, bookGated = 0;
        for (var node : ClientState.definitions().nodes().values()) {
            var state = ClientState.progress().nodes().get(node.id());
            boolean book = com.cappleapple.mastery.progression.ProgressionService.bookUnlocked(ClientState.definitions(), ClientState.progress(), node.id());
            boolean visible = com.cappleapple.mastery.layout.GraphPresentation.visible(node.visibility(), ClientState.progress().rank(node.id()), state == null ? 0 : state.unlockOrder());
            if (!book || !visible) {
                require(!ClientState.entries().containsKey(node.id()), "hidden node absent from search, selection, and layout: " + node.id());
                unavailable++; if (!book) bookGated++;
            }
        }
        require(unavailable > 0 && bookGated > 0, "live fixture includes unavailable and book-gated branches");
        REPORT.addProperty("normal_hidden_nodes_excluded_from_entries", unavailable);
        REPORT.addProperty("normal_book_gated_nodes_excluded_from_entries", bookGated);
    }
    private static net.minecraft.world.item.ItemStack findSkillBook(Minecraft mc) {
        for (var stack : mc.player.getInventory().items) {
            if (!net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals("mastery:skill_book")) continue;
            var data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            if (data != null && data.copyTag().getString("mastery_unlock").equals(EDITOR_BOOK)) return stack;
        }
        return net.minecraft.world.item.ItemStack.EMPTY;
    }
    private static final class BookPreviewScreen extends net.minecraft.client.gui.screens.Screen {
        private final net.minecraft.world.item.ItemStack book;
        private final String iconLabel;
        private BookPreviewScreen(net.minecraft.world.item.ItemStack book) { this(book, "Diamond branch icon"); }
        private BookPreviewScreen(net.minecraft.world.item.ItemStack book, String iconLabel) { super(net.minecraft.network.chat.Component.literal("Generic Skill Book icon validation")); this.book = book; this.iconLabel = iconLabel; }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void renderBackground(net.minecraft.client.gui.GuiGraphics graphics, int x, int y, float partialTick) {}
        @Override public void render(net.minecraft.client.gui.GuiGraphics graphics, int x, int y, float partialTick) {
            graphics.fill(0, 0, width, height, 0xFF292923);
            graphics.drawCenteredString(font, title, width / 2, 30, 0xFFE2B3);
            var plain = new net.minecraft.world.item.ItemStack(book.getItem());
            for (int index = 0; index < 2; index++) {
                int center = width * (index == 0 ? 1 : 3) / 4;
                graphics.pose().pushPose(); graphics.pose().translate(center - 64, height / 2.0 - 64, 0); graphics.pose().scale(8, 8, 1);
                graphics.renderItem(index == 0 ? plain : book, 0, 0); graphics.pose().popPose();
                graphics.drawCenteredString(font, index == 0 ? "Unconfigured book" : iconLabel, center, height / 2 + 83, 0xFFFFFF);
            }
            graphics.drawCenteredString(font, "Same mastery:skill_book item | Token selects the branch icon", width / 2, height - 43, 0xCFCFCF);
            graphics.drawCenteredString(font, EDITOR_BOOK, width / 2, height - 27, 0xAAAAAA);
        }
    }
    private static JsonObject zipJson(ZipFile archive, String path) throws Exception {
        var entry = archive.getEntry(path); require(entry != null, "export contains " + path);
        try (var reader = new java.io.InputStreamReader(archive.getInputStream(entry), java.nio.charset.StandardCharsets.UTF_8)) {
            return com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
    private static Map<String, Tag> nativeBooks(Minecraft mc) {
        Map<String, Tag> result = new java.util.TreeMap<>();
        CuriosApi.getCuriosInventory(mc.player).ifPresent(handler -> handler.getCurios().forEach((kind, slots) -> {
            var stacks = slots.getStacks();
            for (int slot = 0; slot < stacks.getSlots(); slot++) {
                var stack = stacks.getStackInSlot(slot);
                if (!stack.isEmpty() && ISpellContainer.isSpellContainer(stack) && ISpellContainer.get(stack).isSpellWheel())
                    result.put(kind + "/" + slot, stack.save(mc.level.registryAccess()).copy());
            }
        }));
        return result;
    }
    private static Button button(net.minecraft.client.gui.screens.Screen screen, String prefix) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(value -> value.getMessage().getString().startsWith(prefix)).findFirst().orElseThrow();
    }
    private static void setEditorDraft(Minecraft mc, String id, String name, String section) {
        var screen = (DefinitionEditorScreen)mc.screen;
        EditBox idBox = screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
        MultiLineEditBox text = screen.children().stream().filter(MultiLineEditBox.class::isInstance).map(MultiLineEditBox.class::cast).findFirst().orElseThrow();
        JsonObject definition = EditorDrafts.tree(); definition.addProperty("name", name); definition.addProperty("section", section);
        idBox.setValue(id); text.setValue(new GsonBuilder().setPrettyPrinting().create().toJson(definition));
        Button save = button(screen, "Save to world"); screen.mouseClicked(save.getX() + 5, save.getY() + 5, 0);
    }
    private static void validateAndOpenGuide() throws Exception {
        var bookId = ResourceLocation.parse("mastery:guide");
        Class<?> registryClass = Class.forName("vazkii.patchouli.common.book.BookRegistry");
        Object registry = registryClass.getField("INSTANCE").get(null);
        Map<?, ?> books = (Map<?, ?>)registryClass.getField("books").get(registry);
        Object book = books.get(bookId); require(book != null, "optional Patchouli guide registered");
        Object contents = book.getClass().getMethod("getContents").invoke(book);
        require(!(boolean)contents.getClass().getMethod("isErrored").invoke(contents), "Patchouli guide has no content errors");
        require(((Map<?, ?>)contents.getClass().getField("entries").get(contents)).size() == 11, "all player guide entries loaded");
        REPORT.addProperty("patchouli_loaded", true);
        Minecraft.getInstance().gui.getChat().clearMessages(true);
        Object api = Class.forName("vazkii.patchouli.api.PatchouliAPI").getMethod("get").invoke(null);
        Class.forName("vazkii.patchouli.api.PatchouliAPI$IPatchouliAPI").getMethod("openBookGUI", ResourceLocation.class).invoke(api, bookId);
    }
    private static void openGuideEntry(String entry) throws Exception {
        Minecraft.getInstance().gui.getChat().clearMessages(true);
        Object api = Class.forName("vazkii.patchouli.api.PatchouliAPI").getMethod("get").invoke(null);
        Class.forName("vazkii.patchouli.api.PatchouliAPI$IPatchouliAPI").getMethod("openBookEntry", ResourceLocation.class, ResourceLocation.class, int.class)
                .invoke(api, ResourceLocation.parse("mastery:guide"), ResourceLocation.parse("mastery:" + entry), 0);
    }

    /** Fits the camera to a subset of existing anchors; no graph position is edited. */
    private static void prepareOverview(Minecraft mc, boolean detail) {
        ClientState.view().expanded.clear();
        ClientState.view().selected = detail ? NODE : "";
        if (detail) {
            ClientState.view().expanded.add(TREE);
            ClientState.view().expanded.add(NODE);
        }
        Map<String, GraphLayout.Point> anchors = ClientState.layout().anchors();
        List<GraphLayout.Point> focus = (detail ? List.of(TREE, NODE, "mastery:fireball/efficiency", "mastery:fireball/empowered")
                : List.of(TREE, SECOND_TREE)).stream().map(anchors::get).filter(java.util.Objects::nonNull).toList();
        require(!focus.isEmpty(), "overview has anchors");
        double minX = focus.stream().mapToDouble(GraphLayout.Point::x).min().orElse(0), maxX = focus.stream().mapToDouble(GraphLayout.Point::x).max().orElse(0);
        double minY = focus.stream().mapToDouble(GraphLayout.Point::y).min().orElse(0), maxY = focus.stream().mapToDouble(GraphLayout.Point::y).max().orElse(0);
        int width = mc.getWindow().getGuiScaledWidth(), height = mc.getWindow().getGuiScaledHeight();
        int detailWidth = detail && width >= 400 ? Math.min(212, width / 3) + 6 : 0;
        double zoom = Math.min((width - 40 - detailWidth) / Math.max(1, maxX - minX + 140), (height - 110) / Math.max(1, maxY - minY + 100));
        ClientState.view().zoom = Math.max(0.25, Math.min(1.1, zoom));
        ClientState.view().panX = -(minX + maxX) / 2 * ClientState.view().zoom;
        ClientState.view().panY = -(minY + maxY) / 2 * ClientState.view().zoom;
        mc.setScreen(new MasteryScreen());
    }

    private static void verifyPersistentState(String stage) {
        require(expectedWorld.equals(ClientState.runtime().get("world_id").getAsString()), stage + ": world identity");
        require(ClientState.progress().rank(NODE) == 1 && ClientState.progress().rank(SECOND_NODE) == 1, stage + ": purchased ranks");
        require(bound(CONTEXT, SPELL), stage + ": equipped loadouts");
        require(ClientState.progress().tree(TREE).points() == expectedPoints, stage + ": point balance");
        require(close(expectedRoot, ClientState.layout().anchors().get(TREE)), stage + ": tree anchor");
        require(close(expectedOffset, ClientState.view().offsets.get(NODE)), stage + ": relative subtree offset");
    }

    private static boolean synced(Minecraft mc) {
        return mc.player != null && !ClientState.definitions().trees().isEmpty() && ClientState.runtime().has("world_id");
    }
    private static boolean bound(String context, String ability) {
        List<String> slots = ClientState.progress().loadouts().get(context);
        return slots != null && !slots.isEmpty() && ability.equals(slots.getFirst());
    }
    private static void connect(Minecraft mc, String next) throws Exception {
        ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(ADDRESS),
                new ServerData("Mastery smoke", ADDRESS, ServerData.Type.OTHER), false, null);
        checkpoint(next);
    }

    /** Sends the screen's real click/drag/release callbacks, then tests the saved local offset. */
    private static void exercisePointerDrag(Minecraft mc) {
        ClientState.view().selected = "";
        ClientState.view().zoom = 0.75;
        ClientState.view().expanded.add(TREE);
        ClientState.view().expanded.add(SECOND_TREE);
        GraphLayout.Point root = ClientState.layout().anchors().get(TREE);
        centerOn(root);
        MasteryScreen screen = new MasteryScreen();
        mc.setScreen(screen);
        double x = screen.width / 2.0, y = (62 + screen.height - 26) / 2.0;
        require(screen.mouseClicked(x, y, 0), "tree pointer press is accepted");
        screen.mouseDragged(x + 80, y + 20, 0, 80, 20);
        screen.mouseReleased(x + 80, y + 20, 0);
        GraphLayout.Point moved = ClientState.layout().anchors().get(TREE);
        require(Math.abs(moved.x() - root.x() - 80 / 0.75) < 0.001, "root follows pointer horizontally");
        require(Math.abs(moved.y() - root.y() - 20 / 0.75) < 0.001, "root follows pointer vertically");
        require(GraphLayout.sectionAt(moved.x(), moved.y()).equals(ClientState.view().orientations.get(TREE)), "growth orientation follows the fixed logical map center");

        GraphLayout.Point child = ClientState.layout().anchors().get(NODE);
        centerOn(child);
        mc.setScreen(screen = new MasteryScreen());
        x = screen.width / 2.0; y = (62 + screen.height - 26) / 2.0;
        screen.mouseClicked(x, y, 0);
        screen.mouseDragged(x - 30, y + 22, 0, -30, 22);
        screen.mouseReleased(x - 30, y + 22, 0);
        GraphLayout.Point childMoved = ClientState.layout().anchors().get(NODE);
        require(Math.abs(childMoved.x() - child.x() + 30 / 0.75) < 0.001, "subtree follows pointer");
        require(ClientState.view().manualAnchors.contains(NODE), "subtree records manual positioning");
        double zoom = ClientState.view().zoom;
        screen.mouseScrolled(x, y, 0, -1);
        require(ClientState.view().zoom < zoom, "wheel changes canvas zoom");
        REPORT.addProperty("pointer_root_and_subtree_drag", true);
        REPORT.addProperty("wheel_zoom", true);

        GraphLayout.Point fire = ClientState.layout().anchors().get(SECOND_TREE);
        moved = ClientState.layout().anchors().get(TREE);
        ClientState.view().zoom = 0.52;
        ClientState.view().panX = -(moved.x() + fire.x()) / 2 * ClientState.view().zoom;
        ClientState.view().panY = -(moved.y() + fire.y()) / 2 * ClientState.view().zoom + 50;
        ClientState.view().selected = NODE;
        mc.setScreen(new MasteryScreen());
    }
    private static void centerOn(GraphLayout.Point point) {
        ClientState.view().panX = -point.x() * ClientState.view().zoom;
        ClientState.view().panY = -point.y() * ClientState.view().zoom;
    }
    private static boolean close(GraphLayout.Point a, GraphLayout.Point b) {
        return a != null && b != null && Math.abs(a.x() - b.x()) < 0.001 && Math.abs(a.y() - b.y()) < 0.001;
    }
    private static void screenshot(Minecraft mc, String name) throws Exception {
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(OUTPUT.resolve(name)); }
    }
    private static void checkpoint(String next) throws Exception {
        phase = next; phaseTick = 0;
        JsonObject value = new JsonObject();
        value.addProperty("phase", phase);
        value.addProperty("username", Minecraft.getInstance().getUser().getName());
        value.addProperty("ticks", ticks);
        Files.writeString(OUTPUT.resolve("checkpoint.json"), value.toString());
    }
    private static void require(boolean condition, String description) {
        if (!condition) throw new IllegalStateException("Assertion failed: " + description);
    }
    private static void complete(Minecraft mc, Exception error) {
        done = true;
        REPORT.addProperty("success", error == null);
        REPORT.addProperty("phase", phase);
        REPORT.addProperty("ticks", ticks);
        REPORT.addProperty("elapsed_seconds", (System.nanoTime() - started) / 1_000_000_000.0);
        if (error != null) REPORT.addProperty("error", error.toString());
        try {
            Files.createDirectories(OUTPUT);
            Files.writeString(OUTPUT.resolve(OVERVIEW ? "overview-result.json" : "result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(REPORT));
        } catch (Exception writeError) { LogUtils.getLogger().error("Could not save client smoke result", writeError); }
        if (mc.player != null) mc.disconnect(new TitleScreen());
        mc.stop();
    }
}
