package com.rspsi.studio.ui;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.ObjectDefinitionEditTransaction;
import com.rspsi.cache.definition.ObjectDefinitionEditValue;
import com.rspsi.cache.definition.ObjectDefinitionRawView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.editor.EditorSession;
import com.rspsi.editor.ObjectDefinitionEditCommand;
import com.rspsi.editor.ObjectDefinitionStructureEditCommand;
import com.rspsi.editor.inspector.ObjectFieldCatalog;
import com.rspsi.editor.model.OsrsLocShape;
import com.rspsi.editor.render.OsrsTerrainColorMath;
import com.rspsi.studio.theme.StudioDrawColors;
import imgui.ImGui;
import imgui.flag.ImGuiTableColumnFlags;
import imgui.flag.ImGuiTableFlags;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Displee-style "Property | Value" tree for one object definition, shared by
 * the Object Viewer's Properties tab and the Edit game object window.
 *
 * <p>Groups and labels come from {@link ObjectFieldCatalog}. Every editable
 * value commits once, when its widget is released, as an undoable command on
 * the session's definition edit transaction
 * ({@link ObjectDefinitionEditCommand} for scalars and params,
 * {@link ObjectDefinitionStructureEditCommand} for lists and options), so
 * Ctrl+Z works and nothing touches the cache until it is published.</p>
 *
 * <p>The tree is one two-column stretch table: it always fits its container,
 * so it never scrolls sideways, and no size is a pixel constant.</p>
 */
public final class ObjectPropertyTree {
    /** Everything the tree reads; {@code transaction == null} renders read-only. */
    public record Source(int objectId, ObjectDefinitionRawView raw, ObjectDefinitionView definition,
                         ObjectDefinitionEditTransaction transaction, EditorSession session,
                         DefinitionProvider definitions) {
        boolean canEdit() {
            return transaction != null && session != null && session.canEdit();
        }
    }

    private static final int ACCENT = 0xFF60A5FA;
    private static final String[] PARAM_TYPES = {"String", "Int", "Long"};
    private static final OsrsLocShape[] SHAPES = Arrays.stream(OsrsLocShape.values())
            .sorted(Comparator.comparingInt(OsrsLocShape::id)).toArray(OsrsLocShape[]::new);
    private static final String[] SHAPE_LABELS = Arrays.stream(SHAPES)
            .map(shape -> shape.id() + " - " + shape.displayName()).toArray(String[]::new);

    private final Map<String, ImString> texts = new HashMap<>();
    private final Map<String, ImInt> ints = new HashMap<>();
    private final Map<String, int[]> sliders = new HashMap<>();
    private final ImInt newParamId = new ImInt(0);
    private final ImInt newParamType = new ImInt(1);
    private final ImString newParamValue = new ImString(256);
    private String activeKey;
    private String status = "";
    private int shownObjectId = Integer.MIN_VALUE;

    /** Last edit result or rejection, for the host to show; empty when there is none. */
    public String status() {
        return status;
    }

