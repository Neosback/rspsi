package com.rspsi.studio.ui.panels;

import com.rspsi.cache.definition.ObjectDefinitionEditTransaction;
import com.rspsi.cache.definition.ObjectDefinitionEditValue;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.cache.definition.ObjectDefinitionRawView;
import com.rspsi.cache.workspace.LoadedOsrsCacheSession;
import com.rspsi.editor.ObjectDefinitionEditCommand;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.selection.ObjectSelection;
import com.rspsi.editor.selection.ObjectSetSelection;
import com.rspsi.editor.selection.Selection;
import com.rspsi.editor.settings.EditorSettingKeys;
import com.rspsi.editor.settings.SettingsStore;
import com.rspsi.editor.ui.DockRegion;
import com.rspsi.studio.theme.StudioDrawColors;
import com.rspsi.studio.theme.StudioFonts;
import com.rspsi.studio.theme.StudioIcons;
import com.rspsi.studio.ui.ObjectPreviewRenderer;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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

    // Definition editing stays isolated from the source cache. Transactions
    // are retained while this cache session is active so switching between
    // objects does not discard in-memory edits or break history references.
    private LoadedOsrsCacheSession definitionEditCache;
    private final Map<Integer, ObjectDefinitionEditTransaction> definitionEdits =
            new HashMap<>();
    private final Map<String, ScalarEditState> scalarEditStates = new HashMap<>();
    private final ImInt newParamId = new ImInt(0);
    private final ImInt newParamType = new ImInt(0);
    private final ImString newParamValue = new ImString(512);
    private String definitionEditStatus = "";
    private int lastPropertiesObjectId = Integer.MIN_VALUE;

    private final ObjectPreviewRenderer previewRenderer = new ObjectPreviewRenderer();
    private float previewYaw = (float) Math.toRadians(200.0);
    private float previewPitch = -0.35f;
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
    private static final String[] OBJECT_TYPES = {
            "0 - Straight wall", "1 - Diagonal wall corner", "2 - Entire wall corner",
            "3 - Straight wall corner", "4 - Straight decor", "5 - Diagonal decor",
            "6 - Diagonal corner decor", "7 - Straight internal decor", "8 - Diagonal in decor",
            "9 - Diagonal wall", "10 - Straight solid objects", "11 - Ground decor",
            "22 - Floor decor"
    };
    private static final String[] ROTATIONS = {"West (0)", "North (1)", "East (2)", "South (3)"};
    private static final String[] PARAM_TYPES = {"String", "Int", "Long"};

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
    public void render(StudioPanelContext context) {
        LoadedOsrsCacheSession cache = context.cache();
        SettingsStore settings = context.settings();

        syncDefinitionEditCache(cache);
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
            ImGui.pushStyleColor(ImGuiCol.Button, 0.23f, 0.51f, 0.96f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.29f, 0.56f, 0.98f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.18f, 0.44f, 0.87f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 1.0f, 1.0f, 1.0f);
        } else {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.12f, 0.16f, 0.21f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.16f, 0.21f, 0.28f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.12f, 0.16f, 0.21f, 1.0f);
            ImGui.pushStyleColor(ImGuiCol.Text, 0.58f, 0.64f, 0.72f, 1.0f);
        }
        if (ImGui.button(label + "##sub-" + tabIndex, width, 28.0f)) {
            activeSubTab = tabIndex;
        }
        ImGui.popStyleColor(4);
    }

    private static final float PREVIEW_HEIGHT = 200.0f;
    private static final float CELL_SIZE = 88.0f;
    private static final float CELL_SPACING = 8.0f;

    private void renderObjectViewerSubTab(StudioPanelContext context,
                                          LoadedOsrsCacheSession cache,
                                          SettingsStore settings) {
        // Filter dropdown & Search
        ImGui.combo("Filter##obj-flt", typeFilter, FILTER_OPTIONS);
        ImGui.inputTextWithHint("##obj-search", StudioIcons.SEARCH + "  Search by ID or name...", searchFilter);
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
        draw.addRectFilled(cx, cy, cx + panelW, cy + PREVIEW_HEIGHT, StudioDrawColors.abgr(0xFF1E2836), 4.0f);
        draw.addRect(cx, cy, cx + panelW, cy + PREVIEW_HEIGHT, StudioDrawColors.abgr(0xFF33455C), 4.0f);

        if (objId < 0 || cache == null) {
            String prompt = cache == null ? "No cache loaded." : "Select an object below to preview it.";
            draw.addText(StudioFonts.ui(), 13, cx + 12, cy + PREVIEW_HEIGHT * 0.5f - 8.0f,
                    StudioDrawColors.abgr(0xFF64748B), prompt);
            ImGui.dummy(panelW, PREVIEW_HEIGHT + 8.0f);
        } else {
            int size = (int) PREVIEW_HEIGHT - 4;
            int texture = previewRenderer.render(cache.bundle().definitions(), objId, objectType.get(),
                    objectRotation.get(), previewYaw, previewPitch, previewZoom, size, size);

            ImGui.setCursorScreenPos(cx + (panelW - size) * 0.5f, cy + 2.0f);
            if (texture != 0) {
                ImGui.image((long) texture, size, size);
            } else {
                draw.addText(StudioFonts.ui(), 12, cx + 12, cy + PREVIEW_HEIGHT * 0.5f - 16.0f,
                        StudioDrawColors.abgr(0xFFF59E0B), "No renderable model for #" + objId);
                draw.addText(StudioFonts.mono(), 11, cx + 12, cy + PREVIEW_HEIGHT * 0.5f + 2.0f,
                        StudioDrawColors.abgr(0xFF94A3B8), "(varbit/varp-driven appearance with no live game state,");
                draw.addText(StudioFonts.mono(), 11, cx + 12, cy + PREVIEW_HEIGHT * 0.5f + 16.0f,
                        StudioDrawColors.abgr(0xFF94A3B8), "and no configured default - nothing to fall back to)");
            }

            // Drag-anywhere-on-the-preview to orbit; the invisible button
            // both hosts the drag gesture and doubles as the drop source.
            ImGui.setCursorScreenPos(cx + (panelW - size) * 0.5f, cy + 2.0f);
            ImGui.invisibleButton("##preview-3d-" + objId, size, size);
            boolean previewHovered = ImGui.isItemHovered();
            if (ImGui.isItemActive() && ImGui.isMouseDragging(0)) {
                previewYaw -= ImGui.getMouseDragDeltaX() * 0.01f;
                previewPitch = clamp(previewPitch + ImGui.getMouseDragDeltaY() * 0.01f, -1.4f, 1.4f);
                ImGui.resetMouseDragDelta();
            }
            if (previewHovered) {
                float wheel = ImGui.getIO().getMouseWheel();
                if (wheel != 0.0f) {
                    // Scrolling "up" (positive wheel) should move the camera
                    // closer, so it shrinks the distance multiplier.
                    previewZoom = clamp(previewZoom * (1.0f - wheel * 0.1f), 0.35f, 4.0f);
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
                ImGui.setTooltip("Drag to rotate, scroll to zoom - drop on the 3D viewport to spawn");
            }

            ImGui.setCursorScreenPos(cx, cy + PREVIEW_HEIGHT + 4.0f);
        }

        String objName = cache == null || objId < 0 ? null
                : cache.bundle().definitions().object(objId).map(ObjectDefinitionView::name)
                        .filter(n -> !n.isBlank()).orElse(null);
        if (objId >= 0) {
            ImGui.text((objName == null ? "Unnamed" : objName) + "  #" + objId);
        }

        ImGui.beginDisabled(objId < 0);
        if (ImGui.button(StudioIcons.ADD_OBJECT + " Add game object##add-obj", panelW * 0.5f - 4.0f, 24.0f)) {
            settings.set(EditorSettingKeys.OBJECT_ID, objId);
            settings.set(EditorSettingKeys.OBJECT_TYPE, objectType.get());
            settings.set(EditorSettingKeys.OBJECT_ROTATION, objectRotation.get());
            if (context.activateTool() != null) {
                context.activateTool().accept("object.place");
            }
        }
        ImGui.endDisabled();
        ImGui.sameLine();
        ImGui.textDisabled(StudioIcons.OPEN_IN_NEW + " Drag preview to Viewport");

        ImGui.pushItemWidth(panelW * 0.48f);
        if (ImGui.combo("##place-type", objectType, OBJECT_TYPES)) {
            settings.set(EditorSettingKeys.OBJECT_TYPE, objectType.get());
        }
        ImGui.sameLine();
        if (ImGui.combo("##place-rot", objectRotation, ROTATIONS)) {
            settings.set(EditorSettingKeys.OBJECT_ROTATION, objectRotation.get());
        }
        ImGui.popItemWidth();
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
                if (def.name() != null && !def.name().isBlank()) name = def.name();
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
                        if (defOpt.isEmpty() || defOpt.get().name() == null
                                || !defOpt.get().name().toLowerCase().contains(query)) {
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
        ImGui.inputInt("Object ID##prop-obj-id", selectedObjectId);
        int id = selectedObjectId.get();
        if (id != lastPropertiesObjectId) {
            scalarEditStates.clear();
            definitionEditStatus = "";
            lastPropertiesObjectId = id;
        }

        if (cache == null) {
            ImGui.textDisabled("Load an OSRS cache to inspect object definitions.");
            return;
        }

        var definitions = cache.bundle().definitions();
        definitions.object(id).ifPresentOrElse(def -> {
            ObjectDefinitionEditTransaction transaction = definitionTransaction(cache, id);
            ObjectDefinitionRawView raw = transaction == null
                    ? definitions.objectRaw(id).orElse(null)
                    : transaction.preview();

            String previewName = rawField(raw, "name")
                    .map(ObjectDefinitionRawView.Field::value)
                    .orElse(def.name());
            int previewSizeX = rawInt(raw, "sizeX").orElse(def.width());
            int previewSizeY = rawInt(raw, "sizeY").orElse(def.length());

            ImGui.pushFont(StudioFonts.mono(), 0.0f);
            ImGui.textColored(0xFF38BDF8,
                    previewName == null || previewName.isEmpty() ? "(unnamed)" : previewName);
            ImGui.text("Size:        " + previewSizeX + " x " + previewSizeY);
            ImGui.text("Interactive: " + def.interactive());
            ImGui.text("Models:      " + java.util.Arrays.toString(def.modelIds()));
            ImGui.text("Actions:     " + String.join(", ",
                    def.interactions().stream().filter(a -> !a.isBlank()).toList()));
            ImGui.popFont();

            if (transaction != null) {
                renderDefinitionTransactionEditor(context, transaction);
                raw = transaction.preview();
            } else {
                ImGui.separator();
                ImGui.textDisabled(
                        "This cache backend does not expose object-definition edit transactions.");
            }

            ImGui.separator();
            ImGui.textDisabled(transaction == null
                    ? "Raw decoded definition"
                    : "In-memory decoded preview");
            ImGui.inputTextWithHint("##raw-object-filter",
                    StudioIcons.SEARCH + "  Filter field, opcode, param or value...",
                    rawPropertyFilter);

            if (raw == null) {
                ImGui.textDisabled(
                        "Raw definition metadata is unavailable for this cache backend.");
            } else {
                renderRawObjectDefinition(
                        raw,
                        transaction == null ? Set.of() : transaction.dirtyFields(),
                        transaction == null ? Set.of() : transaction.dirtyParams());
            }
        }, () -> ImGui.textDisabled("Object definition not found for ID " + id));
    }

    private void renderDefinitionTransactionEditor(StudioPanelContext context,
                                                   ObjectDefinitionEditTransaction transaction) {
        ImGui.pushID(transaction.id());
        ImGui.separator();
        ImGui.text("Definition edit transaction");
        if (transaction.dirty()) {
            ImGui.sameLine();
            ImGui.textColored(0xFF38BDF8,
                    "* " + transaction.dirtyFields().size() + " fields, "
                            + transaction.dirtyParams().size() + " params modified");
        } else {
            ImGui.sameLine();
            ImGui.textDisabled("clean");
        }

        ImGui.textDisabled(
                "Preview only. The source cache remains read-only until an explicit output-cache phase.");

        boolean canEdit = context.session() != null && context.session().canEdit();
        if (!canEdit) {
            ImGui.textDisabled("Open an editable Studio session to modify this transaction.");
        }

        ImGui.beginDisabled(!canEdit);
        renderScalarField(context, transaction, "name", "Name");
        renderScalarField(context, transaction, "sizeX", "Size X");
        renderScalarField(context, transaction, "sizeY", "Size Y");
        renderScalarField(context, transaction, "animationId", "Animation ID");
        renderBooleanField(context, transaction, "isHollow", "Hollow");
        renderBooleanField(context, transaction, "isRotated", "Rotated");
        renderParamEditor(context, transaction);
        ImGui.endDisabled();

        if (ImGui.button("Validate encoded preview##definition-validate")) {
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
        ImGui.popID();
    }

    private void renderScalarField(StudioPanelContext context,
                                   ObjectDefinitionEditTransaction transaction,
                                   String fieldName,
                                   String label) {
        Optional<ObjectDefinitionRawView.Field> currentField =
                rawField(transaction.preview(), fieldName);
        if (currentField.isEmpty()) return;

        ObjectDefinitionRawView.Field field = currentField.get();
        if (!isTextEditableScalar(field.type())) return;

        String key = "field:" + transaction.id() + ":" + fieldName;
        ScalarEditState state = scalarEditStates.computeIfAbsent(
                key, ignored -> new ScalarEditState());
        if (!state.editing && !Objects.equals(state.syncedValue, field.value())) {
            state.sync(field.value());
        }

        boolean dirty = transaction.dirtyFields().contains(fieldName);
        ImGui.inputText(
                (dirty ? "* " : "") + label + "##definition-" + fieldName,
                state.input);
        boolean activated = ImGui.isItemActivated();
        boolean deactivated = ImGui.isItemDeactivated();

        if (activated) {
            state.editing = true;
            state.before = editValue(field);
            state.error = "";
        }

        if (deactivated && state.editing) {
            finishScalarFieldEdit(context, transaction, fieldName, field.type(), state);
        }

        if (!state.error.isBlank()) {
            ImGui.textColored(0xFF60A5FA, state.error);
        }
    }

    private void finishScalarFieldEdit(StudioPanelContext context,
                                       ObjectDefinitionEditTransaction transaction,
                                       String fieldName,
                                       ObjectDefinitionRawView.ValueType type,
                                       ScalarEditState state) {
        ObjectDefinitionEditValue before = state.before;
        try {
            ObjectDefinitionEditValue after = editValue(type, state.input.get());
            if (!Objects.equals(before, after)) {
                context.session().execute(ObjectDefinitionEditCommand.field(
                        transaction, fieldName, before, after));
                definitionEditStatus = "Updated " + fieldName + " in object "
                        + transaction.id() + " preview.";
            }
            ObjectDefinitionRawView.Field canonical = rawField(
                    transaction.preview(), fieldName).orElseThrow();
            state.sync(canonical.value());
            state.error = "";
        } catch (RuntimeException failure) {
            rawField(transaction.preview(), fieldName)
                    .ifPresent(restored -> state.sync(restored.value()));
            state.error = failureMessage(failure);
            definitionEditStatus = "Rejected " + fieldName + " edit: " + state.error;
        } finally {
            state.editing = false;
            state.before = null;
        }
    }

    private void renderBooleanField(StudioPanelContext context,
                                    ObjectDefinitionEditTransaction transaction,
                                    String fieldName,
                                    String label) {
        Optional<ObjectDefinitionRawView.Field> currentField =
                rawField(transaction.preview(), fieldName);
        if (currentField.isEmpty()
                || currentField.get().type() != ObjectDefinitionRawView.ValueType.BOOLEAN) {
            return;
        }

        boolean current = Boolean.parseBoolean(currentField.get().value());
        boolean dirty = transaction.dirtyFields().contains(fieldName);
        if (ImGui.checkbox((dirty ? "* " : "") + label + "##definition-" + fieldName,
                current)) {
            ObjectDefinitionEditValue before =
                    ObjectDefinitionEditValue.booleanValue(current);
            ObjectDefinitionEditValue after =
                    ObjectDefinitionEditValue.booleanValue(!current);
            try {
                context.session().execute(ObjectDefinitionEditCommand.field(
                        transaction, fieldName, before, after));
                definitionEditStatus = "Updated " + fieldName + " in object "
                        + transaction.id() + " preview.";
            } catch (RuntimeException failure) {
                definitionEditStatus = "Rejected " + fieldName + " edit: "
                        + failureMessage(failure);
            }
        }
    }

    private void renderParamEditor(StudioPanelContext context,
                                   ObjectDefinitionEditTransaction transaction) {
        ImGui.separator();
        ObjectDefinitionRawView preview = transaction.preview();
        ImGui.text("Opcode 249 parameters");
        if (preview.params().isEmpty()) {
            ImGui.textDisabled("No parameters in this definition.");
        }

        for (ObjectDefinitionRawView.Param param : preview.params()) {
            ImGui.pushID(param.id());
            ImGui.textDisabled("#" + param.id() + "  "
                    + param.type().name().toLowerCase(java.util.Locale.ROOT));
            ImGui.sameLine();
            boolean removed = false;
            if (ImGui.smallButton("Remove##definition-param")) {
                try {
                    context.session().execute(ObjectDefinitionEditCommand.param(
                            transaction, param.id(), editValue(param), null));
                    scalarEditStates.remove(paramStateKey(transaction.id(), param.id()));
                    definitionEditStatus = "Removed param " + param.id()
                            + " from object " + transaction.id() + " preview.";
                    removed = true;
                } catch (RuntimeException failure) {
                    definitionEditStatus = "Param removal failed: "
                            + failureMessage(failure);
                }
            }

            if (!removed) {
                renderParamValue(context, transaction, param);
            }
            ImGui.popID();
        }

        ImGui.separator();
        ImGui.textDisabled("Add or replace parameter");
        ImGui.inputInt("Param ID##definition-param-id", newParamId);
        ImGui.combo("Type##definition-param-type", newParamType, PARAM_TYPES);
        ImGui.inputText("Value##definition-param-value", newParamValue);
        if (ImGui.button("Add / Replace##definition-param-add")) {
            try {
                int paramId = newParamId.get();
                ObjectDefinitionEditValue after = editValue(
                        paramType(newParamType.get()), newParamValue.get());
                ObjectDefinitionEditValue before = transaction.preview().params().stream()
                        .filter(param -> param.id() == paramId)
                        .findFirst()
                        .map(ObjectViewerPanel::editValue)
                        .orElse(null);
                if (!Objects.equals(before, after)) {
                    context.session().execute(ObjectDefinitionEditCommand.param(
                            transaction, paramId, before, after));
                    scalarEditStates.remove(paramStateKey(transaction.id(), paramId));
                    definitionEditStatus = (before == null ? "Added param " : "Replaced param ")
                            + paramId + " in object " + transaction.id() + " preview.";
                }
            } catch (RuntimeException failure) {
                definitionEditStatus = "Param edit failed: " + failureMessage(failure);
            }
        }
    }

    private void renderParamValue(StudioPanelContext context,
                                  ObjectDefinitionEditTransaction transaction,
                                  ObjectDefinitionRawView.Param param) {
        if (param.type() != ObjectDefinitionRawView.ValueType.STRING
                && param.type() != ObjectDefinitionRawView.ValueType.INTEGER
                && param.type() != ObjectDefinitionRawView.ValueType.LONG) {
            ImGui.textDisabled(compactRawValue(param.value()));
            return;
        }

        String key = paramStateKey(transaction.id(), param.id());
        ScalarEditState state = scalarEditStates.computeIfAbsent(
                key, ignored -> new ScalarEditState());
        if (!state.editing && !Objects.equals(state.syncedValue, param.value())) {
            state.sync(param.value());
        }

        boolean dirty = transaction.dirtyParams().contains(param.id());
        ImGui.inputText(
                (dirty ? "* Value" : "Value") + "##definition-param-value",
                state.input);
        boolean activated = ImGui.isItemActivated();
        boolean deactivated = ImGui.isItemDeactivated();

        if (activated) {
            state.editing = true;
            state.before = editValue(param);
            state.error = "";
        }

        if (deactivated && state.editing) {
            ObjectDefinitionEditValue before = state.before;
            try {
                ObjectDefinitionEditValue after =
                        editValue(param.type(), state.input.get());
                if (!Objects.equals(before, after)) {
                    context.session().execute(ObjectDefinitionEditCommand.param(
                            transaction, param.id(), before, after));
                    definitionEditStatus = "Updated param " + param.id()
                            + " in object " + transaction.id() + " preview.";
                }
                ObjectDefinitionEditValue canonical = transaction.preview().params().stream()
                        .filter(candidate -> candidate.id() == param.id())
                        .findFirst()
                        .map(ObjectViewerPanel::editValue)
                        .orElseThrow();
                state.sync(canonical.value());
                state.error = "";
            } catch (RuntimeException failure) {
                state.sync(before == null ? "" : before.value());
                state.error = failureMessage(failure);
                definitionEditStatus = "Rejected param " + param.id()
                        + " edit: " + state.error;
            } finally {
                state.editing = false;
                state.before = null;
            }
        }

        if (!state.error.isBlank()) {
            ImGui.textColored(0xFF60A5FA, state.error);
        }
    }

    private void renderRawObjectDefinition(ObjectDefinitionRawView raw,
                                           Set<String> dirtyFields,
                                           Set<Integer> dirtyParams) {
        String filter = rawPropertyFilter.get().trim().toLowerCase(java.util.Locale.ROOT);

        ImGui.pushFont(StudioFonts.mono(), 0.0f);
        ImGui.textDisabled("OPCODE       FIELD                    TYPE       VALUE");

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
            ImGui.text(formatRawRow(opcode, name,
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
            ImGui.text(formatRawRow(
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

    private void syncDefinitionEditCache(LoadedOsrsCacheSession cache) {
        if (definitionEditCache == cache) return;
        definitionEditCache = cache;
        definitionEdits.clear();
        scalarEditStates.clear();
        definitionEditStatus = "";
    }

    private ObjectDefinitionEditTransaction definitionTransaction(
            LoadedOsrsCacheSession cache, int id) {
        ObjectDefinitionEditTransaction existing = definitionEdits.get(id);
        if (existing != null) return existing;
        ObjectDefinitionEditTransaction created =
                cache.bundle().definitions().editObject(id).orElse(null);
        if (created != null) {
            definitionEdits.put(id, created);
        }
        return created;
    }

    private static Optional<ObjectDefinitionRawView.Field> rawField(
            ObjectDefinitionRawView raw, String name) {
        if (raw == null) return Optional.empty();
        return raw.fields().stream()
                .filter(field -> field.name().equals(name))
                .findFirst();
    }

    private static Optional<Integer> rawInt(ObjectDefinitionRawView raw, String name) {
        return rawField(raw, name).flatMap(field -> {
            try {
                return Optional.of(Integer.parseInt(field.value()));
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        });
    }

    private static boolean isTextEditableScalar(ObjectDefinitionRawView.ValueType type) {
        return type == ObjectDefinitionRawView.ValueType.STRING
                || type == ObjectDefinitionRawView.ValueType.INTEGER
                || type == ObjectDefinitionRawView.ValueType.LONG;
    }

    private static ObjectDefinitionEditValue editValue(ObjectDefinitionRawView.Field field) {
        return editValue(field.type(), field.value());
    }

    private static ObjectDefinitionEditValue editValue(ObjectDefinitionRawView.Param param) {
        return editValue(param.type(), param.value());
    }

    private static ObjectDefinitionEditValue editValue(
            ObjectDefinitionRawView.ValueType type, String value) {
        return switch (type) {
            case STRING -> ObjectDefinitionEditValue.stringValue(value);
            case INTEGER -> ObjectDefinitionEditValue.intValue(
                    Integer.parseInt(value.trim()));
            case LONG -> ObjectDefinitionEditValue.longValue(
                    Long.parseLong(value.trim()));
            case BOOLEAN -> ObjectDefinitionEditValue.booleanValue(
                    Boolean.parseBoolean(value.trim()));
            default -> throw new IllegalArgumentException(
                    "Complex definition value is not scalar-editable: " + type);
        };
    }

    private static ObjectDefinitionRawView.ValueType paramType(int index) {
        return switch (index) {
            case 0 -> ObjectDefinitionRawView.ValueType.STRING;
            case 1 -> ObjectDefinitionRawView.ValueType.INTEGER;
            case 2 -> ObjectDefinitionRawView.ValueType.LONG;
            default -> throw new IllegalArgumentException("Unknown param type index: " + index);
        };
    }

    private static String paramStateKey(int objectId, int paramId) {
        return "param:" + objectId + ":" + paramId;
    }

    private static String failureMessage(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank()
                ? failure.getClass().getSimpleName()
                : message;
    }

    private static final class ScalarEditState {
        private final ImString input = new ImString(512);
        private String syncedValue;
        private boolean editing;
        private ObjectDefinitionEditValue before;
        private String error = "";

        private void sync(String value) {
            input.set(value == null ? "" : value);
            syncedValue = value == null ? "" : value;
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
