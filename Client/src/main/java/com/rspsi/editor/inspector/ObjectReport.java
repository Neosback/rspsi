package com.rspsi.editor.inspector;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.ObjectAppearanceView;
import com.rspsi.cache.definition.ObjectCollisionView;
import com.rspsi.cache.definition.ObjectDefinitionView;
import com.rspsi.cache.definition.ObjectVarState;
import com.rspsi.editor.model.OsrsLocShape;
import com.rspsi.editor.model.WorldObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Readable, frontend-neutral description of an object definition and,
 * optionally, one placement of it: sections of label/value rows for property
 * grids, with the same content as plain text for copy buttons.
 */
public record ObjectReport(String title, List<Section> sections) {
    public ObjectReport {
        Objects.requireNonNull(title, "title");
        sections = List.copyOf(Objects.requireNonNull(sections, "sections"));
    }

    public record Section(String title, List<Row> rows) {
        public Section {
            Objects.requireNonNull(title, "title");
            rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
        }
    }

    /** One property; {@code warning} marks values that explain why something draws nothing. */
    public record Row(String label, String value, boolean warning) {
        public Row {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(value, "value");
        }
    }

    /** Definition-only report (object browser). */
    public static ObjectReport forDefinition(int id, DefinitionProvider definitions) {
        return build(id, null, definitions, ObjectVarState.freshAccount());
    }

    /** Report for one placed object (tile inspector, selection). */
    public static ObjectReport forPlacement(WorldObject object, DefinitionProvider definitions) {
        return forPlacement(object, definitions, ObjectVarState.freshAccount());
    }

    /** Report for one placed object as it appears in {@code varState}. */
    public static ObjectReport forPlacement(WorldObject object, DefinitionProvider definitions,
                                            ObjectVarState varState) {
        return build(Objects.requireNonNull(object, "object").id(), object, definitions, varState);
    }

    public String toText() {
        StringBuilder text = new StringBuilder(title).append('\n');
        for (Section section : sections) {
            text.append('\n').append(section.title()).append('\n');
            for (Row row : section.rows()) {
                text.append("  ").append(row.label()).append(": ").append(row.value()).append('\n');
            }
        }
        return text.toString();
    }

    private static ObjectReport build(int id, WorldObject placement, DefinitionProvider definitions,
                                      ObjectVarState varState) {
        Objects.requireNonNull(definitions, "definitions");
        Optional<ObjectDefinitionView> found = definitions.object(id);
        if (found.isEmpty()) {
            return new ObjectReport("Object #" + id, List.of(new Section("Definition",
                    List.of(new Row("Status", "No definition with this id in the selected cache", true)))));
        }
        ObjectDefinitionView definition = found.orElseThrow();
        List<Section> sections = new ArrayList<>();

        List<Row> identity = new ArrayList<>();
        identity.add(row("Id", Integer.toString(id)));
        identity.add(row("Name", definition.hasDisplayName() ? definition.displayName()
                : "(unnamed - the cache name is \"null\")"));
        if (placement != null) {
            identity.add(row("Type", placement.type() + " - " + placement.shape()
                    .map(OsrsLocShape::displayName).orElse("unknown shape")));
            identity.add(row("Layer", placement.category().displayName()));
            identity.add(row("Rotation", placement.rotation() + " (" + placement.rotation() * 90 + " degrees)"));
            identity.add(row("Position", placement.x() + ", " + placement.y() + "  plane " + placement.plane()));
            if (placement.wallOrientationA() != 0) {
                identity.add(row("Wall edges", wallEdges(placement.wallOrientationA() | placement.wallOrientationB())));
            }
        }
        sections.add(new Section("Identity", identity));

        List<Row> appearance = new ArrayList<>();
        if (definition.hasTransforms()) {
            appearance.add(row("State variable", definition.varbit() != -1
                    ? "varbit " + definition.varbit() + " = " + varState.varbitValue(definition.varbit())
                    : definition.varp() != -1
                    ? "varp " + definition.varp() + " = " + varState.varpValue(definition.varp())
                    : "none"));
            appearance.add(row("States", states(definition, definitions)));
        }
        if (placement != null) {
            ObjectResolutionSummary resolution = ObjectResolutionSummary.capture(placement, definitions, varState);
            appearance.add(new Row("Draws", resolution.diagnosticSummary(), !resolution.renderableGeometryReady()));
            if (!resolution.selectedModelIds().isEmpty()) {
                appearance.add(row("Models used", resolution.selectedModelIds().toString()));
            }
        }
        appearance.add(row("Models by type", modelsByType(definition)));
        sections.add(new Section("Appearance", appearance));

        List<Row> footprint = new ArrayList<>();
        footprint.add(row("Size", definition.width() + " x " + definition.length() + " tiles"));
        Optional<ObjectCollisionView> collision = definitions.objectCollision(id);
        collision.ifPresent(value -> {
            footprint.add(row("Blocks walking", value.blockWalk() == 0 ? "no"
                    : value.blockWalk() == 1 ? "yes" : "yes (" + value.blockWalk() + ")"));
            footprint.add(row("Blocks projectiles", value.blockProjectile() ? "yes" : "no"));
            if (value.breakRouteFinding()) footprint.add(row("Breaks route finding", "yes"));
        });
        footprint.add(row("Interactive", definition.interactive() ? "yes" : "no"));
        List<String> actions = definition.interactions().stream()
                .filter(action -> action != null && !action.isBlank()).toList();
        footprint.add(row("Actions", actions.isEmpty() ? "none" : String.join(", ", actions)));
        if (definition.mapSceneId() >= 0) footprint.add(row("Map scene icon", Integer.toString(definition.mapSceneId())));
        sections.add(new Section("Footprint & interaction", footprint));

        definitions.objectAppearance(id).ifPresent(look -> {
            List<Row> rows = modelAppearance(look);
            if (!rows.isEmpty()) sections.add(new Section("Model settings", rows));
        });

        String title = (definition.hasDisplayName() ? definition.displayName() : "Object") + "  #" + id;
        return new ObjectReport(title, sections);
    }

