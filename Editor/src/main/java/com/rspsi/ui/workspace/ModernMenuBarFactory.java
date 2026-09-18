package com.rspsi.ui.workspace;

import com.rspsi.controllers.MainController;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;

import java.util.Objects;

/** Builds the visible Studio menu from the existing FXML command targets. */
public final class ModernMenuBarFactory {
    private ModernMenuBarFactory() { }

    public static MenuBar create(MainController controller, ControlledWorkspaceShell shell) {
        Objects.requireNonNull(controller, "controller");
        Objects.requireNonNull(shell, "shell");
        MenuBar bar = new MenuBar();
        bar.getStyleClass().add("workspace-menu-bar");

        Menu file = new Menu("File");
        file.getItems().addAll(
                forward("New", controller.getNewMapButton()),
                forward("Open coordinates…", controller.getOpenCoordinateButton()),
                forward("Open region hash…", controller.getOpenHashButton()),
                forward("Open map file…", controller.getOpenFileButton()),
                forward("Open pack…", controller.getOpenAsPackBtn()),
                forward("Open OSRS project…", controller.getOpenOsrsProjectButton()),
                new SeparatorMenuItem(),
                forward("Save", controller.getSaveMenuItem()),
                forward("Save As…", controller.getSaveAsMenuItem()),
                new SeparatorMenuItem(),
                forward("Quit", controller.getQuitMenuItem()));

        Menu edit = new Menu("Edit");
        edit.getItems().addAll(
                forward("Undo", controller.getUndoMenuItem()),
                forward("Redo", controller.getRedoMenuItem()),
                new SeparatorMenuItem(),
                forward("Copy", controller.getCopySelectedTilesBtn()),
                forward("Paste", controller.getPasteTilesBtn()),
                forward("Delete", controller.getDeleteSelectedTilesBtn()));

        Menu workspace = new Menu("Workspace");
        MenuItem reset = new MenuItem("Reset Map Editor layout");
        reset.setOnAction(event -> shell.resetLayout("map"));
        MenuItem drawer = new MenuItem("Toggle utility drawer");
        drawer.setOnAction(event -> shell.toggleUtilityDrawer());
        workspace.getItems().addAll(reset, drawer,
                new SeparatorMenuItem(),
                category("General map settings", shell, "general"),
                category("Rendering", shell, "rendering"),
                category("Terrain", shell, "terrain"),
                category("Objects", shell, "objects"),
                category("Collision / diagnostics", shell, "collision"));

        Menu view = new Menu("View");
        view.getItems().addAll(
                category("Open world outliner", shell, "outliner"),
                category("Open inspector", shell, "inspector"),
                new SeparatorMenuItem(),
                forward("Set view distance…", controller.getChangeViewDist()),
                forward("Show full map", controller.getShowFullMap()),
                forward("Show object view", controller.getShowObjectViewBtn()));

        Menu tools = new Menu("Map");
        tools.getItems().addAll(
                forward("Import prefab", controller.getImportTilesBtn()),
                forward("Export selected tiles", controller.getExportTilesBtn()),
                forward("Generate bridge", controller.getGenerateBridgeBtn()),
                new SeparatorMenuItem(),
                forward("Force map update", controller.getForceMapUpdateBtn()),
                forward("Reset tile heights", controller.getFixHeightsBtn()),
                forward("Show map index editor", controller.getShowMapIndexEditor()),
                forward("Reload swatches", controller.getReloadSwatchesBtn()),
                forward("Reload models", controller.getReloadModelsBtn()));

        Menu window = new Menu("Window");
        window.getItems().addAll(
                forward("Preferences…", controller.getPreferencesMenuItem()),
                new SeparatorMenuItem(),
                forward("Remember editor size", controller.getRememberSize()),
                forward("Remember editor location", controller.getRememberLocation()),
                forward("Save by group name", controller.getSaveByGroupName()));

        Menu help = new Menu("Help");
        help.getItems().add(forward("Contact and support", controller.getContactMeBtn()));
        bar.getMenus().addAll(file, edit, workspace, view, tools, window, help);
        return bar;
    }

    private static MenuItem category(String label, ControlledWorkspaceShell shell, String id) {
        MenuItem item = new MenuItem(label);
        item.setOnAction(event -> shell.selectRightCategory(id));
        return item;
    }

    private static MenuItem forward(String label, MenuItem target) {
        if (target == null) {
            MenuItem disabled = new MenuItem(label);
            disabled.setDisable(true);
            return disabled;
        }
        MenuItem proxy;
        if (target instanceof CheckMenuItem checkTarget) {
            CheckMenuItem check = new CheckMenuItem(label);
            check.setSelected(checkTarget.isSelected());
            checkTarget.selectedProperty().addListener((obs, oldValue, newValue) -> {
                if (check.isSelected() != newValue) check.setSelected(newValue);
            });
            check.selectedProperty().addListener((obs, oldValue, newValue) -> {
                if (checkTarget.isSelected() != newValue) checkTarget.setSelected(newValue);
            });
            proxy = check;
        } else {
            proxy = new MenuItem(label);
        }
        proxy.setDisable(target.isDisable());
        proxy.setOnAction(event -> target.fire());
        return proxy;
    }
}
