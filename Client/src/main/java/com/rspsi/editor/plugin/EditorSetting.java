package com.rspsi.editor.plugin;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A typed, frontend-neutral setting binding. A JavaFX or ImGui host renders
 * the metadata and calls {@link #setValue(Object)}; the feature owns the
 * backing state through the getter/setter pair.
 */
public final class EditorSetting {
    public enum ValueType { INTEGER, DECIMAL, BOOLEAN, ENUM }

    private final String id;
    private final String label;
    private final ValueType type;
    private final double minimum;
    private final double maximum;
    private final List<String> options;
    private final Supplier<?> getter;
    private final Consumer<Object> setter;

    public EditorSetting(String id, String label, ValueType type,
                         double minimum, double maximum, List<String> options,
                         Supplier<?> getter, Consumer<Object> setter) {
        this.id = text(id, "setting id");
        this.label = text(label, "setting label");
        this.type = Objects.requireNonNull(type, "setting type");
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || minimum > maximum) {
            throw new IllegalArgumentException("Setting range is invalid");
        }
        this.minimum = minimum;
        this.maximum = maximum;
        this.options = List.copyOf(options == null ? List.of() : options);
        if (type == ValueType.ENUM && this.options.isEmpty()) {
            throw new IllegalArgumentException("Enum settings require options");
        }
        if (this.options.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("Setting options cannot be blank");
        }
        this.getter = Objects.requireNonNull(getter, "setting getter");
        this.setter = Objects.requireNonNull(setter, "setting setter");
    }

    public static EditorSetting integer(String id, String label, int minimum, int maximum,
                                        Supplier<Integer> getter, Consumer<Integer> setter) {
        Objects.requireNonNull(setter, "setting setter");
        return new EditorSetting(id, label, ValueType.INTEGER, minimum, maximum, List.of(),
                getter, value -> setter.accept(coerceInteger(value)));
    }

    public static EditorSetting decimal(String id, String label, double minimum, double maximum,
                                        Supplier<Double> getter, Consumer<Double> setter) {
        Objects.requireNonNull(setter, "setting setter");
        return new EditorSetting(id, label, ValueType.DECIMAL, minimum, maximum, List.of(),
                getter, value -> setter.accept(coerceDecimal(value)));
    }

    public static EditorSetting bool(String id, String label,
                                    Supplier<Boolean> getter, Consumer<Boolean> setter) {
        Objects.requireNonNull(setter, "setting setter");
        return new EditorSetting(id, label, ValueType.BOOLEAN, 0, 1, List.of(),
                getter, value -> setter.accept(coerceBoolean(value)));
    }

    public static EditorSetting enumeration(String id, String label, List<String> options,
                                            Supplier<String> getter, Consumer<String> setter) {
        Objects.requireNonNull(setter, "setting setter");
        return new EditorSetting(id, label, ValueType.ENUM, 0, Math.max(0, options.size() - 1),
                options, getter, value -> setter.accept(coerceEnum(value, options)));
    }

    public String id() { return id; }
    public String label() { return label; }
    public ValueType type() { return type; }
    public double minimum() { return minimum; }
    public double maximum() { return maximum; }
    public List<String> options() { return options; }
    public Object value() { return getter.get(); }

    public void setValue(Object value) {
        setter.accept(normalize(value));
    }

    private Object normalize(Object value) {
        return switch (type) {
            case INTEGER -> asInteger(value);
            case DECIMAL -> asDecimal(value);
            case BOOLEAN -> asBoolean(value);
            case ENUM -> asEnum(value, options);
        };
    }

    private Object checkedRange(double value) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException("Setting " + id + " is outside its allowed range");
        }
        return value;
    }

    private Integer asInteger(Object value) {
        Integer integer = coerceInteger(value);
        checkedRange(integer);
        return integer;
    }

    private static Integer coerceInteger(Object value) {
        if (!(value instanceof Number number)) throw new IllegalArgumentException("Expected integer");
        double numeric = number.doubleValue();
        if (!Double.isFinite(numeric) || numeric != Math.rint(numeric)
                || numeric < Integer.MIN_VALUE || numeric > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Expected integer");
        }
        return (int) numeric;
    }

    private Double asDecimal(Object value) {
        Double decimal = coerceDecimal(value);
        checkedRange(decimal);
        return decimal;
    }

    private static Double coerceDecimal(Object value) {
        if (!(value instanceof Number number)) throw new IllegalArgumentException("Expected decimal");
        double numeric = number.doubleValue();
        if (!Double.isFinite(numeric)) throw new IllegalArgumentException("Expected finite decimal");
        return numeric;
    }

    private Boolean asBoolean(Object value) {
        return coerceBoolean(value);
    }

    private static Boolean coerceBoolean(Object value) {
        if (!(value instanceof Boolean booleanValue)) throw new IllegalArgumentException("Expected boolean");
        return booleanValue;
    }

    private String asEnum(Object value, List<String> allowed) {
        return coerceEnum(value, allowed, id);
    }

    private static String coerceEnum(Object value, List<String> allowed) {
        return coerceEnum(value, allowed, "setting");
    }

    private static String coerceEnum(Object value, List<String> allowed, String id) {
        if (!(value instanceof String stringValue) || !allowed.contains(stringValue)) {
            throw new IllegalArgumentException("Expected one of " + allowed + " for " + id);
        }
        return stringValue;
    }

    private static String text(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " cannot be empty");
        return normalized;
    }
}
