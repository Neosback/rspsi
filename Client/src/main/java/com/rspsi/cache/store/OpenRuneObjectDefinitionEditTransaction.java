package com.rspsi.cache.store;

import com.rspsi.cache.definition.ObjectDefinitionEditTransaction;
import com.rspsi.cache.definition.ObjectDefinitionEditValue;
import com.rspsi.cache.definition.ObjectDefinitionRawView;
import dev.openrune.definition.codec.ObjectCodec;
import dev.openrune.definition.type.ObjectType;
import dev.openrune.definition.type.builders.ObjectTypeBuilder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** OpenRune-backed in-memory object-definition transaction. */
final class OpenRuneObjectDefinitionEditTransaction
        implements ObjectDefinitionEditTransaction {

    private final ObjectType source;
    private final ObjectCodec codec;
    private final ObjectDefinitionRawView original;
    private ObjectTypeBuilder builder;
    private ObjectDefinitionRawView cachedPreview;
    private ObjectDefinitionRawView publishedPreview;

    OpenRuneObjectDefinitionEditTransaction(ObjectType source, int revision) {
        this.source = Objects.requireNonNull(source, "source");
        if (revision <= 0) {
            throw new IllegalArgumentException("OSRS revision must be positive");
        }
        this.codec = new ObjectCodec(revision);
        this.original = OpenRuneDefinitionProvider.toRawView(source);
        this.builder = source.toBuilder();
    }

    @Override
    public int id() {
        return source.getId();
    }

    @Override
    public ObjectDefinitionRawView original() {
        return original;
    }

    @Override
    public ObjectDefinitionRawView preview() {
        if (cachedPreview == null) {
            cachedPreview = OpenRuneDefinitionProvider.toRawView(builder.build());
        }
        return cachedPreview;
    }

    @Override
    public Set<String> dirtyFields() {
        Map<String, ObjectDefinitionRawView.Field> before = fieldsByName(original);
        Map<String, ObjectDefinitionRawView.Field> after = fieldsByName(preview());

        LinkedHashSet<String> dirty = new LinkedHashSet<>();
        LinkedHashSet<String> names = new LinkedHashSet<>(before.keySet());
        names.addAll(after.keySet());
        for (String name : names) {
            ObjectDefinitionRawView.Field beforeField = before.get(name);
            ObjectDefinitionRawView.Field afterField = after.get(name);
            ObjectDefinitionRawView.Field candidate =
                    afterField == null ? beforeField : afterField;
            if (candidate == null || !isScalar(candidate.type())) {
                continue;
            }
            if (!Objects.equals(beforeField, afterField)) {
                dirty.add(name);
            }
        }
        return Set.copyOf(dirty);
    }

    @Override
    public Set<Integer> dirtyParams() {
        Map<Integer, ObjectDefinitionRawView.Param> before = paramsById(original);
        Map<Integer, ObjectDefinitionRawView.Param> after = paramsById(preview());

        LinkedHashSet<Integer> dirty = new LinkedHashSet<>();
        LinkedHashSet<Integer> ids = new LinkedHashSet<>(before.keySet());
        ids.addAll(after.keySet());
        for (int id : ids) {
            if (!Objects.equals(before.get(id), after.get(id))) {
                dirty.add(id);
            }
        }
        return Set.copyOf(dirty);
    }

    @Override
    public boolean hasUnpublishedChanges() {
        ObjectDefinitionRawView current = preview();
        if (current.equals(original)) {
            return false;
        }
        return publishedPreview == null || !current.equals(publishedPreview);
    }

    @Override
    public void markPublished(ObjectDefinitionRawView publishedPreview) {
        ObjectDefinitionRawView checked =
                Objects.requireNonNull(publishedPreview, "publishedPreview");
        if (checked.id() != id()) {
            throw new IllegalArgumentException(
                    "Published object definition id " + checked.id()
                            + " does not match transaction " + id());
        }
        this.publishedPreview = checked;
    }

    @Override
    public void setField(String fieldName, ObjectDefinitionEditValue value) {
        Objects.requireNonNull(fieldName, "fieldName");
        Objects.requireNonNull(value, "value");

        String name = fieldName.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Object field name cannot be blank");
        }
        if (name.equals("id") || name.equals("params")) {
            throw new IllegalArgumentException("Object field is not scalar-editable: " + name);
        }

        ObjectDefinitionRawView.Field field = preview().fields().stream()
                .filter(candidate -> candidate.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown object field: " + name));

        if (field.type() != ObjectDefinitionRawView.ValueType.STRING
                && field.type() != ObjectDefinitionRawView.ValueType.INTEGER
                && field.type() != ObjectDefinitionRawView.ValueType.LONG
                && field.type() != ObjectDefinitionRawView.ValueType.BOOLEAN) {
            throw new IllegalArgumentException(
                    "Complex object field requires a dedicated editor: " + name);
        }

        Method setter = findSetter(name);
        Object backendValue = convertValue(value, setter.getParameterTypes()[0]);
        invoke(setter, builder, backendValue);
        invalidatePreview();
    }

    @Override
    public void putParam(int paramId, ObjectDefinitionEditValue value) {
        validateParamId(paramId);
        Objects.requireNonNull(value, "value");
        if (value.type() == ObjectDefinitionRawView.ValueType.BOOLEAN) {
            throw new IllegalArgumentException(
                    "OSRS opcode 249 params support int, long, or string values");
        }

        Map<Integer, Object> params = mutableParams();
        params.put(paramId, switch (value.type()) {
            case STRING -> value.value();
            case INTEGER -> Integer.parseInt(value.value());
            case LONG -> Long.parseLong(value.value());
            default -> throw new IllegalArgumentException(
                    "Unsupported opcode 249 parameter type: " + value.type());
        });
        builder.setParams(params);
        invalidatePreview();
    }

    @Override
    public void removeParam(int paramId) {
        validateParamId(paramId);
        Map<Integer, Object> params = mutableParams();
        params.remove(paramId);
        builder.setParams(params.isEmpty() ? null : params);
        invalidatePreview();
    }

    @Override
    public void reset() {
        builder = source.toBuilder();
        invalidatePreview();
    }

    @Override
    public byte[] encodeValidated() {
        ObjectType edited = builder.build();
        byte[] first = encode(edited);
        ObjectType decoded = codec.loadData(id(), first);
        byte[] second = encode(decoded);
        if (!Arrays.equals(first, second)) {
            throw new IllegalStateException(
                    "OpenRune object codec round-trip was not canonical for object " + id());
        }
        return first.clone();
    }

    private byte[] encode(ObjectType definition) {
        ByteBuf buffer = Unpooled.buffer(256);
        try {
            codec.encode(buffer, definition);
            byte[] encoded = new byte[buffer.readableBytes()];
            buffer.getBytes(buffer.readerIndex(), encoded);
            return encoded;
        } finally {
            buffer.release();
        }
    }

    private void invalidatePreview() {
        cachedPreview = null;
    }

    private Method findSetter(String fieldName) {
        String stem = fieldName;
        if (fieldName.startsWith("is")
                && fieldName.length() > 2
                && Character.isUpperCase(fieldName.charAt(2))) {
            stem = fieldName.substring(2);
        }
        String setterName = "set" + Character.toUpperCase(stem.charAt(0))
                + stem.substring(1);

        return Arrays.stream(ObjectTypeBuilder.class.getMethods())
                .filter(method -> method.getName().equals(setterName))
                .filter(method -> method.getParameterCount() == 1)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Object field does not expose a builder setter: " + fieldName));
    }

    private static Object convertValue(ObjectDefinitionEditValue value, Class<?> targetType) {
        if (targetType == String.class) {
            requireType(value, ObjectDefinitionRawView.ValueType.STRING);
            return value.value();
        }
        if (targetType == int.class || targetType == Integer.class) {
            requireType(value, ObjectDefinitionRawView.ValueType.INTEGER);
            return Integer.parseInt(value.value());
        }
        if (targetType == long.class || targetType == Long.class) {
            if (value.type() != ObjectDefinitionRawView.ValueType.INTEGER
                    && value.type() != ObjectDefinitionRawView.ValueType.LONG) {
                throw new IllegalArgumentException(
                        "Expected integer/long edit value for long field");
            }
            return Long.parseLong(value.value());
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            requireType(value, ObjectDefinitionRawView.ValueType.BOOLEAN);
            return Boolean.parseBoolean(value.value());
        }
        throw new IllegalArgumentException(
                "Unsupported scalar builder field type: " + targetType.getName());
    }

    private static void requireType(ObjectDefinitionEditValue value,
                                    ObjectDefinitionRawView.ValueType expected) {
        if (value.type() != expected) {
            throw new IllegalArgumentException(
                    "Expected " + expected + " edit value, got " + value.type());
        }
    }

    private Map<Integer, Object> mutableParams() {
        Map<Integer, Object> current = builder.getParams();
        return current == null ? new TreeMap<>() : new TreeMap<>(current);
    }

    private static boolean isScalar(ObjectDefinitionRawView.ValueType type) {
        return type == ObjectDefinitionRawView.ValueType.STRING
                || type == ObjectDefinitionRawView.ValueType.INTEGER
                || type == ObjectDefinitionRawView.ValueType.LONG
                || type == ObjectDefinitionRawView.ValueType.BOOLEAN;
    }

    private static Map<String, ObjectDefinitionRawView.Field> fieldsByName(
            ObjectDefinitionRawView view) {
        Map<String, ObjectDefinitionRawView.Field> result = new HashMap<>();
        for (ObjectDefinitionRawView.Field field : view.fields()) {
            result.put(field.name(), field);
        }
        return result;
    }

    private static Map<Integer, ObjectDefinitionRawView.Param> paramsById(
            ObjectDefinitionRawView view) {
        Map<Integer, ObjectDefinitionRawView.Param> result = new HashMap<>();
        for (ObjectDefinitionRawView.Param param : view.params()) {
            result.put(param.id(), param);
        }
        return result;
    }

    private static void validateParamId(int paramId) {
        if (paramId < 0 || paramId > 0xFFFFFF) {
            throw new IllegalArgumentException(
                    "Param id outside unsigned-medium range: " + paramId);
        }
    }

    private static Object invoke(Method method, Object target, Object value) {
        try {
            return method.invoke(target, value);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException(
                    "Unable to access OpenRune object builder field " + method.getName(),
                    exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(
                    "OpenRune object builder rejected field " + method.getName(),
                    cause);
        }
    }
}
