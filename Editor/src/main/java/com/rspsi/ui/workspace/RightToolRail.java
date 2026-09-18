package com.rspsi.ui.workspace;

import com.rspsi.ui.StudioIcon;
import com.rspsi.ui.StudioIconFactory;
import javafx.geometry.Insets;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** Compact category rail for the settings/inspection panel beside the viewport. */
public final class RightToolRail extends VBox {
    public record Category(String id, String label, StudioIcon icon) {
        public Category {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(icon, "icon");
        }
    }

    private static final List<Category> CATEGORIES = List.of(
            new Category("general", "General map settings", StudioIcon.SETTINGS),
            new Category("rendering", "Rendering", StudioIcon.BUILD),
            new Category("terrain", "Terrain", StudioIcon.TERRAIN),
            new Category("objects", "Objects", StudioIcon.OBJECT),
            new Category("collision", "Collision and diagnostics", StudioIcon.VALIDATION),
            new Category("outliner", "World outliner", StudioIcon.ASSETS),
            new Category("inspector", "Inspector", StudioIcon.INSPECTOR));

    private final ToggleGroup group = new ToggleGroup();
    private final Map<ToggleButton, String> categoryIds = new LinkedHashMap<>();
    private Consumer<String> selectionListener = ignored -> { };

    public RightToolRail() {
        setSpacing(6);
        setPadding(new Insets(8, 5, 8, 5));
        getStyleClass().add("workspace-right-tool-rail");
        setAccessibleText("Map settings categories");

        for (Category category : CATEGORIES) {
            ToggleButton button = new ToggleButton();
            button.setGraphic(StudioIconFactory.icon(category.icon()));
            button.setAccessibleText(category.label());
            button.setTooltip(new Tooltip(category.label()));
            button.setToggleGroup(group);
            button.getStyleClass().add("workspace-category-button");
            button.setOnAction(event -> selectionListener.accept(category.id()));
            getChildren().add(button);
            categoryIds.put(button, category.id());
            if ("general".equals(category.id())) button.setSelected(true);
        }
    }

    public void onCategorySelected(Consumer<String> listener) {
        selectionListener = Objects.requireNonNull(listener, "listener");
    }

    public void select(String categoryId) {
        for (var node : getChildren()) {
            if (!(node instanceof ToggleButton button)) continue;
            if (categoryId.equals(categoryIds.get(button))) {
                button.setSelected(true);
                selectionListener.accept(categoryId);
                return;
            }
        }
    }
}
