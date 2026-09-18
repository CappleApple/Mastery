package com.cappleapple.mastery.client.gui;

import com.cappleapple.mastery.client.*;
import com.cappleapple.mastery.data.*;
import com.cappleapple.mastery.client.gui.editor.*;
import com.cappleapple.mastery.layout.GraphLayout;
import com.cappleapple.mastery.layout.ThemePalette;
import com.cappleapple.mastery.network.MasteryNetwork;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/** One coordinate space, one clipped viewport, with independent optional node details. */
public final class MasteryScreen extends Screen {
    private static final int NODE_W = 40, NODE_H = 40;
    private static final Map<String, ResourceLocation> SPRITES = new HashMap<>();
    private record RenderNode(String id, GraphLayout.Entry entry, String label, ItemStack icon, ResourceLocation customIcon, ResourceLocation texture, boolean expandable) {}
    private record RenderEdge(GraphLayout.Edge edge, GraphLayout.Point from, GraphLayout.Point to) {}
    private final Map<String,TreeTheme> resolvedThemes=new HashMap<>();
    private final Map<String,ConnectionPresentation> resolvedConnections=new HashMap<>();
    private final Map<String,NodeAppearance> resolvedAppearances=new HashMap<>();
    private long themeRevision=-1;
    private long projectionRevision=-1;
    private Map<String,GraphLayout.Point> projectedTargets=Map.of();
    private final List<AbstractWidget> detailWidgets = new ArrayList<>();
    private List<RenderNode> nodes = List.of();
    private final com.cappleapple.mastery.layout.GraphAnimation animation=new com.cappleapple.mastery.layout.GraphAnimation();
    private final Map<String,RenderNode> animatedNodes=new LinkedHashMap<>();
    private Map<String,GraphLayout.Point> displayPositions=Map.of();
    private Map<String,GraphLayout.Point> animationPositions=Map.of(), projectedSource=Map.of();
    private double projectedZoom=-1, projectedMinimum=-1;
    private Set<String> targetVisible=Set.of();
    private List<RenderEdge> edges = List.of();
    private List<FormattedCharSequence> detailLines = List.of();
    private Set<String> relevantTrees = Set.of();
    private EditBox search;
    private boolean showDetails = true;
    private int left, top, right, bottom, detailX, detailWidth, detailsScroll;
    private long cachedRevision = -1, cachedStructureRevision = -1, tooltipRevision = -1;
    private List<Component> hoverTooltip = List.of();
    private List<FormattedCharSequence> footerLines = List.of();
    private String tooltipId = "";
    private String selectedContext = "", pressedNode = "", hovered = "", lastSearch = "";
    private double pressX, pressY;
    private boolean dragging, panning;
    private final com.cappleapple.mastery.layout.UnlockHold hold=new com.cappleapple.mastery.layout.UnlockHold();
    private int pressTicks, pendingRank, pendingTicks;
    private boolean purchaseSent;
    private String pendingPurchase="";
    private UnlockPresentation pendingSounds;

    private Double targetX, targetY;
    private long lastCameraFrame,holdStartedAt;
    private int holdDelayMs;
    private String searchNotice = "";
    private boolean cachedEditMode;
    private String cachedEditorStatus = "";
    private EditorContextMenu contextMenu;

