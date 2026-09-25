package com.rspsi.studio.ui.panels;

import com.rspsi.cache.workspace.LoadedOsrsCacheSession;
import com.rspsi.editor.knowledge.Evidence;
import com.rspsi.editor.knowledge.KnowledgeFact;
import com.rspsi.editor.knowledge.KnowledgeSnapshot;
import com.rspsi.editor.knowledge.MetricKey;
import com.rspsi.editor.knowledge.RegionProfile;
import com.rspsi.editor.knowledge.SemanticTag;
import com.rspsi.editor.knowledge.WorldKnowledgeService;
import com.rspsi.editor.model.LocalTile;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.model.WorldTile;
import com.rspsi.editor.render.PickResult;
import com.rspsi.editor.ui.DockRegion;
import com.rspsi.osrs.rules.RuleTrace;
import com.rspsi.studio.theme.StudioFonts;
import com.rspsi.studio.theme.StudioPalette;
import com.rspsi.studio.theme.StudioWidgets;
import com.rspsi.studio.ui.StudioPanel;
import com.rspsi.studio.ui.StudioPanelContext;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImString;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** World Knowledge, semantic classification, and rule-trace panel. */
public final class KnowledgePanel implements StudioPanel {
    public static final String ID = "studio.knowledge";
    private final ImString customTagInput = new ImString(32);

    @Override public String id() { return ID; }
    @Override public String title() { return "World Knowledge"; }
    @Override public String icon() { return com.rspsi.studio.theme.StudioIcons.INFO; }
    @Override public DockRegion preferredRegion() { return DockRegion.RIGHT; }
    @Override public Set<DockRegion> allowedRegions() {
        return EnumSet.of(DockRegion.RIGHT, DockRegion.BOTTOM);
    }
    @Override public int order() { return 35; }

    @Override
    public float preferredRightSidebarWidth() {
        return 420.0f;
    }

    @Override
    public void render(StudioPanelContext context) {
        if (context.pluginLifecycle() == null || context.pluginLifecycle().host() == null) {
            ImGui.textDisabled("Plugin host unavailable.");
            return;
        }

        WorldKnowledgeService knowledge = context.pluginLifecycle().host().context().knowledge();
        if (knowledge == null) {
            ImGui.textDisabled("Knowledge service uninitialized.");
            return;
        }

        KnowledgeSnapshot snapshot = knowledge.snapshot();

        if (ImGui.collapsingHeader("Region & World Intelligence", ImGuiTreeNodeFlags.DefaultOpen)) {
            renderRegionProfile(snapshot.worldProfile());
        }

        Optional<PickResult> picked = context.viewport() != null
                ? context.viewport().selection() : Optional.empty();

        if (picked.isEmpty()) {
            ImGui.dummy(1.0f, 6.0f);
            ImGui.textDisabled(
                    "Select or pick a tile in the viewport to inspect semantic knowledge and rule traces.");
            return;
        }

        PickResult hit = picked.get();
        WorldTile worldCoord = hit.tile();
        LocalTile local = context.session() == null ? null
                : context.session().coordinates().toLocal(worldCoord).orElse(null);

        StudioWidgets.section("Selection Semantics");
        if (StudioWidgets.beginPropertyTable("knowledge-selection")) {
            StudioWidgets.propertyRowMono("Selected tile",
                    worldCoord.plane() + " / " + worldCoord.x() + " / " + worldCoord.y());
            StudioWidgets.endPropertyTable();
        }

        if (local == null) {
            ImGui.textDisabled("Selected tile is outside the active document.");
            return;
        }
        TileCoordinate coord = local.coordinate();
        WorldDocument world = context.pluginLifecycle().host().context().world();
        WorldObject pickedObject = resolvePickedObject(context, world, hit, worldCoord, coord);

        snapshot.topologyAt(coord).ifPresent(topo -> {
            if (StudioWidgets.beginPropertyTable("knowledge-topology")) {
                StudioWidgets.propertyRow("Slope", String.format("%.1f°", topo.slopeMagnitude()));
                StudioWidgets.propertyRow("Aspect", topo.aspect().toString());
                StudioWidgets.propertyRow("Curvature", topo.curvature().toString());
                StudioWidgets.propertyRow("Classification", topo.isCliff() ? "Cliff" : "Traversable");
                StudioWidgets.endPropertyTable();
            }
        });

        Set<SemanticTag> tags = snapshot.tagsAt(coord);
        ImGui.dummy(1.0f, 6.0f);
        ImGui.textDisabled("Semantic tags");
        if (tags.isEmpty()) {
            ImGui.textDisabled("No semantic tags active on this tile.");
        } else {
            for (SemanticTag tag : tags) {
                StudioWidgets.pill(
                        tag.qualifiedName(),
                        0xFF183550,
                        StudioPalette.ACCENT_HOVER);
                ImGui.sameLine();
            }
            ImGui.newLine();
        }

        if (ImGui.collapsingHeader("Explain Classification", ImGuiTreeNodeFlags.DefaultOpen)) {
            List<KnowledgeFact<SemanticTag>> facts = snapshot.factsAt(coord);
            List<KnowledgeFact<SemanticTag>> objectFacts = pickedObject == null
                    ? List.of() : knowledge.classifyObject(pickedObject);
            if (facts.isEmpty() && objectFacts.isEmpty()) {
                ImGui.textDisabled("No inferred classifications active.");
            } else {
                facts.forEach(KnowledgePanel::renderFact);
                objectFacts.forEach(KnowledgePanel::renderFact);
            }
        }

        if (ImGui.collapsingHeader("Explain Rendering", ImGuiTreeNodeFlags.DefaultOpen)
                && world != null && coord.plane() >= 0 && coord.plane() < world.planes()) {
            renderRuleTrace(world, coord, pickedObject, context.cache());
        }

        if (ImGui.collapsingHeader("User Metadata Overrides")) {
            ImGui.setNextItemWidth(Math.max(140.0f, ImGui.getContentRegionAvailX() - 92.0f));
            ImGui.inputTextWithHint("##custom-tag", "core:SPAWN", customTagInput);
            ImGui.sameLine();
            if (ImGui.button("Add##add-user-tag") && !customTagInput.get().isBlank()) {
                knowledge.userOverrides().addTileTag(coord, SemanticTag.of(customTagInput.get()));
                knowledge.invalidate();
                customTagInput.set("");
            }
        }
    }

