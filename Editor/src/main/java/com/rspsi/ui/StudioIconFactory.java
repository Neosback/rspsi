package com.rspsi.ui;

import org.kordamp.ikonli.javafx.FontIcon;

import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;

import java.util.Objects;

/** Creates consistently sized, accessible icon controls for the shell. */
public final class StudioIconFactory {
    private StudioIconFactory() {
    }

    public static FontIcon icon(StudioIcon icon) {
        Objects.requireNonNull(icon, "icon");
        FontIcon result = new FontIcon(icon.glyph());
        result.setIconSize(16);
        result.getStyleClass().add("studio-icon");
        return result;
    }

    public static Button button(StudioIcon icon, String label, Runnable action) {
        Objects.requireNonNull(action, "action");
        Button button = new Button();
        button.setGraphic(icon(icon));
        button.setAccessibleText(label);
        button.setTooltip(new Tooltip(label));
        button.getStyleClass().add("studio-icon-button");
        button.setOnAction(event -> action.run());
        return button;
    }
}
