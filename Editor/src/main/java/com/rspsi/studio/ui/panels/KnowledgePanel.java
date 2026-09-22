package com.rspsi.studio.ui.panels;

import com.rspsi.cache.workspace.LoadedOsrsCacheSession;
import com.rspsi.editor.knowledge.Evidence;
import com.rspsi.editor.knowledge.KnowledgeFact;
import com.rspsi.editor.knowledge.KnowledgeSnapshot;
import com.rspsi.editor.knowledge.MetricKey;
import com.rspsi.editor.knowledge.RegionProfile;
import com.rspsi.editor.knowledge.SemanticTag;
import com.rspsi.editor.knowledge.WorldKnowledgeService;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.LocalTile;
import com.rspsi.editor.model.WorldTile;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.render.PickResult;
import com.rspsi.editor.ui.DockRegion;
import com.rspsi.osrs.rules.RuleTrace;
import com.rspsi.studio.theme.StudioFonts;
import com.rspsi.studio.theme.StudioIcons;
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

/**
 * World Knowledge, semantic classification, and rule-trace panel.
 */
public final class KnowledgePanel implements StudioPanel {
    public static final String ID = "studio.knowledge";
    private final ImString customTagInput = new ImString(32);

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String title() {
        return "World Knowledge";
    }

    @Override
    public String icon() {
        return StudioIcons.INFO;
    }

    @Override
    public DockRegion preferredRegion() {
        return DockRegion.RIGHT;
    }

    @Override
    public Set<DockRegion> allowedRegions() {
        return EnumSet.of(DockRegion.RIGHT, DockRegion.BOTTOM);
    }