    private static void renderRegionProfile(RegionProfile profile) {
        if (!StudioWidgets.beginPropertyTable("knowledge-region")) return;
        StudioWidgets.propertyRow("Tiles",
                profile.tileCount() + " across " + profile.planes() + " planes");
        StudioWidgets.propertyRow("Dominant floor",
                "Underlay #" + profile.dominantUnderlay().orElse(-1)
                        + " · Overlay #" + profile.dominantOverlay().orElse(-1));
        StudioWidgets.propertyRow("Elevation",
                "Min " + profile.minHeight()
                        + " · Max " + profile.maxHeight()
                        + " · Avg " + String.format("%.1f", profile.averageHeight()));
        profile.metric(MetricKey.MAX_SLOPE).ifPresent(value ->
                StudioWidgets.propertyRow("Steepest slope", String.format("%.1f°", value)));
        profile.metric(MetricKey.WALKABLE_RATIO).ifPresent(value ->
                StudioWidgets.propertyRow("Walkable space", String.format("%.1f%%", value * 100)));
        StudioWidgets.endPropertyTable();
    }

    private static WorldObject resolvePickedObject(
            StudioPanelContext context,
            WorldDocument world,
            PickResult hit,
            WorldTile worldCoord,
            TileCoordinate fallback) {
        if (!hit.objectHit() || world == null || context.session() == null) return null;
        WorldTile objTile = hit.objectTile() != null ? hit.objectTile() : worldCoord;
        LocalTile objLocal = context.session().coordinates().toLocal(objTile).orElse(null);
        TileCoordinate objCoord = objLocal != null ? objLocal.coordinate() : fallback;
        if (objCoord.plane() < 0 || objCoord.plane() >= world.planes() || !world.contains(objCoord)) {
            return null;
        }
        return world.tile(objCoord).snapshot().objects().stream()
                .filter(obj -> obj.id() == hit.objectId())
                .findFirst()
                .orElse(null);
    }

    private static void renderFact(KnowledgeFact<SemanticTag> fact) {
        ImGui.pushID("knowledge-fact-" + fact.value().qualifiedName() + "-" + fact.source());
        ImGui.textColored(StudioPalette.ACCENT, fact.value().qualifiedName());
        ImGui.sameLine();
        StudioWidgets.pill(
                String.format("%.0f%%", fact.confidence() * 100),
                0xFF1D344A,
                StudioPalette.TEXT);
        ImGui.sameLine();
        ImGui.textDisabled(fact.source().toString());

        for (Evidence evidence : fact.evidence()) {
            if (StudioWidgets.beginPropertyTable("evidence-" + evidence.hashCode())) {
                StudioWidgets.propertyRow("Evidence", evidence.description());
                StudioWidgets.propertyRow("Signal", String.format("%.2f", evidence.weight()));
                StudioWidgets.endPropertyTable();
            }
        }
        ImGui.separator();
        ImGui.popID();
    }

    private static void renderRuleTrace(
            WorldDocument world,
            TileCoordinate coord,
            WorldObject pickedObject,
            LoadedOsrsCacheSession cache) {
        RuleTrace.TileRuleTrace tileTrace = RuleTrace.traceTile(world, coord);
        if (StudioWidgets.beginPropertyTable("knowledge-rule-trace")) {
            StudioWidgets.propertyRowMono("Tile elevation", Integer.toString(tileTrace.elevation()));
            StudioWidgets.propertyRowMono("Effective plane", Integer.toString(tileTrace.effectivePlane()));
            StudioWidgets.propertyRow(
                    "Tile flags",
                    "Blocked " + tileTrace.flags().blocked()
                            + " · Bridge " + tileTrace.flags().bridge()
                            + " · Roof " + tileTrace.flags().underRoof());
            StudioWidgets.endPropertyTable();
        }

        if (pickedObject == null) return;
        final WorldObject objRef = pickedObject;
        RuleTrace.traceObject(
                pickedObject,
                world,
                cache != null ? cache.bundle().definitions() : null).ifPresent(trace -> {
            ImGui.dummy(1.0f, 6.0f);
            if (StudioWidgets.beginPropertyTable("knowledge-object-trace")) {
                StudioWidgets.propertyRow(
                        "Object shape",
                        trace.shapeDescriptor() != null
                                ? trace.shapeDescriptor().name()
                                : "Shape " + objRef.type());
                StudioWidgets.propertyRow("Loc variants",
                        trace.variantCount() + " · mirror " + trace.mirrorApplied());
                StudioWidgets.propertyRowMono(
                        "Displacement", Integer.toString(trace.displacementUsed()));
                StudioWidgets.propertyRow(
                        "Merge normals",
                        trace.mergeNormalsEligible() ? "Yes · opcode 22" : "No");
                StudioWidgets.propertyRow(
                        "Ground contour",
                        trace.contourGroundApplied()
                                ? "Type " + trace.contourGroundType()
                                : "Disabled");
                StudioWidgets.endPropertyTable();
            }
        });
    }
}
