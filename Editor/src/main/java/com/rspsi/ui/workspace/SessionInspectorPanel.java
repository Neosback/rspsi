package com.rspsi.ui.workspace;

import com.rspsi.editor.EditorSession;
import com.rspsi.editor.SessionChangeListener;
import com.rspsi.editor.SelectionChangeListener;
import com.rspsi.editor.SessionStateListener;
import com.rspsi.editor.collision.OsrsCollisionBuilder;
import com.rspsi.editor.model.TileCoordinate;
import com.rspsi.editor.model.TileInspectorSnapshot;
import com.rspsi.editor.model.TileSnapshot;
import com.rspsi.editor.model.WorldTileAddress;
import com.rspsi.editor.model.WorldWindow;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.selection.ObjectSelection;
import com.rspsi.editor.selection.ObjectSetSelection;
import com.rspsi.editor.selection.Selection;
import com.rspsi.editor.selection.TileAreaSelection;
import com.rspsi.editor.selection.TileSelection;
import com.rspsi.editor.selection.TileSetSelection;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.util.Objects;

/**
 * JavaFX inspector backed by the neutral selection/session contracts.
 * Coordinates are derived through {@link WorldWindow}; no cache library or
 * renderer object is exposed to the panel.
 */
public final class SessionInspectorPanel extends VBox implements AutoCloseable {
    private final Label status = new Label();
    private final GridPane values = new GridPane();
    private final SessionChangeListener changeListener = ignored -> refreshOnFxThread();
    private final SessionStateListener stateListener = ignored -> refreshOnFxThread();
    private final SelectionChangeListener selectionListener = ignored -> refreshOnFxThread();
    private EditorSession session;
    private WorldWindow window;

    public SessionInspectorPanel() {
        setSpacing(8);
        setPadding(new Insets(12));
        getStyleClass().add("workspace-session-panel");
        setAccessibleText("Selection inspector");
        Label title = new Label("Inspector");
        title.getStyleClass().add("workspace-panel-title");
        status.getStyleClass().add("workspace-panel-status");
        values.setHgap(12);
        values.setVgap(6);
        getChildren().addAll(title, status, values);
        clear("Select a tile or object to inspect it.");
    }

    public void bind(EditorSession session, WorldWindow window) {
        if (this.session != null) {
            this.session.removeChangeListener(changeListener);
            this.session.removeStateListener(stateListener);
            this.session.selection().removeChangeListener(selectionListener);
        }
        this.session = Objects.requireNonNull(session, "session");
        this.window = Objects.requireNonNull(window, "window");
        this.session.addChangeListener(changeListener);
        this.session.addStateListener(stateListener);
        this.session.selection().addChangeListener(selectionListener);
        refresh();
    }

    public void refresh() {
        if (session == null) {
            clear("Waiting for an editor session.");
            return;
        }
        Selection selection = session.selection().current();
        if (selection instanceof TileSelection tile) {
            showTile(tile.coordinate());
        } else if (selection instanceof ObjectSelection object) {
            showObject(object.object());
        } else if (selection instanceof ObjectSetSelection objects) {
            clear("Objects selected: " + objects.objects().size());
        } else if (selection instanceof TileSetSelection tiles) {
            clear("Multiple tiles selected: " + tiles.coordinates().size());
        } else if (selection instanceof TileAreaSelection area) {
            clear("Tile area selected on plane " + area.plane() + ".");
        } else {
            clear("Select a tile or object to inspect it.");
        }
    }

    public void showTile(TileCoordinate coordinate) {
        if (session == null || window == null || !window.contains(coordinate)
                || coordinate.plane() >= session.world().planes()) {
            clear("Selected tile is outside the current world window.");
            return;
        }
        TileSnapshot tile = session.world().tile(coordinate).snapshot();
        WorldTileAddress address = WorldTileAddress.of(window.worldX(coordinate),
                window.worldY(coordinate), coordinate.plane());
        boolean bridge = session.world().planes() > 1
                && (session.world().tile(1, coordinate.x(), coordinate.y()).snapshot().flags()
                & OsrsCollisionBuilder.LINK_BELOW) != 0;
        boolean roof = (tile.flags() & OsrsCollisionBuilder.REMOVE_ROOFS) != 0;
        TileInspectorSnapshot snapshot = new TileInspectorSnapshot(address, tile, bridge, roof);
        status.setText("Tile semantics");
        values.getChildren().clear();
        row("World", snapshot.address().worldX() + ", " + snapshot.address().worldY());
        row("Region", snapshot.address().regionId() + " (" + snapshot.address().regionX()
                + ", " + snapshot.address().regionY() + ")");
        row("Local", snapshot.address().regionLocalX() + ", " + snapshot.address().regionLocalY());
        row("Chunk", snapshot.address().chunkX() + ", " + snapshot.address().chunkY()
                + " / " + snapshot.address().chunkLocalX() + ", " + snapshot.address().chunkLocalY());
        row("Plane", Integer.toString(snapshot.address().plane()));
        row("Heights", tile.southWestHeight() + " / " + tile.southEastHeight() + " / "
                + tile.northEastHeight() + " / " + tile.northWestHeight());
        row("Underlay", Integer.toString(tile.underlayId()));
        row("Overlay", tile.overlayId() + " (shape " + tile.overlayShape() + ", rotation "
                + tile.overlayRotation() + ")");
        row("Flags", String.format("0x%02X", snapshot.rawFlags()));
        row("Bridge", snapshot.bridge() ? "Yes" : "No");
        row("Roof flag", snapshot.roofRelated() ? "Present" : "Absent");
    }

    private void showObject(WorldObject object) {
        clear("Object selection");
        row("ID", Integer.toString(object.id()));
        row("World", object.x() + ", " + object.y());
        row("Plane", Integer.toString(object.plane()));
        row("Type", Integer.toString(object.type()));
        row("Rotation", Integer.toString(object.rotation()));
    }

    private void clear(String message) {
        status.setText(message);
        values.getChildren().clear();
    }

    private void refreshOnFxThread() {
        if (Platform.isFxApplicationThread()) {
            refresh();
        } else {
            Platform.runLater(this::refresh);
        }
    }

    private void row(String name, String value) {
        int row = values.getRowCount();
        Label key = new Label(name);
        key.getStyleClass().add("workspace-property-key");
        Label text = new Label(value);
        text.getStyleClass().add("workspace-property-value");
        text.setWrapText(true);
        values.addRow(row, key, text);
    }

    @Override
    public void close() {
        if (session != null) {
            session.removeChangeListener(changeListener);
            session.removeStateListener(stateListener);
            session.selection().removeChangeListener(selectionListener);
            session = null;
        }
    }
}