    public MasteryScreen() { super(Component.translatable("screen.mastery.title")); }
    private static ResourceLocation sprite(String name) {
        return SPRITES.computeIfAbsent(name, n -> ResourceLocation.fromNamespaceAndPath("mastery", "graph/" + n));
    }
    @Override protected void init() {
        animation.reset();
        cachedEditMode = ClientState.editMode();
        contextMenu = null;
        search = addRenderableWidget(new EditBox(font, Math.max(150, width - 180), 11, Math.min(165, width - 160), 18, Component.literal("Search discovered skills")));
        search.setHint(Component.literal("Search skills..."));
        search.setValue(lastSearch);
        int x = 10;
        List<ToolbarAction> toolbar = ClientState.editMode() ? List.of(
                new ToolbarAction("Reset", this::resetView),
                new ToolbarAction("Fit map", this::fitMap),
                new ToolbarAction("Definitions", () -> minecraft.setScreen(new EditorLibraryScreen(this))),
                new ToolbarAction("Defaults", () -> EditorClient.editDefaults(this)),
                new ToolbarAction("Exit edit", () -> { if (minecraft.getConnection() != null) minecraft.getConnection().sendCommand("mastery edit_mode false"); }),
                new ToolbarAction("Details", () -> { showDetails = !showDetails; rebuild(); }),
                new ToolbarAction(ClientState.view().showSectors ? "Sectors On" : "Sectors Off", this::toggleSectors)) : List.of(
                new ToolbarAction("Reset", this::resetView),
                new ToolbarAction("Collapse", () -> { ClientState.view().expanded.clear(); rebuild(); }),
                new ToolbarAction("Invested", this::showInvested),
                new ToolbarAction("Spells", () -> minecraft.setScreen(new SpellBindingsScreen(this,""))),
                new ToolbarAction("Details", () -> { showDetails = !showDetails; rebuild(); }),
                new ToolbarAction(ClientState.view().showSectors ? "Sectors On" : "Sectors Off", this::toggleSectors));
        for (var action : toolbar) {
            int buttonWidth = Math.max(43, (width - 26) / toolbar.size());
            addRenderableWidget(Button.builder(Component.literal(action.label()), b -> action.run().run()).bounds(x, 36, buttonWidth - 3, 20).build());
            x += buttonWidth;
        }
        rebuild();
    }
    private record ToolbarAction(String label, Runnable run) {}
    private void calculateBounds() {
        String error = ClientState.runtime().has("last_error") ? ClientState.runtime().get("last_error").getAsString() : "";
        String status = ClientState.editMode()
                ? !EditorClient.status().isBlank() ? EditorClient.status() : "EDIT MODE | Right-click a tree or skill to edit | Default positions"
                : !error.isBlank() ? error : !searchNotice.isBlank() ? searchNotice : "";
        cachedEditorStatus = EditorClient.status();
        footerLines = font.split(Component.literal(status), Math.max(80, width - 18));
        left = 8; top = 62; bottom = height - 12 - Math.min(3, footerLines.size()) * 12;
        boolean panel = showDetails && !ClientState.view().selected.isBlank() && width >= 400;
        detailWidth = panel ? Math.min(212, width / 3) : 0;
        right = width - 8 - (panel ? detailWidth + 6 : 0);
        detailX = right + 6;
    }
    private void rebuild() {
        calculateBounds();
        GraphLayout.Result layout = ClientState.layout();
        if (!ClientState.view().selected.isBlank() && !ClientState.entries().containsKey(ClientState.view().selected)) {
            ClientState.view().selected = ""; calculateBounds();
        }
        Set<String> visible = ClientState.visible();
        List<RenderNode> next = new ArrayList<>();
        for (String id : visible) {
            var entry = ClientState.entries().get(id);
            if (entry == null || !layout.anchors().containsKey(id)) continue;
            String icon = "";
            String texture = "passive";
            if (entry.kind() == GraphLayout.Kind.TREE) { icon = ClientState.definitions().trees().get(id).icon(); texture = "specialization"; }
            else {
                NodeDefinition definition = ClientState.definitions().nodes().get(id);
                if (definition == null) continue;
                icon = definition.icon();
                texture = switch (definition.type()) {
                    case ACTIVE -> "active";
                    case MODIFIER -> "modifier";
                    case SYNERGY -> "synergy";
                    case KEYSTONE, CAPSTONE -> "keystone";
                    case UTILITY -> "utility";
                    default -> "passive";
                };
            }
            ResourceLocation iconId = ResourceLocation.tryParse(icon);
            ItemStack stack = iconId != null && BuiltInRegistries.ITEM.containsKey(iconId) ? new ItemStack(BuiltInRegistries.ITEM.get(iconId)) : ItemStack.EMPTY;
            next.add(new RenderNode(id, entry, ClientState.name(id), stack, stack.isEmpty() ? IconResources.texture(icon) : null, sprite(texture), ClientState.expandable(id)));
        }
        nodes = List.copyOf(next);targetVisible=Set.copyOf(visible);
        nodes.forEach(n->animatedNodes.put(n.id(),n));
        updateAnimation();
        if (!nodes.isEmpty() && !ClientState.view().cameraInitialized) {
            GraphLayout.Point firstRoot = nodes.stream().filter(n -> n.entry().kind() == GraphLayout.Kind.TREE).map(n -> layout.anchors().get(n.id())).findFirst().orElse(new GraphLayout.Point(0, 0));
            ClientState.view().panX = -firstRoot.x() * ClientState.view().zoom;
            ClientState.view().panY = -firstRoot.y() * ClientState.view().zoom + Math.min(60, (bottom - top) * 0.2);
            ClientState.view().cameraInitialized = true;
        }
        List<RenderEdge> nextEdges = new ArrayList<>();
        for (var edge : com.cappleapple.mastery.layout.GraphPresentation.visibleEdges(layout.edges(),ClientState.entries(),visible)) {
            if (!visible.contains(edge.from()) || !visible.contains(edge.to())) continue;
            var a = layout.anchors().get(edge.from()); var b = layout.anchors().get(edge.to());
            if (a != null && b != null) nextEdges.add(new RenderEdge(edge, a, b));
        }
        edges = com.cappleapple.mastery.layout.GraphPresentation.transitionEdges(edges.stream().map(RenderEdge::edge).toList(),
                nextEdges.stream().map(RenderEdge::edge).toList(),visible,displayPositions.keySet()).stream()
                .filter(e->ClientState.entries().containsKey(e.from())&&ClientState.entries().containsKey(e.to()))
                .map(e->new RenderEdge(e,layout.anchors().get(e.from()),layout.anchors().get(e.to()))).toList();
        String selected = ClientState.view().selected;
        var entry = ClientState.entries().get(selected);
        var relevant = new HashSet<String>();
        if (entry != null) {
            relevant.add(entry.tree()); relevant.addAll(entry.relatedTrees());
            for (var e : ClientState.entries().values()) if (e.kind() == GraphLayout.Kind.SYNERGY && e.relatedTrees().contains(entry.tree())) relevant.addAll(e.relatedTrees());
        }
        relevantTrees = Set.copyOf(relevant);
        detailLines = NodeDetails.describe(selected).stream().flatMap(line -> font.split(line, Math.max(80, detailWidth - 16)).stream()).toList();
        rebuildDetailControls();
        cachedRevision = ClientState.revision();
        cachedStructureRevision = ClientState.structureRevision();
    }

