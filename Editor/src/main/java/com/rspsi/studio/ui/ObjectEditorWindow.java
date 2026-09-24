package com.rspsi.studio.ui;

import com.rspsi.cache.definition.ObjectDefinitionEditTransaction;
import com.rspsi.cache.definition.ObjectDefinitionRawView;
import com.rspsi.cache.workspace.LoadedOsrsCacheSession;
import com.rspsi.editor.EditorSession;
import com.rspsi.editor.RotateObjectCommand;
import com.rspsi.editor.inspector.ObjectReport;
import com.rspsi.editor.model.OsrsLocShape;
import com.rspsi.editor.render.ObjectPreviewScene;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.studio.theme.StudioDrawColors;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.type.ImBoolean;

import java.util.Objects;

/**
 * "Edit game object" window, opened from the viewport's right-click menu,
 * modelled on Displee's object editor: every decoded definition field grouped
 * and editable on the left, a 3D preview in the middle, the placed instance
 * on the right.
 *
 * <p>Definition edits go through the cache session's edit transaction as
 * undoable commands (see {@link ObjectPropertyTree}), the same path the Object
 * Viewer uses, and change the in-memory preview only. Publishing them to an
 * output cache is still done from the Object Viewer's output section.</p>
 */
public final class ObjectEditorWindow {
    private final ImBoolean open = new ImBoolean(false);
    private final ObjectPreviewRenderer preview = new ObjectPreviewRenderer();
    private final ObjectPropertyTree properties = new ObjectPropertyTree();
    private WorldObject placement;
    private int objectId = -1;
    private float yaw = ObjectPreviewScene.DEFAULT_ORBIT_YAW;
    private float elevation = ObjectPreviewScene.DEFAULT_ELEVATION;
    private float zoom = 1.0f;
    private String status = "";

    /** Opens the editor on one placed object. */
    public void open(WorldObject object) {
        placement = Objects.requireNonNull(object, "object");
        objectId = object.id();
        status = "";
        open.set(true);
    }

    public void render(LoadedOsrsCacheSession cache, EditorSession session) {
        if (!open.get() || cache == null || objectId < 0) return;
        float unit = ImGui.getFontSize();
        ImGui.setNextWindowSize(unit * 78.0f, unit * 44.0f, ImGuiCond.FirstUseEver);
        String name = cache.bundle().definitions().object(objectId)
                .map(def -> def.displayName()).orElse("Object #" + objectId);
        if (!ImGui.begin("Edit game object - " + name + "###object-editor", open)) {
            ImGui.end();
            return;
        }
        ObjectDefinitionEditTransaction transaction = cache.objectDefinitions().transaction(objectId).orElse(null);
        boolean canEdit = session != null && session.canEdit() && transaction != null;

        if (ImGui.beginTable("##object-editor-layout", 3,
                ImGuiTableFlags.Resizable | ImGuiTableFlags.BordersInnerV | ImGuiTableFlags.SizingStretchProp)) {
            ImGui.tableSetupColumn("properties", ImGuiTableColumnFlags.WidthStretch, 0.42f);
            ImGui.tableSetupColumn("preview", ImGuiTableColumnFlags.WidthStretch, 0.33f);
            ImGui.tableSetupColumn("placement", ImGuiTableColumnFlags.WidthStretch, 0.25f);
            ImGui.tableNextRow();

            float bodyHeight = ImGui.getContentRegionAvailY() - ImGui.getFrameHeightWithSpacing() * 1.6f;
            ImGui.tableNextColumn();
            ImGui.beginChild("##object-editor-properties", 0.0f, bodyHeight, false);
            renderProperties(cache, session, transaction, canEdit);
            ImGui.endChild();

            ImGui.tableNextColumn();
            renderPreview(cache, bodyHeight);

            ImGui.tableNextColumn();
            ImGui.beginChild("##object-editor-placement", 0.0f, bodyHeight, false);
            renderPlacement(cache, session);
            ImGui.endChild();
            ImGui.endTable();
        }

        renderFooter(session, transaction, canEdit);
        ImGui.end();
    }

    private void renderProperties(LoadedOsrsCacheSession cache, EditorSession session,
                                  ObjectDefinitionEditTransaction transaction, boolean canEdit) {
        ObjectDefinitionRawView raw = transaction != null ? transaction.preview()
                : cache.bundle().definitions().objectRaw(objectId).orElse(null);
        if (!canEdit) {
            ImGui.textDisabled(transaction == null ? "Read-only: no edit transaction for this cache."
                    : "Read-only: open an editable session to change fields.");
        }
        properties.render("object-editor", new ObjectPropertyTree.Source(objectId, raw,
                cache.bundle().definitions().object(objectId).orElse(null), transaction, session,
                cache.bundle().definitions()));
    }