    public void render(String id, Source source) {
        if (source.objectId() != shownObjectId) {
            shownObjectId = source.objectId();
            texts.clear();
            ints.clear();
            sliders.clear();
            activeKey = null;
            status = "";
        }
        if (source.raw() == null) {
            ImGui.textDisabled("This cache backend exposes no decoded definition fields.");
            return;
        }
        if (!ImGui.beginTable("##opt-" + id, 2, ImGuiTableFlags.BordersInnerV | ImGuiTableFlags.RowBg
                | ImGuiTableFlags.SizingStretchProp | ImGuiTableFlags.Resizable)) {
            return;
        }
        ImGui.tableSetupColumn("Property", ImGuiTableColumnFlags.WidthStretch, 0.46f);
        ImGui.tableSetupColumn("Value", ImGuiTableColumnFlags.WidthStretch, 0.54f);
        ImGui.tableHeadersRow();

        Map<String, List<ObjectDefinitionRawView.Field>> groups = new LinkedHashMap<>();
        ObjectFieldCatalog.GROUP_ORDER.forEach(group -> groups.put(group, new ArrayList<>()));
        for (ObjectDefinitionRawView.Field field : source.raw().fields()) {
            if (isPartnerField(field.name())) continue;
            groups.get(ObjectFieldCatalog.describe(field.name()).group()).add(field);
        }

        idRow(source);
        for (Map.Entry<String, List<ObjectDefinitionRawView.Field>> group : groups.entrySet()) {
            boolean params = group.getKey().equals(ObjectFieldCatalog.PARAMS);
            if (group.getValue().isEmpty() && !params) continue;
            ImGui.tableNextRow();
            ImGui.tableNextColumn();
            boolean open = ImGui.treeNodeEx(group.getKey() + "##opt-group-" + group.getKey(),
                    ImGuiTreeNodeFlags.DefaultOpen | ImGuiTreeNodeFlags.SpanAllColumns
                            | ImGuiTreeNodeFlags.LabelSpanAllColumns | ImGuiTreeNodeFlags.DrawLinesToNodes);
            if (!open) continue;
            for (ObjectDefinitionRawView.Field field : group.getValue()) {
                ImGui.pushID(field.name());
                renderField(source, field);
                ImGui.popID();
            }
            if (params) renderParams(source);
            ImGui.treePop();
        }
        ImGui.endTable();
    }

    private static void idRow(Source source) {
        label("Id", "Object definition id.", false);
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        ImGui.text(Integer.toString(source.objectId()));
    }

    private static boolean isPartnerField(String name) {
        return name.equals("objectTypes") || name.equals("modifiedColours")
                || name.equals("modifiedTextureColours") || name.equals("params") || name.equals("id");
    }

    private void renderField(Source source, ObjectDefinitionRawView.Field field) {
        ObjectFieldCatalog.FieldInfo info = ObjectFieldCatalog.describe(field.name());
        switch (field.name()) {
            case "actions" -> {
                renderActions(source, info);
                return;
            }
            case "objectModels" -> {
                renderPairs(source, info, "objectModels", "objectTypes", "Model", PairKind.MODEL);
                return;
            }
            case "originalColours" -> {
                renderPairs(source, info, "originalColours", "modifiedColours", "Colour", PairKind.COLOUR);
                return;
            }
            case "originalTextureColours" -> {
                renderPairs(source, info, "originalTextureColours", "modifiedTextureColours", "Texture",
                        PairKind.TEXTURE);
                return;
            }
            default -> {
            }
        }
        if (info.control() == ObjectFieldCatalog.Control.INT_LIST) {
            renderList(source, info, field.name());
            return;
        }

        boolean dirty = isDirty(source, field.name());
        label(info.label(), help(info, field), dirty);
        ImGui.tableNextColumn();
        ImGui.beginDisabled(!source.canEdit());
        switch (field.type()) {
            case BOOLEAN -> {
                boolean current = Boolean.parseBoolean(field.value());
                if (ImGui.checkbox("##value", current)) {
                    applyField(source, field.name(), ObjectDefinitionEditValue.booleanValue(current),
                            ObjectDefinitionEditValue.booleanValue(!current));
                }
            }
            case INTEGER -> {
                if (info.control() == ObjectFieldCatalog.Control.SIGNED_BYTE_SLIDER) {
                    signedByteSlider(source, field);
                } else {
                    textInput(source, field);
                }
            }
            case LONG, STRING -> textInput(source, field);
            default -> {
                ImGui.alignTextToFramePadding();
                ImGui.textWrapped(field.value());
            }
        }
        ImGui.endDisabled();
    }