    private void refreshDetails() {
        calculateBounds();
        detailLines = NodeDetails.describe(ClientState.view().selected).stream().flatMap(line -> font.split(line, Math.max(80, detailWidth - 16)).stream()).toList();
        rebuildDetailControls();
        cachedRevision = ClientState.revision();
    }
    private Map<String,GraphLayout.Point> displayTargets() {
        Map<String,GraphLayout.Point> points=new HashMap<>();var anchors=ClientState.layout().anchors();
        for(String id:targetVisible)if(anchors.containsKey(id))points.put(id,anchors.get(id));
        if(!ClientState.editMode())for(var node:nodes)if(node.entry().kind()==GraphLayout.Kind.SYNERGY&&!ClientState.view().manualAnchors.contains(node.id())) {
            var parents=node.entry().dependencies();
            if(!parents.isEmpty()&&parents.stream().anyMatch(p->!targetVisible.contains(p))) {
                Set<String> endpoints=new TreeSet<>();parents.forEach(p->endpoints.addAll(com.cappleapple.mastery.layout.GraphPresentation.visibleAncestors(p,ClientState.entries(),targetVisible)));
                var visibleParents=endpoints.stream().map(anchors::get).filter(Objects::nonNull).toList();
                if(!visibleParents.isEmpty())points.put(node.id(),new GraphLayout.Point(visibleParents.stream().mapToDouble(GraphLayout.Point::x).average().orElse(0),visibleParents.stream().mapToDouble(GraphLayout.Point::y).average().orElse(0)-82));
            }
        }
        return points;
    }
    private GraphLayout.Point transitionOrigin(String id,Map<String,GraphLayout.Point> targets) {
        var ancestors=com.cappleapple.mastery.layout.GraphPresentation.visibleAncestors(id,ClientState.entries(),targetVisible.stream().filter(value->!value.equals(id)).collect(java.util.stream.Collectors.toSet()));
        var points=ancestors.stream().map(targets::get).filter(Objects::nonNull).toList();
        return points.isEmpty()?ClientState.layout().anchors().getOrDefault(id,new GraphLayout.Point(0,0)):
                new GraphLayout.Point(points.stream().mapToDouble(GraphLayout.Point::x).average().orElse(0),points.stream().mapToDouble(GraphLayout.Point::y).average().orElse(0));
    }
    private void updateAnimation() {
        double zoom=ClientState.view().zoom, minimum=com.cappleapple.mastery.config.MasteryClientConfig.MINIMUM_NODE_WIDTH.get();
        var bounds=new HashMap<String,com.cappleapple.mastery.layout.ReadableZoom.Bounds>();
        animatedNodes.forEach((id,node)->bounds.put(id,new com.cappleapple.mastery.layout.ReadableZoom.Bounds(
                (appearance(id).showName()?Math.max(52,font.width(node.label())+4):52)*appearanceScale(id),(appearance(id).showName()?78:52)*appearanceScale(id))));
        var rawTargets=displayTargets();
        if(zoom!=projectedZoom||minimum!=projectedMinimum||projectionRevision!=ClientState.definitionRevision()||!rawTargets.equals(projectedSource)) {
            projectedTargets=com.cappleapple.mastery.layout.ReadableZoom.project(rawTargets,zoom,minimum,bounds);
            projectedSource=rawTargets;projectedZoom=zoom;projectedMinimum=minimum;projectionRevision=ClientState.definitionRevision();
        }
        var targets=projectedTargets;
        boolean enabled=com.cappleapple.mastery.config.MasteryClientConfig.ANIMATIONS.get()&&!dragging&&!ClientState.editMode();
        animation.update(targets,id->transitionOrigin(id,targets),net.minecraft.Util.getMillis(),enabled,com.cappleapple.mastery.config.MasteryClientConfig.ANIMATION_SPEED.get());
        animationPositions=animation.frame(net.minecraft.Util.getMillis());
        // Separate destinations only; both transition directions may overlap in flight.
        displayPositions=animationPositions;
        edges=edges.stream().filter(e->displayPositions.containsKey(e.edge().from())&&displayPositions.containsKey(e.edge().to())).toList();
        animatedNodes.keySet().removeIf(id->!displayPositions.containsKey(id)&&!targetVisible.contains(id));
    }
    private void refreshEdgePositions() {
        Map<String, GraphLayout.Point> positions = ClientState.layout().anchors();
        edges = edges.stream().map(e -> new RenderEdge(e.edge(), positions.get(e.edge().from()), positions.get(e.edge().to()))).toList();
    }
    private void rebuildDetailControls() {
        detailWidgets.forEach(this::removeWidget); detailWidgets.clear();
        if (detailWidth == 0) return;
        if (ClientState.editMode()) {
            String selected = ClientState.view().selected;
            if (!ClientState.entries().containsKey(selected)) return;
            int x = detailX + 6, w = detailWidth - 12;
            actionButton("Edit definition", x, bottom - 70, w, () -> EditorClient.edit(this, selected), true);
            actionButton("Add child", x, bottom - 47, w, () -> EditorClient.addChild(this, selected), true);
            actionButton("Delete...", x, bottom - 24, w, () -> EditorClient.delete(this, selected), true);
            return;
        }
        NodeDefinition node = ClientState.definitions().nodes().get(ClientState.view().selected);
        if (node == null) return;
        int x = detailX + 6, w = detailWidth - 12;
        int y = bottom - 24;
        int rank = ClientState.progress().rank(node.id());
        var rewards=NodeDetails.spells(node);
        if (!rewards.isEmpty() && rank>0) {
            actionButton("Spell bindings",x,y,w,()->{
                if(rewards.size()==1)minecraft.setScreen(new SpellBindingsScreen(this,rewards.getFirst()));
                else minecraft.setScreen(new EditorChoiceScreen(this,"Choose a spell to bind",rewards,spell->minecraft.setScreen(new SpellBindingsScreen(this,spell))));
            },true);y-=23;
        }
        if (rank > 0) {
            boolean active = ClientState.nodeEnabled(node.id());
            actionButton(active ? "Disable node" : "Enable node", x, y, w,
                    () -> MasteryNetwork.sendAction("toggle", node.id(), "", active ? 0 : 1), rank > 0);
            y -= 23;
        }

    }
    private static String modifierSpell(NodeDefinition node) {
        if (!node.spell().isBlank()) return node.spell();
        for (Dependency dep : node.dependencyLeaves()) {
            NodeDefinition parent = ClientState.definitions().nodes().get(dep.node());
            if (parent != null && !parent.spell().isBlank()) return parent.spell();
        }
        return "";
    }
    private void actionButton(String text, int x, int y, int w, Runnable action, boolean enabled) {
        Button button = Button.builder(Component.literal(text), b -> action.run()).bounds(x, y, w, 20).build();
        button.active = enabled;
        addRenderableWidget(button); detailWidgets.add(button);
    }