    private void renderPreview(LoadedOsrsCacheSession cache, float height) {
        float width = ImGui.getContentRegionAvailX();
        int size = (int) Math.max(64.0f, Math.min(width, height - ImGui.getFrameHeightWithSpacing() * 2.0f));
        // TODO(object-editor): preview the edited transaction, not the cache definition. The
        // preview renderer reads DefinitionProvider, which does not see in-memory edits.
        int texture = preview.render(cache.bundle().definitions(), objectId, placement.type(),
                placement.rotation(), yaw, elevation, zoom, size, size);
        if (texture != 0) {
            ImGui.image(texture, size, size);
            if (ImGui.isItemHovered()) {
                if (ImGui.isMouseDragging(ImGuiMouseButton.Left)) {
                    yaw -= ImGui.getIO().getMouseDeltaX() * 0.01f;
                    elevation = Math.max(ObjectPreviewScene.MIN_ELEVATION, Math.min(ObjectPreviewScene.MAX_ELEVATION,
                            elevation + ImGui.getIO().getMouseDeltaY() * 0.01f));
                }
                float wheel = ImGui.getIO().getMouseWheel();
                if (wheel != 0.0f) zoom = Math.max(0.35f, Math.min(4.0f, zoom * (wheel > 0 ? 0.9f : 1.1f)));
                if (ImGui.isMouseDoubleClicked(ImGuiMouseButton.Left)) {
                    yaw = ObjectPreviewScene.DEFAULT_ORBIT_YAW;
                    elevation = ObjectPreviewScene.DEFAULT_ELEVATION;
                    zoom = 1.0f;
                }
            }
            ImGui.textDisabled("Drag to orbit, scroll to zoom, double-click to reset. Grid cells are one tile.");
        } else {
            ImGui.textWrapped("No renderable model for this object in its current state.");
        }
    }

    private void renderPlacement(LoadedOsrsCacheSession cache, EditorSession session) {
        ImGui.textColored(StudioDrawColors.abgr(0xFF38BDF8), "Placed object");
        if (ImGui.beginTable("##oe-placement", 2, ImGuiTableFlags.SizingStretchProp)) {
            row("Position", placement.x() + ", " + placement.y() + "  plane " + placement.plane());
            row("Type", placement.type() + " - " + placement.shape().map(OsrsLocShape::displayName).orElse("?"));
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            ImGui.alignTextToFramePadding();
            ImGui.text("Rotation");
            ImGui.tableNextColumn();
            ImGui.setNextItemWidth(-Float.MIN_VALUE);
            String[] rotations = {"0", "1 (+90 degrees)", "2 (+180 degrees)", "3 (+270 degrees)"};
            imgui.type.ImInt rotation = new imgui.type.ImInt(placement.rotation());
            boolean editable = session != null && session.canEdit();
            ImGui.beginDisabled(!editable);
            if (ImGui.combo("##oe-rotation", rotation, rotations) && rotation.get() != placement.rotation()) {
                session.execute(new RotateObjectCommand(placement, rotation.get(), "Rotate object #" + objectId));
                placement = new WorldObject(placement.id(), placement.type(), rotation.get(),
                        placement.plane(), placement.x(), placement.y());
            }
            ImGui.endDisabled();
            // TODO(object-editor): change the placed shape/type through an undoable replace
            // command once one exists for a single placement.
            ImGui.endTable();
        }
        ImGui.spacing();
        PropertyGrid.render("oe-report", ObjectReport.forPlacement(placement, cache.bundle().definitions()));
    }

    private static void row(String label, String value) {
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.textDisabled(label);
        ImGui.tableNextColumn();
        ImGui.textWrapped(value);
    }

    private void renderFooter(EditorSession session, ObjectDefinitionEditTransaction transaction, boolean canEdit) {
        ImGui.separator();
        String message = !properties.status().isBlank() ? properties.status() : status;
        if (!message.isBlank()) {
            ImGui.textWrapped(message);
        }
        int dirtyFields = transaction == null ? 0 : transaction.dirtyFields().size();
        ImGui.textDisabled(dirtyFields == 0 ? "No unsaved definition edits."
                : dirtyFields + " field(s) edited in the preview.");
        ImGui.sameLine();
        float buttons = ImGui.calcTextSize("Close").x + ImGui.calcTextSize("Save game object").x
                + ImGui.getStyle().getFramePaddingX() * 4.0f + ImGui.getStyle().getItemSpacingX();
        ImGui.setCursorPosX(Math.max(ImGui.getCursorPosX(), ImGui.getWindowContentRegionMaxX() - buttons));
        if (ImGui.button("Close##oe-close")) {
            open.set(false);
        }
        ImGui.sameLine();
        // TODO(object-editor): save from this window. Definition edits already live in the
        // session's ObjectDefinitionEditTransaction; publishing reuses the Object Viewer's
        // ObjectDefinitionOutputCacheBuilder flow (Object Viewer > Properties > output cache).
        ImGui.beginDisabled(true);
        ImGui.button("Save game object##oe-save");
        ImGui.endDisabled();
        if (ImGui.isItemHovered(imgui.flag.ImGuiHoveredFlags.AllowWhenDisabled)) {
            ImGui.setTooltip("Not wired yet: publish edits from Object Viewer > Properties > output cache.");
        }
    }
}