    private static String states(ObjectDefinitionView definition, DefinitionProvider definitions) {
        int[] transforms = definition.transforms();
        if (transforms.length == 0) return "default " + label(definition.defaultTransform(), definitions);
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < transforms.length; i++) {
            String key = i == transforms.length - 1 ? "default" : Integer.toString(i);
            parts.add(key + " = " + label(transforms[i], definitions));
        }
        return String.join(", ", parts);
    }

    private static String label(int id, DefinitionProvider definitions) {
        if (id < 0) return "nothing";
        return definitions.object(id).map(value -> value.hasDisplayName()
                ? value.displayName() + " (" + id + ")" : "#" + id).orElse("#" + id + " (missing)");
    }

    private static String modelsByType(ObjectDefinitionView definition) {
        int[] models = definition.modelIds();
        int[] types = definition.modelTypes();
        if (models.length == 0) return "none";
        if (types.length == 0) return "any type: " + Arrays.toString(models);
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < models.length; i++) parts.add("type " + types[i] + ": " + models[i]);
        return String.join(", ", parts);
    }

    private static List<Row> modelAppearance(ObjectAppearanceView look) {
        List<Row> rows = new ArrayList<>();
        if (look.animationId() >= 0) rows.add(row("Animation", Integer.toString(look.animationId())));
        if (look.scaleX() != 128 || look.scaleY() != 128 || look.scaleZ() != 128) {
            rows.add(row("Scale", look.scaleX() + " / " + look.scaleY() + " / " + look.scaleZ() + "  (128 = 1x)"));
        }
        if (look.offsetX() != 0 || look.offsetY() != 0 || look.offsetZ() != 0) {
            rows.add(row("Offset", look.offsetX() + " / " + look.offsetY() + " / " + look.offsetZ()));
        }
        if (!look.recolors().isEmpty()) rows.add(row("Recolours", look.recolors().toString()));
        if (!look.retextures().isEmpty()) rows.add(row("Retextures", look.retextures().toString()));
        if (look.contouredGround()) rows.add(row("Follows terrain", "yes (contoured)"));
        if (!look.castsShadow()) rows.add(row("Casts shadow", "no"));
        return rows;
    }

    private static String wallEdges(int bits) {
        List<String> edges = new ArrayList<>();
        String[] names = {"west", "north", "east", "south", "north-west", "north-east", "south-east", "south-west"};
        for (int i = 0; i < names.length; i++) if ((bits & (1 << i)) != 0) edges.add(names[i]);
        return String.join(", ", edges);
    }

    private static Row row(String label, String value) {
        return new Row(label, value, false);
    }
}