    private void textInput(Source source, ObjectDefinitionRawView.Field field) {
        String key = "field:" + field.name();
        ImString buffer = texts.computeIfAbsent(key, k -> new ImString(256));
        if (!key.equals(activeKey)) buffer.set(field.value());
        ImGui.setNextItemWidth(-Float.MIN_VALUE);
        ImGui.inputText("##value", buffer);
        if (trackActive(key) && ImGui.isItemDeactivatedAfterEdit()) {
            try {
                ObjectDefinitionEditValue before = value(field.type(), field.value());
                ObjectDefinitionEditValue after = value(field.type(), buffer.get().trim());
                if (!before.equals(after)) applyField(source, field.name(), before, after);
            } catch (RuntimeException failure) {
                status = "Rejected " + field.name() + ": " + failure.getMessage();
            }
        }
    }

    private void signedByteSlider(Source source, ObjectDefinitionRawView.Field field) {
        String key = "slider:" + field.name();
        int[] value = sliders.computeIfAbsent(key, k -> new int[1]);
        int current = Integer.parseInt(field.value());
        if (!key.equals(activeKey)) value[0] = current;
        ImGui.setNextItemWidth(-Float.MIN_VALUE);
        ImGui.sliderInt("##value", value, -128, 127);
        if (trackActive(key) && ImGui.isItemDeactivatedAfterEdit() && value[0] != current) {
            applyField(source, field.name(), ObjectDefinitionEditValue.intValue(current),
                    ObjectDefinitionEditValue.intValue(value[0]));
        }
    }

    // ---- right-click options --------------------------------------------------------------

    private void renderActions(Source source, ObjectFieldCatalog.FieldInfo info) {
        List<String> actions = actions(source);
        String summary = String.join(", ", actions.stream().filter(Objects::nonNull).toList());
        boolean open = nodeRow(info.label(), info.help(), isDirty(source, "actions"),
                summary.isEmpty() ? "none" : summary);
        if (!open) return;
        for (int index = 0; index < actions.size(); index++) {
            ImGui.pushID(index);
            label("Option " + (index + 1), "Opcode " + (30 + index) + ". Empty removes the option.", false);
            ImGui.tableNextColumn();
            ImGui.beginDisabled(!source.canEdit());
            String key = "action:" + index;
            ImString buffer = texts.computeIfAbsent(key, k -> new ImString(128));
            String current = actions.get(index) == null ? "" : actions.get(index);
            if (!key.equals(activeKey)) buffer.set(current);
            ImGui.setNextItemWidth(-Float.MIN_VALUE);
            ImGui.inputTextWithHint("##action", "(none)", buffer);
            if (trackActive(key) && ImGui.isItemDeactivatedAfterEdit() && !buffer.get().equals(current)) {
                int optionIndex = index;
                execute(source, () -> ObjectDefinitionStructureEditCommand.action(
                        source.transaction(), optionIndex, buffer.get()), "option " + (index + 1));
            }
            ImGui.endDisabled();
            ImGui.popID();
        }
        ImGui.treePop();
    }

    private static List<String> actions(Source source) {
        if (source.transaction() != null) return source.transaction().actions();
        if (source.definitions() != null) {
            List<String> positional = source.definitions().objectActions(source.objectId()).orElse(null);
            if (positional != null) return positional;
        }
        return Arrays.asList(new String[5]);
    }

    // ---- integer lists ----------------------------------------------------------------------