    private void refreshSnapshot() {
        if (cachedEditMode != ClientState.editMode()) {
            lastSearch = search.getValue(); targetX = targetY = null;
            clearWidgets(); init(); return;
        }
        if (!cachedEditorStatus.equals(EditorClient.status())) calculateBounds();
        if (cachedStructureRevision != ClientState.structureRevision() || !targetVisible.equals(ClientState.visible())) rebuild();
        else if (cachedRevision != ClientState.revision()) refreshDetails();
    }
    @Override public void tick() {
        refreshSnapshot();
        tickUnlock();
    }
    private void animateCamera() {
        long now=net.minecraft.Util.getMillis();
        double elapsed=lastCameraFrame==0?0:Math.max(0,now-lastCameraFrame);
        lastCameraFrame=now;
        if (targetX != null && targetY != null) {
            var view = ClientState.view();
            double blend=1-Math.pow(.68,elapsed/50.0);
            view.panX += (targetX - view.panX) * blend;
            view.panY += (targetY - view.panY) * blend;
            if (Math.abs(targetX - view.panX) + Math.abs(targetY - view.panY) < 0.4) {
                view.panX = targetX; view.panY = targetY; targetX = targetY = null;
            }
        }
    }
    private UnlockPresentation unlockSettings(String id) {
        return UnlockPresentation.parse(SettingsResolver.forNode(ClientState.definitions(),id).getAsJsonObject("unlock"));
    }
    private boolean canHold(String id) {
        return !ClientState.editMode() && ClientState.definitions().nodes().containsKey(id)
                && com.cappleapple.mastery.progression.ProgressionService.purchaseBlockReason(ClientState.definitions(),ClientState.progress(),id,
                    ClientState.worldTier(),requirement->true,false).isEmpty()
                && minecraft.player!=null&&com.cappleapple.mastery.costs.CostService.affordable(ClientState.definitions(),ClientState.progress(),id,minecraft.player);
    }
    private void playUnlockSound(String id,float pitch) {
        if(id.equals("none"))return;
        var location=ResourceLocation.tryParse(id);
        if(location!=null&&BuiltInRegistries.SOUND_EVENT.containsKey(location))
            minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(BuiltInRegistries.SOUND_EVENT.get(location),pitch,.55F));
    }
    private void tickUnlock() {
        if(!pendingPurchase.isBlank()) {
            if(ClientState.progress().rank(pendingPurchase)>pendingRank) {
                playUnlockSound(pendingSounds.completeSound(),1);pendingPurchase="";
            } else if(++pendingTicks>100) pendingPurchase="";
        }
        if(pressedNode.isBlank())return;
        pressTicks++;
        if(ClientState.editMode()||dragging||!canHold(pressedNode)) {hold.cancel();return;}
        if(!hold.active()||purchaseSent)return;
        int ding=hold.advance();
        if(ding>=0)playUnlockSound(unlockSettings(pressedNode).progressSound(),.8F+ding*.1F);
        if(hold.ready()) {
            purchaseSent=true;pendingPurchase=pressedNode;pendingRank=ClientState.progress().rank(pressedNode);pendingTicks=0;
            pendingSounds=unlockSettings(pressedNode);
            MasteryNetwork.sendAction("purchase",pressedNode,"",0);
        }
    }
    private boolean expansionHit(String id,double x,double y) {
        var anchor=ClientState.layout().anchors().get(id);
        return ClientState.expandable(id)&&anchor!=null&&graphX(x)>=anchor.x()-NODE_W/2.0+5&&graphX(x)<=anchor.x()-NODE_W/2.0+14
                &&graphY(y)>=anchor.y()-NODE_H/2.0+5&&graphY(y)<=anchor.y()-NODE_H/2.0+14;
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The opaque, resource-packable canvas is already drawn before widgets. Do not blur it.
    }
    @Override public void removed() {
        hold.cancel(); pressedNode="";
        lastSearch = search == null ? "" : search.getValue();
        ClientState.view().save();
        super.removed();
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Network definitions can change after tick but before this frame (for example an editor deletion).
        refreshSnapshot();
        graphics.blitSprite(sprite("panel"), 0, 0, width, height);
        graphics.drawString(font, ClientState.editMode() ? Component.literal("Mastery | Editor") : title, 13, 15, ClientState.editMode() ? 0xFFE2B3 : 0xFFFFFF);
        graphics.blitSprite(sprite("background"), left, top, right - left, bottom - top);
        graphics.enableScissor(left, top, right, bottom);
        animateCamera();
        var view = ClientState.view();
        double originX = (left + right) / 2.0 + view.panX, originY = (top + bottom) / 2.0 + view.panY;
        double nodeScale=nodeScale();
        double minX = (left - originX) / view.zoom - NODE_W*nodeScale*16, maxX = (right - originX) / view.zoom + NODE_W*nodeScale*16;
        double minY = (top - originY) / view.zoom - NODE_H*nodeScale*16, maxY = (bottom - originY) / view.zoom + NODE_H*nodeScale*16;
        updateAnimation();
        hovered = inCanvas(mouseX, mouseY) ? hit(mouseX, mouseY) : "";
        graphics.pose().pushPose();
        graphics.pose().translate(originX, originY, 0);
        graphics.pose().scale((float)view.zoom, (float)view.zoom, 1);
        if (view.showSectors) renderSectorGuides(graphics, minX, minY, maxX, maxY, view.zoom);
        for (RenderEdge stored : edges) {
            var from=displayPositions.get(stored.edge().from());var to=displayPositions.get(stored.edge().to());
            if(from==null||to==null)continue;
            RenderEdge edge=new RenderEdge(stored.edge(),from,to);
            if (!GraphLayout.edgeInViewport(edge.from(), edge.to(), minX, minY, maxX, maxY)) continue;
            var target = ClientState.entries().get(edge.edge().to());
            boolean related = relevantTrees.isEmpty() || target != null && (relevantTrees.contains(target.tree()) || target.relatedTrees().stream().anyMatch(relevantTrees::contains));
            float alpha = related ? 0.9F : 0.18F;
            if (!ClientState.editMode() && edge.edge().synergy() && ClientState.progress().rank(edge.edge().to()) == 0) alpha *= 0.5F;

            graphics.pose().pushPose();
            graphics.pose().translate(edge.from().x(), edge.from().y(), 0);
            double dx = edge.to().x() - edge.from().x(), dy = edge.to().y() - edge.from().y();
            graphics.pose().mulPose(Axis.ZP.rotation((float)Math.atan2(dy, dx)));
            graphics.pose().scale((float)(1/view.zoom),(float)(1/view.zoom),1);
            int length=(int)(Math.hypot(dx,dy)*view.zoom), radius=edge.edge().synergy()?4:3;
            TreeTheme theme=theme(edge.edge().to());
            boolean dashed=connectionStyle(edge.edge())==ConnectionPresentation.Style.DASHED;
            for(int band=-radius;band<=radius;band++) {
                int color=ThemePalette.color(theme,1-Math.abs(band)/(double)radius,nodeState(edge.edge().to()),alpha);
                if(dashed) for(int segment=0;segment<length;segment+=14)
                    NodeShapes.rectangle(graphics,segment,band,Math.min(length,segment+10),band+1,color);
                else NodeShapes.rectangle(graphics,0,band,length,band+1,color);
            }
            graphics.pose().popPose();
        }
        graphics.flush();
        graphics.setColor(1, 1, 1, 1);
        for (RenderNode node : animatedNodes.values()) {
            GraphLayout.Point p = displayPositions.get(node.id());
            if (p == null || p.x() < minX || p.x() > maxX || p.y() < minY || p.y() > maxY) continue;
            graphics.pose().pushPose();
            graphics.pose().translate(p.x(),p.y(),0);
            graphics.pose().scale((float)nodeScale,(float)nodeScale,1);
            renderNode(graphics, node, new GraphLayout.Point(0,0));
            graphics.pose().popPose();
        }
        graphics.pose().popPose();
        if (nodes.isEmpty()) {
            graphics.drawCenteredString(font, "Your mastery begins with practice.", (left + right) / 2, (top + bottom) / 2 - 13, 0xDDDDDD);
            graphics.drawCenteredString(font, "Earn a specialization point to reveal its tree.", (left + right) / 2, (top + bottom) / 2 + 2, 0xAAAAAA);
        }
        graphics.disableScissor();
        if (detailWidth > 0) renderDetails(graphics);
        for (int line = 0; line < Math.min(3, footerLines.size()); line++)
            graphics.drawString(font, footerLines.get(line), 9, bottom + 7 + line * 12,
                    ClientState.editMode() && EditorClient.failed() ? 0xFF9999 : 0xBBBBBB);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (contextMenu == null && !hovered.isBlank() && !dragging ) {
            if (!hovered.equals(tooltipId) || tooltipRevision != ClientState.revision()) {
                tooltipId = hovered; tooltipRevision = ClientState.revision();
                hoverTooltip = NodeDetails.tooltip(hovered);
            }
            graphics.renderTooltip(font, hoverTooltip, Optional.empty(), mouseX, mouseY);
        }
        if (contextMenu != null) contextMenu.render(graphics, font, mouseX, mouseY);
    }

    private void toggleSectors() {
        ClientState.view().showSectors = !ClientState.view().showSectors;
        ClientState.view().save();
        lastSearch = search.getValue(); clearWidgets(); init();
    }
    private void renderSectorGuides(GuiGraphics graphics, double minX, double minY, double maxX, double maxY, double zoom) {
        double radius = Math.hypot(Math.max(Math.abs(minX), Math.abs(maxX)), Math.max(Math.abs(minY), Math.abs(maxY))) + 24;
        int thickness = Math.max(1, (int)Math.ceil(1 / zoom));
        for (int sector = 0; sector < 8; sector++) {
            graphics.pose().pushPose();
            graphics.pose().mulPose(Axis.ZP.rotation((float)(Math.PI / 8 + sector * Math.PI / 4)));
            graphics.fill(0, -thickness / 2, (int)Math.ceil(radius), thickness, 0x665B594E);
            graphics.pose().popPose();
        }
        graphics.fill(-3, -3, 4, 4, 0xCCAB9C74);
    }
    private double holdVisualProgress() {
        return Math.clamp((net.minecraft.Util.getMillis()-holdStartedAt-holdDelayMs)/1000.0,0,1);
    }
    private void renderNode(GuiGraphics graphics, RenderNode node, GraphLayout.Point p) {
        if(node.entry().kind()==GraphLayout.Kind.TREE?!ClientState.definitions().trees().containsKey(node.id()):!ClientState.definitions().nodes().containsKey(node.id()))return;
        graphics.pose().pushPose();
        double holdProgress=holdVisualProgress();
        boolean charging=node.id().equals(pressedNode)&&hold.active()&&holdProgress>0&&!purchaseSent;
        if(charging) {
            double amplitude=.4+holdProgress*1.6,time=net.minecraft.Util.getMillis()/35.0;
            graphics.pose().translate(Math.sin(time)*amplitude,Math.cos(time*1.3)*amplitude*.5,0);
        }
        var state=nodeState(node.id());var colors=theme(node.id());var look=appearance(node.id());var shape=look.shape();
        float size=(float)appearanceScale(node.id());graphics.pose().scale(size,size,1);
        if(node.id().equals(ClientState.view().selected))NodeShapes.fill(graphics,shape,26,ThemePalette.color(colors,1,state,1));
        else if(node.id().equals(hovered))NodeShapes.fill(graphics,shape,25,ThemePalette.color(colors,1,state,.7));
        for(int ring=0;ring<4;ring++)NodeShapes.fill(graphics,shape,24-ring,ThemePalette.color(colors,ring/3.0,state,1));
        int inner=switch(node.entry().kind()) {
            case TREE -> 0xD3AD5B;
            default -> switch(ClientState.definitions().nodes().get(node.id()).type().name()) {
                case "ACTIVE" -> 0x99B3C6;case "PASSIVE" -> 0x99A278;case "MODIFIER" -> 0x9E84AC;
                case "SYNERGY" -> 0xC6804D;case "KEYSTONE" -> 0xD7A94C;default -> 0x8BAA99;
            };
        };
        if(state==ThemePalette.State.LOCKED)inner=0x888888;
        else if(state==ThemePalette.State.DISABLED)inner=((int)(((inner>>16)&255)*.4)<<16)|((int)(((inner>>8)&255)*.4)<<8)|(int)((inner&255)*.4);
        NodeShapes.fill(graphics,shape,20,0xFF000000|inner);
        NodeShapes.fill(graphics,shape,17,0xFF2F2B24);
        String experienceTree=PromotedTrees.displayTree(ClientState.definitions(),node.id());
        if(!experienceTree.isBlank()&&!ClientState.editMode()) {
            var tree=ClientState.definitions().trees().get(experienceTree);var progress=ClientState.progress().trees().get(experienceTree);
            int level=progress==null?0:progress.level();double xp=progress==null?0:progress.xp();
            NodeShapes.outlineProgress(graphics,shape,20,3,0xFF574D39,1);
            NodeShapes.outlineProgress(graphics,shape,20,3,0xFFFFE394,com.cappleapple.mastery.layout.ShapeOutline.experience(xp,tree.xpForLevel(level),level,tree.levelCap(ClientState.worldTier())));
        }
        graphics.flush(); // Submit every border span together before drawing the icon.
        int iconY=-8+shape.iconOffsetY(17);
        if(!node.icon().isEmpty())graphics.renderItem(node.icon(),-8,iconY);
        else if(node.customIcon()!=null)graphics.blit(node.customIcon(),-8,iconY,0,0,16,16,16,16);
        else graphics.blitSprite(sprite("rune"),-8,iconY,16,16);
        if(charging) {
            NodeShapes.fillProgress(graphics,shape,17,ThemePalette.color(colors,.8,ThemePalette.State.ENABLED,.38),holdProgress,unlockSettings(node.id()).fillDirection().equals("horizontal"));
            graphics.flush();
        }
        if(!experienceTree.isBlank()&&!ClientState.editMode()) {
            var progress=ClientState.progress().trees().get(experienceTree);String points=Integer.toString(progress==null?0:progress.points());
            int half=font.width(points)/2;graphics.fill(-half-3,-34,half+4,-22,0xDD211E18);graphics.drawCenteredString(font,points,0,-32,0xFFFFDC88);
        }
        if(look.showName())graphics.drawCenteredString(font,node.label(),0,28,state==ThemePalette.State.ENABLED?0xF5EBD0:0x888888);
        graphics.pose().popPose();
    }
    private double appearanceScale(String id) {
        return appearance(id).scaleFor(!PromotedTrees.displayTree(ClientState.definitions(),id).isBlank());
    }
    private com.cappleapple.mastery.data.NodeAppearance appearance(String id) {
        refreshPresentation();
        return resolvedAppearances.computeIfAbsent(id,key->NodeAppearance.parse(SettingsResolver.forNode(ClientState.definitions(),key).getAsJsonObject("appearance")));
    }
    private void refreshPresentation(){if(themeRevision!=ClientState.definitionRevision()){resolvedThemes.clear();resolvedAppearances.clear();resolvedConnections.clear();themeRevision=ClientState.definitionRevision();}}
    private ConnectionPresentation.Style connectionStyle(GraphLayout.Edge edge) {
        refreshPresentation();
        String owner=edge.synergy()?edge.to():edge.from();
        var settings=resolvedConnections.computeIfAbsent(owner,id->ConnectionPresentation.parse(SettingsResolver.forNode(ClientState.definitions(),id).getAsJsonObject("connections")));
        return edge.synergy()?settings.parentLineStyle():settings.childLineStyle();
    }
    private TreeTheme theme(String id) {
        refreshPresentation();
        return resolvedThemes.computeIfAbsent(id,key->TreeTheme.parse(SettingsResolver.forNode(ClientState.definitions(),key).getAsJsonObject("theme")));
    }
    private ThemePalette.State nodeState(String id) {
        if(ClientState.editMode()||ClientState.definitions().trees().containsKey(id)) return ThemePalette.State.ENABLED;
        if(ClientState.progress().rank(id)==0) return ThemePalette.State.LOCKED;
        return ClientState.nodeEnabled(id)?ThemePalette.State.ENABLED:ThemePalette.State.DISABLED;
    }
    private void activate(String id,boolean expand) {
        ClientState.view().selected=id; detailsScroll=0;
        if(ClientState.editMode()) return;
        if(expand&&ClientState.expandable(id)&&!ClientState.view().expanded.add(id))ClientState.view().expanded.remove(id);
    }
    private void renderDetails(GuiGraphics graphics) {
        graphics.blitSprite(sprite("panel"), detailX, top, detailWidth, bottom - top);
        int controlsTop = detailWidgets.stream().mapToInt(AbstractWidget::getY).min().orElse(bottom - 6);
        graphics.enableScissor(detailX + 5, top + 7, width - 12, controlsTop - 5);
        int y = top + 10 - detailsScroll;
        for (FormattedCharSequence line : detailLines) {
            graphics.drawString(font, line, detailX + 8, y, 0xDDDDDD); y += 12;
        }
        graphics.disableScissor();
    }

    private boolean inCanvas(double x, double y) { return x >= left && x < right && y >= top && y < bottom; }
    private double graphX(double screenX) { return (screenX - (left + right) / 2.0 - ClientState.view().panX) / ClientState.view().zoom; }
    private double graphY(double screenY) { return (screenY - (top + bottom) / 2.0 - ClientState.view().panY) / ClientState.view().zoom; }
    private double nodeScale() {
        return Math.max(1,com.cappleapple.mastery.config.MasteryClientConfig.MINIMUM_NODE_WIDTH.get()/40.0/ClientState.view().zoom);
    }
    private String hit(double mx, double my) {
        double gx = graphX(mx), gy = graphY(my);
        for (int i = nodes.size() - 1; i >= 0; i--) {
            String id = nodes.get(i).id();
            GraphLayout.Point p = displayPositions.getOrDefault(id,ClientState.layout().anchors().get(id));
            if (p != null && appearance(id).shape().contains((gx-p.x())/(nodeScale()*appearanceScale(id)),(gy-p.y())/(nodeScale()*appearanceScale(id)),24)) return id;
        }
        return "";
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (contextMenu != null) {
            EditorContextMenu menu = contextMenu; contextMenu = null;
            menu.click(x, y, button); return true;
        }
        if (ClientState.editMode() && button == 1 && inCanvas(x, y)) {
            openContextMenu(x, y); return true;
        }
        if (!ClientState.editMode() && button==1 && inCanvas(x,y)) {
            String id=hit(x,y); if(!id.isBlank()) { activate(id,true); rebuild(); return true; }
        }
        if(!ClientState.editMode()&&button==2&&inCanvas(x,y)) {
            String id=hit(x,y);
            if(!id.isBlank()) {
                hold.cancel();pressedNode="";panning=false;
                if(ClientState.progress().rank(id)>0)MasteryNetwork.sendAction("toggle",id,"",ClientState.nodeEnabled(id)?0:1);
                return true;
            }
        }
        if (super.mouseClicked(x, y, button)) return true;
        if (inCanvas(x, y) && (button == 0 || button == 2)) {
            targetX = targetY = null; pressX = x; pressY = y; dragging = false;
            pressedNode = button == 0 ? hit(x, y) : "";
            panning = pressedNode.isBlank();
            pressTicks=0;purchaseSent=false;hold.cancel();
            if(!pressedNode.isBlank()&&pendingPurchase.isBlank()&&canHold(pressedNode)) {
                holdDelayMs=unlockSettings(pressedNode).holdDelayMs();
                holdStartedAt=net.minecraft.Util.getMillis();
                hold.start(holdDelayMs);
            }
            search.setFocused(false);
            return true;
        }
        return false;
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if ((button == 0 || button == 2) && (panning || !pressedNode.isBlank())) {
            if (!dragging && Math.hypot(x - pressX, y - pressY) < 4) return true;
            if(!dragging&&!pressedNode.isBlank()&&displayPositions.containsKey(pressedNode)) {
                // Start dragging from the visible bridge position, not its collapsed storage anchor.
                ClientState.view().anchors.put(pressedNode,displayTargets().getOrDefault(pressedNode,ClientState.layout().anchors().get(pressedNode)));
            }
            dragging = true; hold.cancel();
            if (panning) { ClientState.view().panX += dx; ClientState.view().panY += dy; }
            else if (!ClientState.editMode()) { ClientState.moveSubtree(pressedNode, dx / ClientState.view().zoom, dy / ClientState.view().zoom); refreshEdgePositions(); }
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 || button == 2) {
            if (!pressedNode.isBlank()) {
                if (dragging && !ClientState.editMode()) {
                    var entry = ClientState.entries().get(pressedNode);
                    if (entry != null && !PromotedTrees.displayTree(ClientState.definitions(),pressedNode).isBlank()) {
                        GraphLayout.Point anchor = ClientState.layout().anchors().get(pressedNode);
                        String side = GraphLayout.sectionAt(anchor.x(), anchor.y());
                        ClientState.orientTree(pressedNode, side);
                    }
                } else {
                    if(!purchaseSent&&pressTicks<5) activate(pressedNode,false);
                    else ClientState.view().selected=pressedNode;
                }
                rebuild();
            }
            panning = dragging = false; pressedNode = ""; hold.cancel();
        }
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (detailWidth > 0 && x >= detailX && y >= top && y < bottom) {
            detailsScroll = Math.max(0, Math.min(Math.max(0, detailLines.size() * 12 - 100), detailsScroll - (int)(dy * 20)));
            return true;
        }
        if (inCanvas(x, y)) {
            hold.cancel();
            targetX = targetY = null;
            double gx = graphX(x), gy = graphY(y);
            var view = ClientState.view();
            view.zoom = Math.max(com.cappleapple.mastery.layout.ReadableZoom.MIN_ZOOM, Math.min(2.5, view.zoom * Math.pow(1.12, dy)));
            view.panX = x - (left + right) / 2.0 - gx * view.zoom;
            view.panY = y - (top + bottom) / 2.0 - gy * view.zoom;
            return true;
        }
        return super.mouseScrolled(x, y, dx, dy);
    }
    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (contextMenu != null) {
            EditorContextMenu menu = contextMenu;
            if (menu.key(keyCode)) contextMenu = null;
            return true;
        }
        if (search.isFocused() && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) { find(); return true; }
        if (!search.isFocused() && MasteryClient.FOCUS_NODE.matches(keyCode, scanCode)) { center(ClientState.view().selected); return true; }
        if (!search.isFocused() && keyCode == GLFW.GLFW_KEY_HOME) { resetView(); return true; }
        if (!search.isFocused() && keyCode == GLFW.GLFW_KEY_ENTER && !ClientState.view().selected.isBlank()) {
            String id = ClientState.view().selected;
            activate(id,hasShiftDown()); rebuild(); return true;
        }
        if (!search.isFocused() && List.of(GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN).contains(keyCode)) {
            navigate(keyCode); return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    private void openContextMenu(double x, double y) {
        String id = hit(x, y);
        List<EditorContextMenu.Action> actions = new ArrayList<>();
        if (!id.isBlank()) {
            ClientState.view().selected = id; detailsScroll = 0; rebuild();
            actions.add(new EditorContextMenu.Action("Edit definition", () -> EditorClient.edit(this, id), false));
            actions.add(new EditorContextMenu.Action("Add child", () -> EditorClient.addChild(this, id), false));
            var spellNode=ClientState.definitions().nodes().get(id);
            var rewards=spellNode!=null&&!spellNode.spell().isBlank()&&spellNode.spellModifier()?List.of(spellNode.spell()):NodeDetails.spells(spellNode);
            if(!rewards.isEmpty())actions.add(new EditorContextMenu.Action("Edit spell upgrades",()->{
                if(rewards.size()==1)EditorClient.editSpell(this,rewards.getFirst());
                else minecraft.setScreen(new EditorChoiceScreen(this,"Choose spell upgrades",rewards,spell->EditorClient.editSpell(this,spell)));
            },false));
        }
        actions.add(new EditorContextMenu.Action("Add tree", () -> EditorClient.addTree(this), false));
        if (!id.isBlank()) actions.add(new EditorContextMenu.Action("Delete...", () -> EditorClient.delete(this, id), true));
        contextMenu = new EditorContextMenu((int)x, (int)y, width, height, actions);
        search.setFocused(false); targetX = targetY = null; pressedNode = ""; panning = dragging = false;
    }
    private void fitMap() {
        var points = ClientState.layout().anchors().values();
        if (points.isEmpty()) return;
        double minX = points.stream().mapToDouble(GraphLayout.Point::x).min().orElse(0);
        double maxX = points.stream().mapToDouble(GraphLayout.Point::x).max().orElse(0);
        double minY = points.stream().mapToDouble(GraphLayout.Point::y).min().orElse(0);
        double maxY = points.stream().mapToDouble(GraphLayout.Point::y).max().orElse(0);
        ClientState.view().zoom = Math.max(com.cappleapple.mastery.layout.ReadableZoom.MIN_ZOOM, Math.min(1.1, Math.min((right - left - 24) / (maxX - minX + NODE_W), (bottom - top - 24) / (maxY - minY + NODE_H))));
        targetX = -(minX + maxX) / 2 * ClientState.view().zoom;
        targetY = -(minY + maxY) / 2 * ClientState.view().zoom;
    }
    private void navigate(int key) {
        GraphLayout.Point from = ClientState.layout().anchors().getOrDefault(ClientState.view().selected, new GraphLayout.Point(0, 0));
        RenderNode best = null; double score = Double.MAX_VALUE;
        for (RenderNode node : nodes) {
            GraphLayout.Point p = ClientState.layout().anchors().get(node.id());
            if (p == null) continue;
            double dx = p.x() - from.x(), dy = p.y() - from.y();
            boolean valid = switch(key) { case GLFW.GLFW_KEY_LEFT -> dx < -1; case GLFW.GLFW_KEY_RIGHT -> dx > 1; case GLFW.GLFW_KEY_UP -> dy < -1; default -> dy > 1; };
            if (valid && Math.hypot(dx, dy) < score) { best = node; score = Math.hypot(dx, dy); }
        }
        if (best != null) { ClientState.view().selected = best.id(); rebuild(); center(best.id()); narrate(Component.literal(ClientState.name(best.id()))); }
    }
    private void narrate(Component text) { if (minecraft != null) minecraft.getNarrator().sayNow(text); }
    private void find() {
        String query = search.getValue().strip().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) return;
        var result = ClientState.entries().keySet().stream().filter(id -> ClientState.name(id).toLowerCase(Locale.ROOT).contains(query) || id.contains(query)).sorted().findFirst();
        if (result.isPresent()) {
            String id = result.get(); ClientState.reveal(id); ClientState.view().selected = id;
            searchNotice = ""; rebuild(); center(id); narrate(Component.literal(ClientState.name(id)));
        } else { searchNotice = "No matching discovered skill."; calculateBounds(); }
    }
    private void center(String id) {
        updateAnimation();
        GraphLayout.Point p = displayPositions.getOrDefault(id,ClientState.layout().anchors().get(id));
        if (p != null) { targetX = -p.x() * ClientState.view().zoom; targetY = -p.y() * ClientState.view().zoom; }
    }
    private void resetView() {
        ClientState.view().zoom = 0.85;
        List<GraphLayout.Point> roots = ClientState.entries().values().stream().filter(e -> e.kind() == GraphLayout.Kind.TREE).map(e -> ClientState.layout().anchors().get(e.id())).toList();
        targetX = -roots.stream().mapToDouble(GraphLayout.Point::x).average().orElse(0) * ClientState.view().zoom;
        targetY = -roots.stream().mapToDouble(GraphLayout.Point::y).average().orElse(0) * ClientState.view().zoom + (bottom - top) * 0.24;
    }
    private void showInvested() {
        ClientState.view().expanded.clear();
        ClientState.progress().nodes().forEach((id, state) -> { if (state.rank() > 0) { ClientState.reveal(id); ClientState.view().expanded.add(id); } });
        ClientState.progress().trees().forEach((id, state) -> { if (state.discovered()) ClientState.view().expanded.add(id); });
        rebuild();
    }
}
