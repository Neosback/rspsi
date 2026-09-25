package com.rspsi.studio.ui.panels;

import com.rspsi.cache.definition.ObjectDefinitionEditTransaction;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.cache.definition.ObjectDefinitionRawView;
import com.rspsi.cache.store.ObjectDefinitionOutputCacheBuilder;
import com.rspsi.cache.workspace.LoadedOsrsCacheSession;
import com.rspsi.cache.workspace.ObjectDefinitionEditWorkspace;
import com.rspsi.editor.inspector.ObjectReport;
import com.rspsi.editor.inspector.ObjectResolutionSummary;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.model.OsrsLocShape;
import com.rspsi.editor.render.ObjectPreviewScene;
import com.rspsi.editor.selection.ObjectSelection;
import com.rspsi.editor.selection.ObjectSetSelection;
import com.rspsi.editor.selection.Selection;
import com.rspsi.editor.settings.EditorSettingKeys;
import com.rspsi.editor.settings.SettingsStore;
import com.rspsi.editor.ui.DockRegion;
import com.rspsi.studio.theme.StudioDrawColors;
import com.rspsi.studio.theme.StudioFonts;
import com.rspsi.studio.theme.StudioPalette;
import com.rspsi.studio.theme.SettingRows;
import com.rspsi.studio.theme.StudioIcons;
import com.rspsi.studio.ui.ObjectPreviewRenderer;
import com.rspsi.studio.ui.ObjectPropertyTree;
import com.rspsi.studio.ui.StudioPanel;
import com.rspsi.studio.ui.StudioPanelContext;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImGuiListClipper;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiStyleVar;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Virtualized Object Viewer & Spawner panel matching Displee's Object Panel.
 * Uses ImGuiListClipper for 60,000+ object definition browsing at 144 FPS,
 * integrated with palette-to-viewport drag & drop.
 */
public final class ObjectViewerPanel implements StudioPanel {
    public static final String ID = "studio.object-viewer";

    private int activeSubTab = 0; // 0: Object viewer, 1: Object properties
    private final ImInt typeFilter = new ImInt(0);
    private final ImString searchFilter = new ImString(64);
    private final ImString rawPropertyFilter = new ImString(64);
    // No selection by default - a hardcoded "always Bank booth" default made
    // every fresh session look like it had already picked something.
    private final ImInt selectedObjectId = new ImInt(-1);
    private final ImInt objectType = new ImInt(10);
    private final ImInt objectRotation = new ImInt(0);

    // Definition transactions themselves are session-owned by
    // LoadedOsrsCacheSession.objectDefinitions(). This panel keeps only
    // transient widget/build state.
    private LoadedOsrsCacheSession definitionUiCache;
    private final ImString outputCachePath = new ImString(1024);
    private CompletableFuture<DefinitionBuildCompletion> definitionBuild;
    private String definitionEditStatus = "";
    private String definitionBuildStatus = "";
    private int lastPropertiesObjectId = Integer.MIN_VALUE;

    private final ObjectPreviewRenderer previewRenderer = new ObjectPreviewRenderer();
    private final ObjectPropertyTree propertyTree = new ObjectPropertyTree();
    private float previewYaw = ObjectPreviewScene.DEFAULT_ORBIT_YAW;
    private float previewElevation = ObjectPreviewScene.DEFAULT_ELEVATION;
    private float previewZoom = 1.0f;

    // Tracks the last viewport pick this panel already reacted to, so a
    // fresh Select-Object/Multi-Select-Object pick takes over the preview
    // without fighting a selection the user then browses away from
    // manually (e.g. clicking a different row in the grid below).
    private int lastSyncedPickedObjectId = Integer.MIN_VALUE;

    // Filter cache state
    private List<Integer> allObjectIds = null;
    private final List<Integer> filteredObjectIds = new ArrayList<>();
    private String lastFilterQuery = null;
    private int lastTypeFilter = -1;

    private static final String[] FILTER_OPTIONS = {"All", "Interactive", "Solid", "Decorations", "Walls"};
    private static final String[] OBJECT_TYPES = java.util.Arrays.stream(OsrsLocShape.values())
            .sorted(java.util.Comparator.comparingInt(OsrsLocShape::id))
            .map(shape -> shape.id() + " - " + shape.displayName())
            .toArray(String[]::new);
    private static final String[] ROTATIONS = {"West (0)", "North (1)", "East (2)", "South (3)"};

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String title() {
        return "Object Viewer";
    }

    @Override
    public String icon() {
        return StudioIcons.OBJECT;
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
        return 20;
    }

    @Override
    public float preferredRightSidebarWidth() {
        return 420.0f;
    }

