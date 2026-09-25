package com.rspsi.editor.plugin.ui;

import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Frontend-neutral interactive UI tree for extension-owned Studio surfaces.
 *
 * <p>Nodes contain semantic values and callbacks only. They intentionally do
 * not expose Dear ImGui, GLFW, OpenGL, or native Studio widget classes.</p>
 */
public sealed interface EditorUiNode permits
        EditorUiNode.Text,
        EditorUiNode.Button,
        EditorUiNode.Toggle,
        EditorUiNode.IntSlider,
        EditorUiNode.DecimalSlider,
        EditorUiNode.Select,
        EditorUiNode.Section,
        EditorUiNode.Row,
        EditorUiNode.Column,
        EditorUiNode.Separator {

    record Text(Supplier<String> value, boolean muted) implements EditorUiNode {
        public Text {
            Objects.requireNonNull(value, "text value");
        }

        public Text(String value) {
            this(() -> value == null ? "" : value, false);
        }

        public Text(String value, boolean muted) {
            this(() -> value == null ? "" : value, muted);
        }
    }

    record Button(String label, Runnable action) implements EditorUiNode {
        public Button {
            label = requireText(label, "button label");
            Objects.requireNonNull(action, "button action");
        }
    }

    record Toggle(String label, BooleanSupplier value, Consumer<Boolean> onChange)
            implements EditorUiNode {
        public Toggle {
            label = requireText(label, "toggle label");
            Objects.requireNonNull(value, "toggle value");
            Objects.requireNonNull(onChange, "toggle change callback");
        }
    }

    record IntSlider(String label, int minimum, int maximum,
                     IntSupplier value, IntConsumer onChange) implements EditorUiNode {
        public IntSlider {
            label = requireText(label, "integer slider label");
            if (minimum > maximum) throw new IllegalArgumentException("Integer slider range is invalid");
            Objects.requireNonNull(value, "integer slider value");
            Objects.requireNonNull(onChange, "integer slider callback");
        }
    }

    record DecimalSlider(String label, double minimum, double maximum,
                         DoubleSupplier value, DoubleConsumer onChange) implements EditorUiNode {
        public DecimalSlider {
            label = requireText(label, "decimal slider label");
            if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || minimum > maximum) {
                throw new IllegalArgumentException("Decimal slider range is invalid");
            }
            Objects.requireNonNull(value, "decimal slider value");
            Objects.requireNonNull(onChange, "decimal slider callback");
        }
    }

    record Select(String label, Supplier<String> value, List<String> options,
                  Consumer<String> onChange) implements EditorUiNode {
        public Select {
            label = requireText(label, "select label");
            Objects.requireNonNull(value, "select value");
            options = List.copyOf(options == null ? List.of() : options);
            if (options.isEmpty() || options.stream().anyMatch(option -> option == null || option.isBlank())) {
                throw new IllegalArgumentException("Select options cannot be empty or blank");
            }
            Objects.requireNonNull(onChange, "select callback");
        }
    }

    record Section(String title, List<EditorUiNode> children) implements EditorUiNode {
        public Section {
            title = requireText(title, "section title");
            children = immutableChildren(children);
        }
    }

    record Row(List<EditorUiNode> children) implements EditorUiNode {
        public Row {
            children = immutableChildren(children);
        }
    }

    record Column(List<EditorUiNode> children) implements EditorUiNode {
        public Column {
            children = immutableChildren(children);
        }
    }

    record Separator() implements EditorUiNode {
    }

    static EditorUiNode text(String value) {
        return new Text(value);
    }

    static EditorUiNode muted(String value) {
        return new Text(value, true);
    }

    static EditorUiNode button(String label, Runnable action) {
        return new Button(label, action);
    }

    static EditorUiNode toggle(String label, BooleanSupplier value, Consumer<Boolean> onChange) {
        return new Toggle(label, value, onChange);
    }

    static EditorUiNode section(String title, EditorUiNode... children) {
        return new Section(title, List.of(children));
    }

    static EditorUiNode row(EditorUiNode... children) {
        return new Row(List.of(children));
    }

    static EditorUiNode column(EditorUiNode... children) {
        return new Column(List.of(children));
    }

    private static List<EditorUiNode> immutableChildren(List<EditorUiNode> values) {
        List<EditorUiNode> children = List.copyOf(values == null ? List.of() : values);
        if (children.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("UI children cannot contain null");
        }
        return children;
    }

    private static String requireText(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " cannot be empty");
        return normalized;
    }
}