    @Override
    public int order() {
        return 35;
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

        // 1. World & Region Profile Summary
        if (ImGui.collapsingHeader("Region & World Intelligence", ImGuiTreeNodeFlags.DefaultOpen)) {
            RegionProfile worldProfile = snapshot.worldProfile();
            ImGui.pushFont(StudioFonts.mono(), 0.0f);
            ImGui.text("Total Tiles:     " + worldProfile.tileCount() + " (" + worldProfile.planes() + " planes)");
            ImGui.text("Dominant Floor:  Underlay #" + worldProfile.dominantUnderlay().orElse(-1)
                    + " | Overlay #" + worldProfile.dominantOverlay().orElse(-1));
            ImGui.text("Elevation:       Min " + worldProfile.minHeight() + " | Max " + worldProfile.maxHeight()
                    + " | Avg " + String.format("%.1f", worldProfile.averageHeight()));

            worldProfile.metric(MetricKey.MAX_SLOPE).ifPresent(maxSlope ->
                    ImGui.text("Steepest Slope:  " + String.format("%.1f", maxSlope) + "°"));
            worldProfile.metric(MetricKey.WALKABLE_RATIO).ifPresent(walkable ->
                    ImGui.text("Walkable Space:  " + String.format("%.1f%%", walkable * 100)));
            ImGui.popFont();
        }

        // 2. Selection Semantics & Explain Classification
        Optional<PickResult> picked = context.viewport() != null
                ? context.viewport().selection() : Optional.empty();

        if (picked.isPresent()) {
            PickResult hit = picked.get();
            WorldTile worldCoord = hit.tile();
            LocalTile local = context.session() == null ? null
                    : context.session().coordinates().toLocal(worldCoord).orElse(null);
            StudioWidgets.section("Selection Semantics");
            ImGui.text("Selected Tile: (" + worldCoord.plane() + ", "
                    + worldCoord.x() + ", " + worldCoord.y() + ")");
            if (local == null) {
                ImGui.textDisabled("Selected tile is outside the active document.");
                return;
            }
            TileCoordinate coord = local.coordinate();

            WorldDocument world = context.pluginLifecycle().host().context().world();
            WorldObject pickedObject = null;
            if (hit.objectHit() && world != null) {
                WorldTile objTile = hit.objectTile() != null ? hit.objectTile() : worldCoord;
                LocalTile objLocal = context.session().coordinates().toLocal(objTile).orElse(null);
                TileCoordinate objCoord = objLocal != null ? objLocal.coordinate() : coord;
                if (objCoord.plane() >= 0 && objCoord.plane() < world.planes() && world.contains(objCoord)) {
                    for (WorldObject obj : world.tile(objCoord).snapshot().objects()) {
                        if (obj.id() == hit.objectId()) {
                            pickedObject = obj;
                            break;
                        }
                    }
                }
            }

            // Derived Topology
            snapshot.topologyAt(coord).ifPresent(topo -> {
                ImGui.pushFont(StudioFonts.mono(), 0.0f);
                ImGui.text("Topology: Slope " + String.format("%.1f", topo.slopeMagnitude())
                        + " | Aspect " + topo.aspect() + " | Curvature " + topo.curvature());
                if (topo.isCliff()) ImGui.textColored(0xFF6666FF, "[!] Terrain marked as CLIFF");
                ImGui.popFont();
            });

            // Semantic Tags
            Set<SemanticTag> tags = snapshot.tagsAt(coord);
            if (tags.isEmpty()) {
                ImGui.textDisabled("No semantic tags active on this tile.");
            } else {
                ImGui.text("Semantic Tags:");
                for (SemanticTag tag : tags) {
                    ImGui.bulletText(tag.qualifiedName());
                }
            }

            // Explain Classification breakdown
            if (ImGui.collapsingHeader("Explain Classification", ImGuiTreeNodeFlags.DefaultOpen)) {
                List<KnowledgeFact<SemanticTag>> facts = snapshot.factsAt(coord);
                boolean hasObjFacts = pickedObject != null && !knowledge.classifyObject(pickedObject).isEmpty();
                if (facts.isEmpty() && !hasObjFacts) {
                    ImGui.textDisabled("No inferred classifications active.");
                } else {
                    for (KnowledgeFact<SemanticTag> fact : facts) {
                        String header = fact.value().qualifiedName() + String.format(" [%.0f%%]", fact.confidence() * 100)
                                + " (" + fact.source() + ")";
                        ImGui.text(header);
                        if (!fact.evidence().isEmpty()) {
                            ImGui.indent();
                            ImGui.pushFont(StudioFonts.mono(), 0.0f);
                            for (Evidence ev : fact.evidence()) {
                                ImGui.text("- " + ev.description() + " (signal: " + String.format("%.2f", ev.weight()) + ")");
                            }
                            ImGui.popFont();
                            ImGui.unindent();
                        }
                    }

                    if (pickedObject != null) {
                        List<KnowledgeFact<SemanticTag>> objFacts = knowledge.classifyObject(pickedObject);
                        for (KnowledgeFact<SemanticTag> fact : objFacts) {
                            String header = fact.value().qualifiedName() + String.format(" [%.0f%%]", fact.confidence() * 100)
                                    + " (" + fact.source() + ")";
                            ImGui.text(header);
                            if (!fact.evidence().isEmpty()) {
                                ImGui.indent();
                                ImGui.pushFont(StudioFonts.mono(), 0.0f);
                                for (Evidence ev : fact.evidence()) {
                                    ImGui.text("- " + ev.description() + " (signal: " + String.format("%.2f", ev.weight()) + ")");
                                }
                                ImGui.popFont();
                                ImGui.unindent();
                            }
                        }
                    }
                }
            }

            // Explain Rendering (Rule Trace)
            if (ImGui.collapsingHeader("Explain Rendering (Rule Trace)", ImGuiTreeNodeFlags.DefaultOpen)
                    && world != null && coord.plane() >= 0 && coord.plane() < world.planes()) {
                RuleTrace.TileRuleTrace tileTrace = RuleTrace.traceTile(world, coord);
                ImGui.pushFont(StudioFonts.mono(), 0.0f);
                ImGui.text("Tile Elevation:   " + tileTrace.elevation());
                ImGui.text("Effective Plane:  " + tileTrace.effectivePlane());
                ImGui.text("Tile Flags:       Blocked=" + tileTrace.flags().blocked()
                        + " Bridge=" + tileTrace.flags().bridge() + " Roof=" + tileTrace.flags().underRoof());

                if (pickedObject != null) {
                    final WorldObject objRef = pickedObject;
                    LoadedOsrsCacheSession cache = context.cache();
                    RuleTrace.traceObject(pickedObject, context.pluginLifecycle().host().context().world(),
                            cache != null ? cache.bundle().definitions() : null).ifPresent(trace -> {
                        ImGui.separator();
                        ImGui.text("Object Shape:     " + (trace.shapeDescriptor() != null ? trace.shapeDescriptor().name() : "Shape " + objRef.type()));
                        ImGui.text("Loc Variants:     " + trace.variantCount() + " (Mirror: " + trace.mirrorApplied() + ")");
                        ImGui.text("Displacement:     " + trace.displacementUsed());
                        ImGui.text("Merge Normals:    " + (trace.mergeNormalsEligible() ? "Yes (Opcode 22)" : "No"));
                        ImGui.text("Ground Contour:   " + (trace.contourGroundApplied() ? "Type " + trace.contourGroundType() : "Disabled"));
                    });
                }
                ImGui.popFont();
            }

            // User Overrides
            if (ImGui.collapsingHeader("User Metadata Overrides")) {
                ImGui.inputTextWithHint("##custom-tag", "New tag (e.g. core:SPAWN)", customTagInput);
                ImGui.sameLine();
                if (ImGui.button("Add Tag##add-user-tag") && !customTagInput.get().isBlank()) {
                    knowledge.userOverrides().addTileTag(coord, SemanticTag.of(customTagInput.get()));
                    knowledge.invalidate();
                    customTagInput.set("");
                }
            }
        } else {
            ImGui.textDisabled("Select or pick a tile in the viewport to inspect semantic knowledge and rule traces.");
        }
    }
}