    @Override
    public void render(StudioPanelContext context) {
        LoadedOsrsCacheSession cache = context.cache();
        SettingsStore settings = context.settings();

        syncDefinitionUiState(cache);
        pollDefinitionBuild(context);
        syncFromViewportPick(context);

        // 1. Sub-tabs at top: [Viewer] [Properties]. The active tab reads as
        // a filled, brighter segment - not a checkmark glued onto a button
        // that already has an icon of its own.
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 8.0f, 5.0f);
        float tabWidth = ImGui.getContentRegionAvailX() * 0.5f - 2.0f;
        renderSubTabButton("Viewer", 0, tabWidth);
        ImGui.sameLine();
        renderSubTabButton("Properties", 1, tabWidth);
        ImGui.popStyleVar();
        ImGui.separator();

        if (activeSubTab == 0) {
            renderObjectViewerSubTab(context, cache, settings);
        } else {
            renderObjectPropertiesSubTab(context, cache, settings);
        }
    }

    /**
     * Picking an object with Select-Object/Multi-Select-Object in the
     * viewport takes over this panel's preview, the same way clicking a row
     * in the grid below does - a viewport pick is not a lesser way to
     * choose an object than typing its id.
     */
    private void syncFromViewportPick(StudioPanelContext context) {
        if (context.session() == null) return;
        Selection current = context.session().selection().current();
        WorldObject picked = switch (current) {
            case ObjectSelection single -> single.object();
            case ObjectSetSelection set -> set.objects().iterator().next();
            case null, default -> null;
        };
        if (picked == null) return;
        if (picked.id() == lastSyncedPickedObjectId) return;
        lastSyncedPickedObjectId = picked.id();
        selectedObjectId.set(picked.id());
        activeSubTab = 0;
    }

    private void renderSubTabButton(String label, int tabIndex, float width) {
        boolean active = activeSubTab == tabIndex;
        if (active) {
            ImGui.pushStyleColor(ImGuiCol.Button, StudioPalette.ACCENT);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, StudioPalette.ACCENT_HOVER);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, StudioPalette.ACCENT_ACTIVE);
            ImGui.pushStyleColor(ImGuiCol.Text, StudioPalette.TEXT);
        } else {
            ImGui.pushStyleColor(ImGuiCol.Button, StudioPalette.PANEL_ELEVATED);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, StudioPalette.FIELD_HOVER);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, StudioPalette.ACCENT_SOFT);
            ImGui.pushStyleColor(ImGuiCol.Text, StudioPalette.TEXT);
        }
        if (ImGui.button(label + "##sub-" + tabIndex, width, ImGui.getFrameHeight() + ImGui.getStyle().getFramePaddingY())) {
            activeSubTab = tabIndex;
        }
        ImGui.popStyleColor(4);
    }

    private static final int GRID_PREVIEW_ANCHOR = 1;
    private static final float CELL_SIZE = 88.0f;
    private static final float CELL_SPACING = 8.0f;

    private void renderObjectViewerSubTab(StudioPanelContext context,
                                          LoadedOsrsCacheSession cache,
                                          SettingsStore settings) {
        // Filter dropdown & Search
        ImGui.setNextItemWidth(-Float.MIN_VALUE);
        ImGui.inputTextWithHint("##obj-search", StudioIcons.SEARCH + "  Search by ID or name...", searchFilter);
        if (SettingRows.beginPlain("object-filter")) {
            SettingRows.combo("Show", typeFilter, FILTER_OPTIONS);
            SettingRows.end();
        }
        ImGui.separator();

        int objId = selectedObjectId.get();
        float panelW = ImGui.getContentRegionAvailX();

        renderPreviewAndControls(context, cache, settings, objId, panelW);

        ImGui.separator();

        // Virtualized thumbnail grid
        updateFilteredList(cache);
        ImGui.textDisabled("Objects (" + filteredObjectIds.size() + " matches):");
        renderThumbnailGrid(cache, settings, panelW);
    }

    /** The persistent, rotatable preview and its placement controls - stays put above the grid. */
    private void renderPreviewAndControls(StudioPanelContext context, LoadedOsrsCacheSession cache,
                                          SettingsStore settings, int objId, float panelW) {
        float cx = ImGui.getCursorScreenPos().x;
        float cy = ImGui.getCursorScreenPos().y;
        ImDrawList draw = ImGui.getWindowDrawList();
        // Square preview sized from the font, so it scales with DPI, capped by the panel width.
        float previewHeight = Math.min(panelW, ImGui.getFontSize() * 17.0f);
        draw.addRectFilled(cx, cy, cx + panelW, cy + previewHeight, StudioPalette.draw(StudioPalette.PANEL_ELEVATED), 4.0f);
        draw.addRect(cx, cy, cx + panelW, cy + previewHeight, StudioPalette.draw(StudioPalette.BORDER_STRONG), 4.0f);

        if (objId < 0 || cache == null) {
            String prompt = cache == null ? "No cache loaded." : "Select an object below to preview it.";
            draw.addText(StudioFonts.ui(), 13, cx + 12, cy + previewHeight * 0.5f - 8.0f,
                    StudioPalette.draw(StudioPalette.TEXT_DISABLED), prompt);
            ImGui.dummy(panelW, previewHeight + 8.0f);
        } else {
            int size = (int) previewHeight - 4;
            int texture = previewRenderer.render(cache.bundle().definitions(), objId, objectType.get(),
                    objectRotation.get(), previewYaw, previewElevation, previewZoom, size, size);

            ImGui.setCursorScreenPos(cx + (panelW - size) * 0.5f, cy + 2.0f);
            if (texture != 0) {
                ImGui.image((long) texture, size, size);
            } else {
                ObjectResolutionSummary resolution = ObjectResolutionSummary.capture(
                        new WorldObject(objId, objectType.get(), objectRotation.get(),
                                0, GRID_PREVIEW_ANCHOR, GRID_PREVIEW_ANCHOR),
                        cache.bundle().definitions());
                String reason = resolution.renderableGeometryReady()
                        ? "Model data resolved, but the preview produced no renderable packet"
                        : resolution.diagnosticSummary();
                draw.addText(StudioFonts.ui(), 12, cx + 12, cy + previewHeight * 0.5f - 16.0f,
                        StudioPalette.WARNING, "No renderable model for #" + objId);
                draw.addText(StudioFonts.mono(), 11, cx + 12, cy + previewHeight * 0.5f + 4.0f,
                        StudioDrawColors.abgr(0xFF94A3B8), compactPreviewDiagnostic(reason));
            }

            // Drag-anywhere-on-the-preview to orbit; the invisible button
            // both hosts the drag gesture and doubles as the drop source.
            ImGui.setCursorScreenPos(cx + (panelW - size) * 0.5f, cy + 2.0f);
            ImGui.invisibleButton("##preview-3d-" + objId, size, size);
            boolean previewHovered = ImGui.isItemHovered();
            if (ImGui.isItemActive() && ImGui.isMouseDragging(0)) {
                previewYaw -= ImGui.getMouseDragDeltaX() * 0.01f;
                previewElevation = clamp(previewElevation + ImGui.getMouseDragDeltaY() * 0.01f,
                        ObjectPreviewScene.MIN_ELEVATION, ObjectPreviewScene.MAX_ELEVATION);
                ImGui.resetMouseDragDelta();
            }
            if (previewHovered) {
                float wheel = ImGui.getIO().getMouseWheel();
                if (wheel != 0.0f) {
                    // Scrolling "up" (positive wheel) should move the camera
                    // closer, so it shrinks the distance multiplier.
                    previewZoom = clamp(previewZoom * (1.0f - wheel * 0.1f), 0.35f, 4.0f);
                }
                if (ImGui.isMouseDoubleClicked(0)) {
                    previewYaw = ObjectPreviewScene.DEFAULT_ORBIT_YAW;
                    previewElevation = ObjectPreviewScene.DEFAULT_ELEVATION;
                    previewZoom = 1.0f;
                }
            }
            // beginDragDropSource must immediately follow the item it
            // sources from - anything (even a debug ImGui.text) placed
            // between the invisibleButton and this call makes it operate on
            // the wrong "last item" and hard-crashes the native assert.
            if (ImGui.beginDragDropSource()) {
                ImGui.setDragDropPayload("DND_OBJECT_ID", objId, ImGuiCond.Once);
                ImGui.text(StudioIcons.OBJECT + " Spawn Object #" + objId);
                ImGui.endDragDropSource();
            } else if (previewHovered) {
                ImGui.setTooltip("Drag to orbit, scroll to zoom, double-click to reset.\nDrag onto the 3D viewport to spawn. Grid cells are one game tile.");
            }

            ImGui.setCursorScreenPos(cx, cy + previewHeight + 4.0f);
        }

        String objName = cache == null || objId < 0 ? null
                : cache.bundle().definitions().object(objId)
                        .map(definition -> objectLabel(definition.displayName(), objId))
                        .orElse(null);
        if (objId >= 0) {
            ImGui.textColored(StudioPalette.ACCENT, objName == null ? "Object #" + objId : objName);
        }

        if (SettingRows.beginPlain("place-object")) {
            if (SettingRows.combo("Shape", objectType, OBJECT_TYPES)) {
                settings.set(EditorSettingKeys.OBJECT_TYPE, objectType.get());
            }
            if (SettingRows.combo("Rotation", objectRotation, ROTATIONS)) {
                settings.set(EditorSettingKeys.OBJECT_ROTATION, objectRotation.get());
            }
            SettingRows.end();
        }
        ImGui.beginDisabled(objId < 0);
        if (ImGui.button(StudioIcons.ADD_OBJECT + " Add game object##add-obj", -Float.MIN_VALUE, 0.0f)) {
            settings.set(EditorSettingKeys.OBJECT_ID, objId);
            settings.set(EditorSettingKeys.OBJECT_TYPE, objectType.get());
            settings.set(EditorSettingKeys.OBJECT_ROTATION, objectRotation.get());
            if (context.activateTool() != null) {
                context.activateTool().accept("object.place");
            }
        }
        ImGui.endDisabled();
        if (ImGui.isItemHovered(imgui.flag.ImGuiHoveredFlags.AllowWhenDisabled)) {
            ImGui.setTooltip("Place this object with the Place tool, or drag the preview onto the viewport.");
        }
    }

    private static String objectLabel(String name, int id) {
        String fallback = "Object #" + id;
        if (name == null || name.isBlank() || "null".equalsIgnoreCase(name.trim())
                || fallback.equals(name)) {
            return fallback;
        }
        return name + " (#" + id + ")";
    }

    private static String compactPreviewDiagnostic(String value) {
        if (value == null || value.isBlank()) return "No diagnostic detail available";
        return value.length() <= 64 ? value : value.substring(0, 61) + "...";
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * A scrollable grid of lightweight swatches (id, name, a has-model
     * indicator) - not a full 3D render per cell, which would mean
     * software-rendering thousands of models a frame. The one real 3D
     * preview lives above and follows the current selection instead.
     */
    private void renderThumbnailGrid(LoadedOsrsCacheSession cache, SettingsStore settings, float panelW) {
        float gridH = Math.max(120.0f, ImGui.getContentRegionAvailY() - 4.0f);
        if (!ImGui.beginChild("##obj-thumb-grid", panelW, gridH, true)) {
            ImGui.endChild();
            return;
        }

        float cellStride = CELL_SIZE + CELL_SPACING;
        int columns = Math.max(1, (int) ((ImGui.getContentRegionAvailX() + CELL_SPACING) / cellStride));
        int rows = (filteredObjectIds.size() + columns - 1) / columns;

        ImGuiListClipper clipper = new ImGuiListClipper();
        clipper.begin(rows);
        while (clipper.step()) {
            for (int row = clipper.getDisplayStart(); row < clipper.getDisplayEnd(); row++) {
                for (int col = 0; col < columns; col++) {
                    int index = row * columns + col;
                    if (index >= filteredObjectIds.size()) break;
                    if (col > 0) ImGui.sameLine(0.0f, CELL_SPACING);
                    renderThumbnailCell(cache, settings, filteredObjectIds.get(index));
                }
            }
        }
        clipper.end();
        ImGui.endChild();
    }

    private void renderThumbnailCell(LoadedOsrsCacheSession cache, SettingsStore settings, int id) {
        ImGui.pushID(id);
        float cx = ImGui.getCursorScreenPos().x;
        float cy = ImGui.getCursorScreenPos().y;

        ImGui.invisibleButton("##cell", CELL_SIZE, CELL_SIZE);
        boolean hovered = ImGui.isItemHovered();
        if (ImGui.isItemClicked()) {
            selectedObjectId.set(id);
            settings.set(EditorSettingKeys.OBJECT_ID, id);
        }
        if (ImGui.beginDragDropSource()) {
            ImGui.setDragDropPayload("DND_OBJECT_ID", id, ImGuiCond.Once);
            ImGui.text("Object #" + id + " (drop on 3D viewport)");
            ImGui.endDragDropSource();
        }

        String name = null;
        boolean hasModel = true;
        if (cache != null) {
            var defOpt = cache.bundle().definitions().object(id);
            if (defOpt.isPresent()) {
                var def = defOpt.get();
                if (def.hasDisplayName()) name = def.displayName();
                hasModel = def.modelIds().length > 0 || def.hasTransforms();
            }
        }

        boolean selected = id == selectedObjectId.get();
        ImDrawList draw = ImGui.getWindowDrawList();
        int bg = selected ? 0xFF2A3F5C : hovered ? 0xFF232F3F : 0xFF1A2432;
        draw.addRectFilled(cx, cy, cx + CELL_SIZE, cy + CELL_SIZE, StudioDrawColors.abgr(bg), 3.0f);
        draw.addRect(cx, cy, cx + CELL_SIZE, cy + CELL_SIZE,
                StudioDrawColors.abgr(selected ? 0xFF3B82F6 : 0xFF2B3A4E), 3.0f, 0, selected ? 2.0f : 1.0f);
        draw.addText(StudioFonts.ui(), 15, cx + 8, cy + 8, StudioDrawColors.abgr(0xFFE2E8F0), "#" + id);
        String label = name == null ? "(unnamed)" : name;
        if (label.length() > 13) label = label.substring(0, 12) + "…";
        draw.addText(StudioFonts.mono(), 11, cx + 8, cy + CELL_SIZE - 20, StudioDrawColors.abgr(0xFF94A3B8), label);
        if (!hasModel) {
            // No models of its own and no transform fallback either - this
            // definition genuinely cannot render; flag it instead of letting
            // the object silently vanish when placed.
            draw.addText(StudioFonts.mono(), 10, cx + 8, cy + CELL_SIZE - 34,
                    StudioDrawColors.abgr(0xFFF59E0B), "no model");
        }

        ImGui.popID();
    }

    private void updateFilteredList(LoadedOsrsCacheSession cache) {
        String query = searchFilter.get().trim().toLowerCase();
        int filterMode = typeFilter.get();

        if (allObjectIds == null && cache != null) {
            allObjectIds = cache.bundle().definitions().objectIds();
            if (allObjectIds.isEmpty()) {
                allObjectIds = new ArrayList<>();
                for (int i = 0; i < 45000; i++) allObjectIds.add(i);
            }
        }

        if (allObjectIds == null) {
            filteredObjectIds.clear();
            return;
        }

        if (query.equals(lastFilterQuery) && filterMode == lastTypeFilter) {
            return; // Cache hit
        }

        lastFilterQuery = query;
        lastTypeFilter = filterMode;
        filteredObjectIds.clear();

        boolean isNumeric = !query.isEmpty() && query.chars().allMatch(Character::isDigit);
        int maxResults = query.isEmpty() ? 5000 : 2000;

        for (int id : allObjectIds) {
            if (filteredObjectIds.size() >= maxResults) break;

            if (cache != null) {
                var defOpt = cache.bundle().definitions().object(id);
                if (filterMode == 1 && defOpt.isPresent() && !defOpt.get().interactive()) {
                    continue; // Skip non-interactive
                }
                if (filterMode == 2 && defOpt.isPresent() && (defOpt.get().width() <= 0 || defOpt.get().length() <= 0)) {
                    continue; // Skip non-solid
                }

                if (!query.isEmpty()) {
                    if (isNumeric) {
                        if (!String.valueOf(id).contains(query)) continue;
                    } else {
                        if (defOpt.isEmpty() || !defOpt.get().hasDisplayName()
                                || !defOpt.get().displayName().toLowerCase().contains(query)) {
                            continue;
                        }
                    }
                }
            } else if (!query.isEmpty() && !String.valueOf(id).contains(query)) {
                continue;
            }

            filteredObjectIds.add(id);
        }
    }

    private void renderObjectPropertiesSubTab(StudioPanelContext context,
                                              LoadedOsrsCacheSession cache,
                                              SettingsStore settings) {
        ImGui.alignTextToFramePadding();
        ImGui.text("Object");
        ImGui.sameLine();
        ImGui.setNextItemWidth(-Float.MIN_VALUE);
        ImGui.inputInt("##prop-obj-id", selectedObjectId);
        int id = selectedObjectId.get();
        if (id != lastPropertiesObjectId) {
            definitionEditStatus = "";
            lastPropertiesObjectId = id;
        }

        if (cache == null) {
            ImGui.textDisabled("Load an OSRS cache to inspect object definitions.");
            return;
        }

        var definitions = cache.bundle().definitions();
        Optional<ObjectDefinitionView> definition = definitions.object(id);
        if (definition.isEmpty()) {
            ImGui.textDisabled("Object definition not found for ID " + id);
            return;
        }
        ObjectDefinitionEditTransaction transaction = definitionTransaction(cache, id);
        ObjectDefinitionRawView raw = transaction == null
                ? definitions.objectRaw(id).orElse(null)
                : transaction.preview();
        boolean canEdit = transaction != null && context.session() != null && context.session().canEdit();

        String previewName = rawField(raw, "name").map(ObjectDefinitionRawView.Field::value)
                .orElse(definition.get().displayName());
        ImGui.textColored(StudioDrawColors.abgr(0xFF38BDF8), objectLabel(previewName, id));
        ImGui.sameLine();
        float copyWidth = ImGui.calcTextSize("Copy").x + ImGui.getStyle().getFramePaddingX() * 2.0f;
        ImGui.setCursorPosX(Math.max(ImGui.getCursorPosX(),
                ImGui.getCursorPosX() + ImGui.getContentRegionAvailX() - copyWidth));
        if (ImGui.smallButton("Copy##prop-copy")) {
            ImGui.setClipboardText(ObjectReport.forDefinition(id, definitions).toText());
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip("Copy this object's properties as text.");
        if (transaction == null) {
            ImGui.textDisabled("Read-only: this cache backend has no definition edit transactions.");
        } else if (!canEdit) {
            ImGui.textDisabled("Read-only: open an editable Studio session to change fields.");
        } else if (transaction.dirty()) {
            ImGui.textColored(StudioDrawColors.abgr(0xFFF59E0B), transaction.dirtyFields().size() + " field(s), "
                    + transaction.dirtyParams().size() + " param(s) edited"
                    + (transaction.hasUnpublishedChanges() ? " - not saved yet" : " - saved"));
        }

        propertyTree.render("viewer", new ObjectPropertyTree.Source(id, raw, definition.get(), transaction,
                context.session(), definitions));
        if (!propertyTree.status().isBlank()) {
            ImGui.textWrapped(propertyTree.status());
        }

        if (transaction != null && ImGui.collapsingHeader("Save game object##prop-save")) {
            renderDefinitionTransactionEditor(context, cache, transaction);
        }

        if (ImGui.collapsingHeader("Raw decoded definition##prop-raw")) {
            ImGui.setNextItemWidth(-Float.MIN_VALUE);
            ImGui.inputTextWithHint("##raw-object-filter",
                    StudioIcons.SEARCH + "  Filter field, opcode, param or value...",
                    rawPropertyFilter);
            if (raw == null) {
                ImGui.textDisabled("Raw definition metadata is unavailable for this cache backend.");
            } else {
                renderRawObjectDefinition(
                        raw,
                        transaction == null ? Set.of() : transaction.dirtyFields(),
                        transaction == null ? Set.of() : transaction.dirtyParams());
            }
        }
    }

    /** Validation and publishing of this session's definition edits to an output cache. */
    private void renderDefinitionTransactionEditor(StudioPanelContext context,
                                                   LoadedOsrsCacheSession cache,
                                                   ObjectDefinitionEditTransaction transaction) {
        ImGui.pushID(transaction.id());
        ImGui.textWrapped("The source cache stays read-only. Edits are published to a separate, "
                + "verified output cache.");
        if (ImGui.button("Validate encoded definition##definition-validate")) {
            try {
                byte[] encoded = transaction.encodeValidated();
                definitionEditStatus = "Validated canonical OpenRune payload: "
                        + encoded.length + " bytes";
            } catch (RuntimeException failure) {
                definitionEditStatus = "Validation failed: " + failureMessage(failure);
            }
        }
        if (!definitionEditStatus.isBlank()) {
            ImGui.textWrapped(definitionEditStatus);
        }
        renderDefinitionOutputSection(context, cache);
        ImGui.popID();
    }

    private void renderRawObjectDefinition(ObjectDefinitionRawView raw,
                                           Set<String> dirtyFields,
                                           Set<Integer> dirtyParams) {
        String filter = rawPropertyFilter.get().trim().toLowerCase(java.util.Locale.ROOT);

        ImGui.pushFont(StudioFonts.mono(), 0.0f);
        ImGui.textDisabled("OPCODE  FIELD  TYPE  VALUE");

        int visibleFields = 0;
        for (ObjectDefinitionRawView.Field field : raw.fields()) {
            if (!matchesRawFilter(filter, field.name(), field.opcode(),
                    field.type().name(), field.value())) {
                continue;
            }
            visibleFields++;
            String opcode = field.opcode().isBlank() ? "-" : field.opcode();
            String name = dirtyFields.contains(field.name())
                    ? "* " + field.name() : field.name();
            ImGui.textWrapped(formatRawRow(opcode, name,
                    field.type().name().toLowerCase(java.util.Locale.ROOT),
                    compactRawValue(field.value())));
        }

        ImGui.separator();
        ImGui.textDisabled("Opcode 249 parameters (" + raw.params().size() + ")");
        int visibleParams = 0;
        for (ObjectDefinitionRawView.Param param : raw.params()) {
            String paramId = Integer.toString(param.id());
            if (!matchesRawFilter(filter, "param", "249", paramId,
                    param.type().name(), param.value())) {
                continue;
            }
            visibleParams++;
            String name = (dirtyParams.contains(param.id()) ? "* " : "")
                    + "param[" + param.id() + "]";
            ImGui.textWrapped(formatRawRow(
                    "249",
                    name,
                    param.type().name().toLowerCase(java.util.Locale.ROOT),
                    compactRawValue(param.value())));
        }

        if (visibleFields == 0 && visibleParams == 0) {
            ImGui.textDisabled("No raw fields match the current filter.");
        } else if (raw.params().isEmpty() && filter.isEmpty()) {
            ImGui.textDisabled("No opcode 249 parameters.");
        }
        ImGui.popFont();
    }

    private void syncDefinitionUiState(LoadedOsrsCacheSession cache) {
        if (definitionUiCache == cache) return;
        definitionUiCache = cache;
        definitionEditStatus = "";
        definitionBuildStatus = "";
        lastPropertiesObjectId = Integer.MIN_VALUE;
        allObjectIds = null;
        filteredObjectIds.clear();
        lastFilterQuery = null;
        lastTypeFilter = -1;
        outputCachePath.set(cache == null
                ? ""
                : cache.objectDefinitions().publicationTarget()
                        .orElseGet(() -> suggestedOutputPath(cache))
                        .toString());
    }

    private ObjectDefinitionEditTransaction definitionTransaction(
            LoadedOsrsCacheSession cache, int id) {
        return cache.objectDefinitions().transaction(id).orElse(null);
    }

    private void renderDefinitionOutputSection(
            StudioPanelContext context,
            LoadedOsrsCacheSession cache) {
        ObjectDefinitionEditWorkspace workspace = cache.objectDefinitions();
        int modified = workspace.modifiedCount();
        int unpublished = workspace.unpublishedCount();

        ImGui.separator();
        ImGui.text("Definition output cache");
        ImGui.textDisabled("Modified: " + modified + "  |  Unpublished: " + unpublished);
        ImGui.textDisabled(
                "Builds a new cache or transactionally updates an existing output. "
                        + "The selected source cache remains read-only.");

        boolean buildRunning = definitionBuild != null;
        Path publicationTarget = workspace.publicationTarget().orElse(null);

        ImGui.beginDisabled(buildRunning || publicationTarget != null);
        ImGui.inputTextWithHint(
                "Output directory##definition-output-cache",
                "Choose a new or existing output cache directory...",
                outputCachePath);
        ImGui.endDisabled();

        Path candidate = outputPathOrNull();
        boolean outputExists = candidate != null && Files.exists(candidate);
        boolean outputDirectory = outputExists && Files.isDirectory(candidate);
        boolean targetMismatch = publicationTarget != null
                && candidate != null
                && !publicationTarget.equals(candidate);
        boolean targetMissing = publicationTarget != null
                && !Files.isDirectory(publicationTarget);

        if (publicationTarget != null) {
            ImGui.textDisabled("Session output: " + publicationTarget);
        }
        if (targetMissing) {
            ImGui.textColored(StudioPalette.INFO,
                    "The bound output cache is missing. Reload the source session before publishing elsewhere.");
        } else if (targetMismatch) {
            ImGui.textColored(StudioDrawColors.abgr(0xFF60A5FA),
                    "This cache session is already bound to " + publicationTarget);
        } else if (outputExists && !outputDirectory) {
            ImGui.textColored(StudioDrawColors.abgr(0xFF60A5FA),
                    "That output path exists but is not a cache directory.");
        } else if (outputDirectory) {
            ImGui.textDisabled(
                    "Existing output selected: Studio will stage, verify, and replace it transactionally.");
        }

        ImGui.beginDisabled(buildRunning || publicationTarget != null);
        if (ImGui.button("Suggest path##definition-output-suggest")) {
            outputCachePath.set(suggestedOutputPath(cache).toString());
            definitionBuildStatus = "";
        }
        ImGui.endDisabled();
        ImGui.sameLine();

        boolean canBuild = unpublished > 0
                && !buildRunning
                && candidate != null
                && !targetMismatch
                && !targetMissing
                && (!outputExists || outputDirectory);
        ImGui.beginDisabled(!canBuild);
        String publishLabel = outputDirectory
                ? "Update output cache##definition-output-build"
                : "Build output cache##definition-output-build";
        if (ImGui.button(publishLabel)) {
            startDefinitionBuild(cache);
        }
        ImGui.endDisabled();

        if (buildRunning) {
            ImGui.textDisabled("Building and verifying output cache...");
        }
        if (!definitionBuildStatus.isBlank()) {
            ImGui.textWrapped(definitionBuildStatus);
        }
    }

    private void startDefinitionBuild(LoadedOsrsCacheSession cache) {
        try {
            Path output = outputPathOrNull();
            if (output == null) {
                throw new IllegalArgumentException("Output cache path cannot be blank");
            }

            ObjectDefinitionEditWorkspace workspace = cache.objectDefinitions();
            Path publicationTarget = workspace.publicationTarget().orElse(null);
            if (publicationTarget != null && !publicationTarget.equals(output)) {
                throw new IllegalArgumentException(
                        "Definition publication is already bound to output cache "
                                + publicationTarget);
            }

            boolean updateExisting = Files.exists(output);
            if (publicationTarget != null && !updateExisting) {
                throw new IllegalArgumentException(
                        "The bound output cache no longer exists: " + publicationTarget);
            }
            if (updateExisting && !Files.isDirectory(output)) {
                throw new IllegalArgumentException(
                        "Existing output path is not a directory: " + output);
            }

            ObjectDefinitionOutputCacheBuilder.BuildPlan plan =
                    ObjectDefinitionOutputCacheBuilder.plan(
                            workspace.unpublishedTransactions());
            Path source = cache.path();
            int revision = cache.identity().revision();

            definitionBuildStatus = "Prepared " + plan.definitionCount()
                    + " unpublished definition snapshot"
                    + (plan.definitionCount() == 1 ? "" : "s")
                    + (updateExisting ? " for transactional update." : " for new output.");
            definitionBuild = CompletableFuture.supplyAsync(() -> {
                try {
                    ObjectDefinitionOutputCacheBuilder.BuildResult result =
                            updateExisting
                                    ? ObjectDefinitionOutputCacheBuilder.updateExistingOutput(
                                            source, output, revision, plan)
                                    : ObjectDefinitionOutputCacheBuilder.buildNewOutput(
                                            source, output, revision, plan);
                    return DefinitionBuildCompletion.success(
                            workspace, plan, result, updateExisting);
                } catch (Exception failure) {
                    return DefinitionBuildCompletion.failure(
                            workspace, plan, updateExisting, failure);
                }
            });
        } catch (RuntimeException failure) {
            definitionBuildStatus =
                    "Output build rejected: " + failureMessage(failure);
        }
    }

    private void pollDefinitionBuild(StudioPanelContext context) {
        if (definitionBuild == null || !definitionBuild.isDone()) {
            return;
        }

        DefinitionBuildCompletion completion;
        try {
            completion = definitionBuild.join();
        } catch (RuntimeException failure) {
            definitionBuild = null;
            definitionBuildStatus =
                    "Output build failed: " + failureMessage(failure);
            return;
        }
        definitionBuild = null;

        if (completion.failure() != null) {
            definitionBuildStatus = "Output build failed: "
                    + failureMessage(completion.failure());
            return;
        }

        for (ObjectDefinitionOutputCacheBuilder.PlannedObjectDefinition definition
                : completion.plan().definitions()) {
            completion.workspace().markPublished(
                    completion.result().outputCache(),
                    definition.objectId(),
                    definition.preview());
        }
        context.persistDefinitionPublication().accept(context.cache());
        if (context.session() != null) {
            context.session().externalStateChanged();
        }

        ObjectDefinitionOutputCacheBuilder.BuildResult result = completion.result();
        definitionBuildStatus =
                (completion.updatedExisting() ? "Updated and verified " : "Built and verified ")
                + result.definitionCount()
                + " definition" + (result.definitionCount() == 1 ? "" : "s")
                + " (" + result.encodedBytes() + " encoded bytes) at "
                + result.outputCache();
    }

    private Path outputPathOrNull() {
        String value = outputCachePath.get().trim();
        if (value.isEmpty()) return null;
        try {
            return Path.of(value).toAbsolutePath().normalize();
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private static Path suggestedOutputPath(LoadedOsrsCacheSession cache) {
        Path source = cache.path().toAbsolutePath().normalize();
        Path parent = source.getParent();
        if (parent == null) {
            parent = Path.of(".").toAbsolutePath().normalize();
        }
        String sourceName = source.getFileName() == null
                ? "cache" : source.getFileName().toString();
        Path candidate = parent.resolve(sourceName + "-studio-output");
        int suffix = 2;
        while (Files.exists(candidate)) {
            candidate = parent.resolve(sourceName + "-studio-output-" + suffix++);
        }
        return candidate;
    }

    private static Optional<ObjectDefinitionRawView.Field> rawField(
            ObjectDefinitionRawView raw, String name) {
        if (raw == null) return Optional.empty();
        return raw.fields().stream()
                .filter(field -> field.name().equals(name))
                .findFirst();
    }

    private static String failureMessage(RuntimeException failure) {
        return failureMessage((Throwable) failure);
    }

    private static String failureMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null
                && (current.getMessage() == null || current.getMessage().isBlank())) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank()
                ? current.getClass().getSimpleName()
                : message;
    }

    private record DefinitionBuildCompletion(
            ObjectDefinitionEditWorkspace workspace,
            ObjectDefinitionOutputCacheBuilder.BuildPlan plan,
            ObjectDefinitionOutputCacheBuilder.BuildResult result,
            boolean updatedExisting,
            Throwable failure) {
        private static DefinitionBuildCompletion success(
                ObjectDefinitionEditWorkspace workspace,
                ObjectDefinitionOutputCacheBuilder.BuildPlan plan,
                ObjectDefinitionOutputCacheBuilder.BuildResult result,
                boolean updatedExisting) {
            return new DefinitionBuildCompletion(
                    workspace, plan, result, updatedExisting, null);
        }

        private static DefinitionBuildCompletion failure(
                ObjectDefinitionEditWorkspace workspace,
                ObjectDefinitionOutputCacheBuilder.BuildPlan plan,
                boolean updatedExisting,
                Throwable failure) {
            return new DefinitionBuildCompletion(
                    workspace, plan, null, updatedExisting, failure);
        }
    }

    private static boolean matchesRawFilter(String filter, String... values) {
        if (filter == null || filter.isBlank()) return true;
        for (String value : values) {
            if (value != null
                    && value.toLowerCase(java.util.Locale.ROOT).contains(filter)) {
                return true;
            }
        }
        return false;
    }

    private static String formatRawRow(String opcode, String field,
                                       String type, String value) {
        return String.format(java.util.Locale.ROOT, "%-12s %-24s %-10s %s",
                opcode, field, type, value);
    }

    private static String compactRawValue(String value) {
        if (value == null) return "null";
        String normalized = value.replace('\n', ' ').replace('\r', ' ');
        return normalized.length() <= 180
                ? normalized
                : normalized.substring(0, 177) + "...";
    }
}