    private void renderList(Source source, ObjectFieldCatalog.FieldInfo info, String field) {
        List<Integer> values = intList(source, field);
        boolean open = nodeRow(info.label(), info.help(), isDirty(source, field), values.size() + " entries");
        if (!open) return;
        boolean transforms = field.equals("transforms");
        for (int index = 0; index < values.size(); index++) {
            ImGui.pushID(index);
            String rowLabel = transforms
                    ? (index == values.size() - 1 ? "Default (last)" : "Var value " + index)
                    : "Entry " + (index + 1);
            label(rowLabel, transforms ? objectName(source, values.get(index)) : "", false);
            ImGui.tableNextColumn();
            ImGui.beginDisabled(!source.canEdit());
            float remove = removeButtonWidth();
            ImGui.setNextItemWidth(Math.max(1.0f, ImGui.getContentRegionAvailX() - remove));
            Integer edited = intInput(field + ":" + index, values.get(index));
            ImGui.sameLine();
            boolean removed = ImGui.button("x##remove");
            ImGui.endDisabled();
            ImGui.popID();
            if (edited != null) {
                List<Integer> next = new ArrayList<>(values);
                next.set(index, edited);
                commitLists(source, Map.of(field, next));
            } else if (removed) {
                List<Integer> next = new ArrayList<>(values);
                next.remove(index);
                commitLists(source, Map.of(field, next));
            }
        }
        addRow(source, () -> {
            List<Integer> next = new ArrayList<>(values);
            next.add(transforms ? -1 : 0);
            commitLists(source, Map.of(field, next));
        });
        ImGui.treePop();
    }

    private enum PairKind { MODEL, COLOUR, TEXTURE }

