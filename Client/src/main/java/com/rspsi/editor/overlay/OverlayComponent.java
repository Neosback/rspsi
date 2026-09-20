package com.rspsi.editor.overlay;

import java.util.List;
import java.util.Objects;

/**
 * Frontend-neutral declarative HUD component tree. ImGui, JavaFX or another
 * frontend can render the same plugin contribution without exposing native UI
 * classes to the plugin API.
 */
public sealed interface OverlayComponent permits
        OverlayComponent.Text,
        OverlayComponent.Line,
        OverlayComponent.ProgressBar,
        OverlayComponent.ProgressPie,
        OverlayComponent.Table,
        OverlayComponent.Split,
        OverlayComponent.Panel,
        OverlayComponent.InfoBox,
        OverlayComponent.Tooltip {

    record Text(String value, boolean muted) implements OverlayComponent {
        public Text {
            value = value == null ? "" : value;
        }
        public Text(String value) { this(value, false); }
    }

    record Line(String left, String right) implements OverlayComponent {
        public Line {
            left = left == null ? "" : left;
            right = right == null ? "" : right;
        }
    }

    record ProgressBar(double progress, String label) implements OverlayComponent {
        public ProgressBar {
            progress = Math.max(0.0, Math.min(1.0, progress));
            label = label == null ? "" : label;
        }
    }

    record ProgressPie(double progress, String label) implements OverlayComponent {
        public ProgressPie {
            progress = Math.max(0.0, Math.min(1.0, progress));
            label = label == null ? "" : label;
        }
    }

    record Table(List<String> headers, List<List<String>> rows) implements OverlayComponent {
        public Table {
            headers = List.copyOf(headers == null ? List.of() : headers);
            rows = (rows == null ? List.<List<String>>of() : rows).stream()
                    .map(row -> List.copyOf(row == null ? List.of() : row)).toList();
        }
    }

    record Split(OverlayComponent first, OverlayComponent second, double ratio)
            implements OverlayComponent {
        public Split {
            first = Objects.requireNonNull(first, "first");
            second = Objects.requireNonNull(second, "second");
            ratio = Math.max(0.05, Math.min(0.95, ratio));
        }
    }

    record Panel(List<OverlayComponent> children, boolean bordered) implements OverlayComponent {
        public Panel {
            children = List.copyOf(children == null ? List.of() : children);
        }
        public Panel(List<OverlayComponent> children) { this(children, true); }
    }

    record InfoBox(String id, String title, String value, double progress,
                   boolean attention) implements OverlayComponent {
        public InfoBox {
            id = Objects.requireNonNull(id, "id").trim();
            if (id.isEmpty()) throw new IllegalArgumentException("InfoBox id cannot be empty");
            title = title == null ? "" : title;
            value = value == null ? "" : value;
            progress = Math.max(-1.0, Math.min(1.0, progress));
        }

        public static InfoBox value(String id, String title, String value) {
            return new InfoBox(id, title, value, -1.0, false);
        }
    }

    record Tooltip(String text) implements OverlayComponent {
        public Tooltip {
            text = text == null ? "" : text;
        }
    }
}
