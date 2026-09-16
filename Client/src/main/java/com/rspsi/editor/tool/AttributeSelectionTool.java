package com.rspsi.editor.tool;

import com.rspsi.editor.input.PointerButton;
import com.rspsi.editor.input.PointerEvent;
import com.rspsi.editor.model.ObjectCategory;
import com.rspsi.editor.render.OverlayDraw;
import com.rspsi.editor.selection.SelectionQuery;

import java.util.List;

/** Selects all matching objects or tiles in the current document on click. */
public final class AttributeSelectionTool implements EditorTool {
    public enum Target { OBJECTS, TILES }

    private Target target = Target.OBJECTS;
    private ToolContext context;
    private Integer objectId;
    private Integer objectType;
    private Integer objectPlane;
    private Integer objectRotation;
    private ObjectCategory objectCategory;
    private Integer tilePlane;
    private Integer underlayId;
    private Integer overlayId;
    private Integer requiredFlagsMask;

    public Target target() { return target; }
    public void setTarget(Target target) { this.target = java.util.Objects.requireNonNull(target, "target"); }
    public void setObjectId(Integer value) { objectId = value; }
    public void setObjectType(Integer value) { objectType = value; }
    public void setObjectPlane(Integer value) { objectPlane = value; }
    public void setObjectRotation(Integer value) { objectRotation = value; }
    public void setObjectCategory(ObjectCategory value) { objectCategory = value; }
    public void setTilePlane(Integer value) { tilePlane = value; }
    public void setUnderlayId(Integer value) { underlayId = value; }
    public void setOverlayId(Integer value) { overlayId = value; }
    public void setRequiredFlagsMask(Integer value) { requiredFlagsMask = value; }

    @Override public String id() { return "attribute-select"; }
    @Override public void activate(ToolContext context) { this.context = context; }
    @Override public void deactivate() { context = null; }

    @Override public void pointerDown(PointerEvent event) {
        if (context == null || event.button() != PointerButton.PRIMARY) return;
        if (target == Target.OBJECTS) {
            context.session().selection().selectObjects(SelectionQuery.objects(context.session().world(),
                    new SelectionQuery.ObjectFilter(objectId, objectType, objectPlane, objectRotation, objectCategory)));
        } else {
            context.session().selection().selectTiles(SelectionQuery.tiles(context.session().world(),
                    new SelectionQuery.TileFilter(tilePlane, underlayId, overlayId, requiredFlagsMask)));
        }
    }

    @Override public void pointerDrag(PointerEvent event) { }
    @Override public void pointerUp(PointerEvent event) { }
    @Override public ToolInspector inspector() { return () -> List.of(
            new PropertyDescriptor("target", "Select", PropertyDescriptor.ValueType.ENUM, 0, 1)); }
    @Override public void renderOverlay(OverlayDraw draw) { }
}