    /**
     * Two parallel lists edited as rows of pairs: models with their loc shapes,
     * recolour from/to, retexture from/to. Both lists always change together.
     */
    private void renderPairs(Source source, ObjectFieldCatalog.FieldInfo info, String firstField,
                             String secondField, String rowName, PairKind kind) {
        List<Integer> first = intList(source, firstField);
        List<Integer> second = intList(source, secondField);
        boolean typeless = kind == PairKind.MODEL && second.isEmpty();
        boolean dirty = isDirty(source, firstField) || isDirty(source, secondField);
        String summary = first.size() + (kind == PairKind.MODEL ? " models" : " pairs")
                + (typeless && !first.isEmpty() ? " (shape 10 only)" : "");
        boolean open = nodeRow(info.label(), info.help(), dirty, summary);
        if (!open) return;
        for (int index = 0; index < first.size(); index++) {
            ImGui.pushID(index);
            label(rowName + " " + (index + 1), "", false);
            ImGui.tableNextColumn();
            ImGui.beginDisabled(!source.canEdit());
            float spacing = ImGui.getStyle().getItemSpacingX();
            float half = Math.max(1.0f, (ImGui.getContentRegionAvailX() - removeButtonWidth() - spacing) * 0.5f);
            Integer editedFirst;
            Integer editedSecond = null;
            int shapeChoice = -1;
            if (kind == PairKind.MODEL) {
                ImGui.setNextItemWidth(half);
                editedFirst = intInput(firstField + ":" + index, first.get(index));
                ImGui.sameLine();
                ImGui.setNextItemWidth(half);
                int shape = typeless ? 10 : (index < second.size() ? second.get(index) : 10);
                ImInt selected = new ImInt(shapeIndex(shape));
                if (ImGui.combo("##shape", selected, SHAPE_LABELS)) shapeChoice = SHAPES[selected.get()].id();
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(typeless ? "No model types: these models are only used for shape 10. "
                            + "Choosing a shape adds a type to every model." : "Loc shape this model is used for.");
                }
            } else {
                float swatch = kind == PairKind.COLOUR ? ImGui.getFrameHeight() + spacing : 0.0f;
                float input = Math.max(1.0f, half - swatch);
                if (kind == PairKind.COLOUR) swatch(first.get(index));
                ImGui.setNextItemWidth(input);
                editedFirst = intInput(firstField + ":" + index, first.get(index));
                ImGui.sameLine();
                if (kind == PairKind.COLOUR) swatch(index < second.size() ? second.get(index) : 0);
                ImGui.setNextItemWidth(input);
                editedSecond = intInput(secondField + ":" + index, index < second.size() ? second.get(index) : 0);
            }
            ImGui.sameLine();
            boolean removed = ImGui.button("x##remove");
            ImGui.endDisabled();
            ImGui.popID();

            if (editedFirst != null || editedSecond != null || shapeChoice >= 0 || removed) {
                List<Integer> nextFirst = new ArrayList<>(first);
                List<Integer> nextSecond = new ArrayList<>(second);
                if (kind == PairKind.MODEL && typeless && shapeChoice >= 0) {
                    for (int i = 0; i < nextFirst.size(); i++) nextSecond.add(10);
                }
                while (kind != PairKind.MODEL && nextSecond.size() < nextFirst.size()) nextSecond.add(0);
                if (removed) {
                    nextFirst.remove(index);
                    if (index < nextSecond.size()) nextSecond.remove(index);
                } else {
                    if (editedFirst != null) nextFirst.set(index, editedFirst);
                    if (editedSecond != null) nextSecond.set(index, editedSecond);
                    if (shapeChoice >= 0) nextSecond.set(index, shapeChoice);
                }
                Map<String, List<Integer>> change = new LinkedHashMap<>();
                change.put(firstField, nextFirst);
                change.put(secondField, nextSecond);
                commitLists(source, change);
            }
        }
        addRow(source, () -> {
            List<Integer> nextFirst = new ArrayList<>(first);
            List<Integer> nextSecond = new ArrayList<>(second);
            nextFirst.add(0);
            if (!typeless) nextSecond.add(kind == PairKind.MODEL ? 10 : 0);
            Map<String, List<Integer>> change = new LinkedHashMap<>();
            change.put(firstField, nextFirst);
            change.put(secondField, nextSecond);
            commitLists(source, change);
        });
        ImGui.treePop();
    }

    private static int shapeIndex(int shapeId) {
        for (int i = 0; i < SHAPES.length; i++) if (SHAPES[i].id() == shapeId) return i;
        return 0;
    }

    private static void swatch(int hsl) {
        int rgb = OsrsTerrainColorMath.packedHslToRgb(hsl & 0xFFFF, 0.8);
        ImGui.colorButton("##swatch", ((rgb >> 16) & 0xFF) / 255.0f, ((rgb >> 8) & 0xFF) / 255.0f,
                (rgb & 0xFF) / 255.0f, 1.0f, 0, ImGui.getFrameHeight(), ImGui.getFrameHeight());
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("HSL " + hsl + String.format(Locale.ROOT, "  (hue %d, sat %d, light %d)",
                    (hsl >> 10) & 0x3F, (hsl >> 7) & 0x7, hsl & 0x7F));
        }
        ImGui.sameLine();
    }

    // ---- params -------------------------------------------------------------------------------

    private void renderParams(Source source) {
        List<ObjectDefinitionRawView.Param> params = source.raw().params();
        for (ObjectDefinitionRawView.Param param : params) {
            ImGui.pushID("param-" + param.id());
            boolean dirty = source.transaction() != null && source.transaction().dirtyParams().contains(param.id());
            label("Param " + param.id(), param.type().name().toLowerCase(Locale.ROOT) + " param (opcode 249).", dirty);
            ImGui.tableNextColumn();
            ImGui.beginDisabled(!source.canEdit());
            boolean scalar = param.type() == ObjectDefinitionRawView.ValueType.STRING
                    || param.type() == ObjectDefinitionRawView.ValueType.INTEGER
                    || param.type() == ObjectDefinitionRawView.ValueType.LONG;
            ImGui.setNextItemWidth(Math.max(1.0f, ImGui.getContentRegionAvailX() - removeButtonWidth()));
            if (scalar) {
                String key = "param:" + param.id();
                ImString buffer = texts.computeIfAbsent(key, k -> new ImString(256));
                if (!key.equals(activeKey)) buffer.set(param.value());
                ImGui.inputText("##param", buffer);
                if (trackActive(key) && ImGui.isItemDeactivatedAfterEdit()) {
                    try {
                        ObjectDefinitionEditValue before = value(param.type(), param.value());
                        ObjectDefinitionEditValue after = value(param.type(), buffer.get().trim());
                        if (!before.equals(after)) {
                            execute(source, () -> ObjectDefinitionEditCommand.param(
                                    source.transaction(), param.id(), before, after), "param " + param.id());
                        }
                    } catch (RuntimeException failure) {
                        status = "Rejected param " + param.id() + ": " + failure.getMessage();
                    }
                }
            } else {
                ImGui.textWrapped(param.value());
            }
            ImGui.sameLine();
            if (ImGui.button("x##remove") && scalar) {
                ObjectDefinitionEditValue before = value(param.type(), param.value());
                execute(source, () -> ObjectDefinitionEditCommand.param(
                        source.transaction(), param.id(), before, null), "param " + param.id());
            }
            ImGui.endDisabled();
            ImGui.popID();
        }
        if (!source.canEdit()) return;
        label("Add param", "Adds or replaces an opcode 249 param.", false);
        ImGui.tableNextColumn();
        float spacing = ImGui.getStyle().getItemSpacingX();
        float third = Math.max(1.0f, (ImGui.getContentRegionAvailX() - spacing * 2.0f) / 3.0f);
        ImGui.setNextItemWidth(third);
        ImGui.inputInt("##new-param-id", newParamId, 0, 0);
        if (ImGui.isItemHovered()) ImGui.setTooltip("Param id");
        ImGui.sameLine();
        ImGui.setNextItemWidth(third);
        ImGui.combo("##new-param-type", newParamType, PARAM_TYPES);
        ImGui.sameLine();
        ImGui.setNextItemWidth(third);
        ImGui.inputTextWithHint("##new-param-value", "value", newParamValue);
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.tableNextColumn();
        if (ImGui.button("Add / replace param##new-param")) {
            try {
                ObjectDefinitionRawView.ValueType type = switch (newParamType.get()) {
                    case 1 -> ObjectDefinitionRawView.ValueType.INTEGER;
                    case 2 -> ObjectDefinitionRawView.ValueType.LONG;
                    default -> ObjectDefinitionRawView.ValueType.STRING;
                };
                ObjectDefinitionEditValue after = value(type, newParamValue.get().trim());
                ObjectDefinitionEditValue before = params.stream().filter(p -> p.id() == newParamId.get())
                        .findFirst().map(p -> value(p.type(), p.value())).orElse(null);
                if (!Objects.equals(before, after)) {
                    int paramId = newParamId.get();
                    execute(source, () -> ObjectDefinitionEditCommand.param(
                            source.transaction(), paramId, before, after), "param " + paramId);
                }
            } catch (RuntimeException failure) {
                status = "Rejected param: " + failure.getMessage();
            }
        }
    }

    // ---- shared helpers -----------------------------------------------------------------------

    /** A property row whose label is an expandable node with a summary value; call treePop when true. */
    private static boolean nodeRow(String label, String help, boolean dirty, String summary) {
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        if (dirty) ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, StudioDrawColors.abgr(ACCENT));
        boolean open = ImGui.treeNodeEx((dirty ? "* " : "") + label + "##node",
                ImGuiTreeNodeFlags.SpanAvailWidth | ImGuiTreeNodeFlags.DrawLinesToNodes);
        if (dirty) ImGui.popStyleColor();
        if (ImGui.isItemHovered() && !help.isBlank()) ImGui.setTooltip(help);
        ImGui.tableNextColumn();
        ImGui.textDisabled(summary);
        return open;
    }

    private static void label(String label, String help, boolean dirty) {
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.alignTextToFramePadding();
        if (dirty) {
            ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, StudioDrawColors.abgr(ACCENT));
            ImGui.textWrapped("* " + label);
            ImGui.popStyleColor();
        } else {
            ImGui.textWrapped(label);
        }
        if (ImGui.isItemHovered() && !help.isBlank()) ImGui.setTooltip(help);
    }

    private static String help(ObjectFieldCatalog.FieldInfo info, ObjectDefinitionRawView.Field field) {
        return info.help() + "\nField: " + field.name()
                + (field.opcode().isBlank() ? "" : "   Opcode: " + field.opcode());
    }

    private void addRow(Source source, Runnable add) {
        if (!source.canEdit()) return;
        ImGui.tableNextRow();
        ImGui.tableNextColumn();
        ImGui.tableNextColumn();
        if (ImGui.smallButton("+ Add")) add.run();
    }

    /** Integer box that returns the new value once it is released after an edit, else null. */
    private Integer intInput(String key, int current) {
        ImInt buffer = ints.computeIfAbsent(key, k -> new ImInt());
        if (!key.equals(activeKey)) buffer.set(current);
        ImGui.inputInt("##" + key, buffer, 0, 0);
        if (trackActive(key) && ImGui.isItemDeactivatedAfterEdit() && buffer.get() != current) {
            return buffer.get();
        }
        return null;
    }

    /** Keeps {@link #activeKey} on the widget being edited; true on the frame it is released. */
    private boolean trackActive(String key) {
        if (ImGui.isItemActive()) {
            activeKey = key;
            return false;
        }
        if (key.equals(activeKey)) {
            activeKey = null;
            return true;
        }
        return false;
    }

    private static float removeButtonWidth() {
        return ImGui.calcTextSize("x").x + ImGui.getStyle().getFramePaddingX() * 2.0f
                + ImGui.getStyle().getItemSpacingX();
    }

    private static boolean isDirty(Source source, String field) {
        return source.transaction() != null && source.transaction().dirtyFields().contains(field);
    }

    private static List<Integer> intList(Source source, String field) {
        if (source.transaction() != null) return source.transaction().intList(field);
        return source.raw().fields().stream().filter(f -> f.name().equals(field)).findFirst()
                .map(f -> parseIntList(f.value())).orElse(List.of());
    }

    static List<Integer> parseIntList(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty() || trimmed.equals("null") || trimmed.equals("[]")) return List.of();
        List<Integer> result = new ArrayList<>();
        for (String part : trimmed.replace("[", "").replace("]", "").split(",")) {
            String value = part.trim();
            if (!value.isEmpty()) result.add(Integer.parseInt(value));
        }
        return result;
    }

    private static String objectName(Source source, int objectId) {
        if (objectId < 0) return "-1: nothing is shown";
        if (source.definitions() == null) return "Object " + objectId;
        return source.definitions().object(objectId).map(def -> def.displayName() + " (" + objectId + ")")
                .orElse("Object " + objectId + " (missing)");
    }

    private void applyField(Source source, String field, ObjectDefinitionEditValue before,
                            ObjectDefinitionEditValue after) {
        execute(source, () -> ObjectDefinitionEditCommand.field(source.transaction(), field, before, after), field);
    }

    private void commitLists(Source source, Map<String, List<Integer>> change) {
        execute(source, () -> ObjectDefinitionStructureEditCommand.lists(source.transaction(), change),
                String.join(", ", change.keySet()));
    }

    private void execute(Source source, java.util.function.Supplier<com.rspsi.editor.EditorCommand> command,
                         String what) {
        if (!source.canEdit()) return;
        try {
            source.session().execute(command.get());
            status = "Updated " + what + " (in-memory preview; Ctrl+Z to undo).";
        } catch (RuntimeException failure) {
            status = "Rejected " + what + ": " + failure.getMessage();
        }
    }

    private static ObjectDefinitionEditValue value(ObjectDefinitionRawView.ValueType type, String text) {
        return switch (type) {
            case INTEGER -> ObjectDefinitionEditValue.intValue(Integer.parseInt(text));
            case LONG -> ObjectDefinitionEditValue.longValue(Long.parseLong(text));
            case BOOLEAN -> ObjectDefinitionEditValue.booleanValue(Boolean.parseBoolean(text));
            default -> ObjectDefinitionEditValue.stringValue(text);
        };
    }
}
