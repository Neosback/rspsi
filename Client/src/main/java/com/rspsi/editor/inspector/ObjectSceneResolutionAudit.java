package com.rspsi.editor.inspector;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.ObjectDefinitionResolver;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.render.ModelRenderPacket;
import com.rspsi.editor.render.RenderObject;
import com.rspsi.editor.render.RenderScene;
import com.rspsi.editor.render.SceneObjectIdentity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Accounts for every authored location through deterministic definition/model
 * resolution and neutral scene submission.
 *
 * <p>Camera-dependent occlusion and final native draw visibility deliberately
 * remain outside this audit. A missing packet for geometry that is otherwise
 * ready is an error, as is a placement whose definition does not exist in the
 * selected cache. State-dependent definitions and loc definitions whose cache
 * models are authored empty are retained as explicit warnings instead of
 * silently disappearing: the client draws nothing for them either.</p>
 */
public final class ObjectSceneResolutionAudit {
    private ObjectSceneResolutionAudit() {
    }

    public static Report audit(WorldDocument document, DefinitionProvider definitions,
                               RenderScene scene) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(definitions, "definitions");
        Objects.requireNonNull(scene, "scene");

        Map<PlacementKey, Integer> projectedCounts = new HashMap<>();
        for (RenderObject renderObject : scene.renderObjects()) {
            projectedCounts.merge(PlacementKey.of(renderObject.object()), 1, Integer::sum);
        }

        Map<IdentityKey, Integer> packetCounts = new HashMap<>();
        for (ModelRenderPacket packet : scene.modelPackets()) {
            SceneObjectIdentity identity = packet.sceneObjectIdentity();
            if (!identity.present()) continue;
            packetCounts.merge(IdentityKey.of(identity), 1, Integer::sum);
        }

        List<Entry> entries = new ArrayList<>();
        Map<PlacementKey, Integer> occurrences = new HashMap<>();
        int failures = 0;
        int warnings = 0;

        for (int plane = 0; plane < document.planes(); plane++) {
            for (int x = 0; x < document.width(); x++) {
                for (int y = 0; y < document.length(); y++) {
                    for (WorldObject object : document.tile(plane, x, y).objects()) {
                        PlacementKey key = PlacementKey.of(object);
                        int occurrence = occurrences.getOrDefault(key, 0);
                        occurrences.put(key, occurrence + 1);

                        ObjectResolutionSummary resolution =
                                ObjectResolutionSummary.capture(object, definitions);
                        boolean projected = occurrence < projectedCounts.getOrDefault(key, 0);
                        int packets = packetCounts.getOrDefault(
                                IdentityKey.of(object, occurrence), 0);
                        Stage stage = stage(resolution, projected, packets);
                        if (stage.severity() == Severity.FAIL) failures++;
                        if (stage.severity() == Severity.WARN) warnings++;
                        entries.add(new Entry(object, occurrence, resolution,
                                projected, packets, stage));
                    }
                }
            }
        }

        return new Report(entries, failures, warnings);
    }

    private static Stage stage(ObjectResolutionSummary resolution,
                               boolean projected, int packetCount) {
        if (!projected) return Stage.SCENE_PROJECTION_MISSING;

        // Geometry completeness is checked before packet presence. The renderer
        // intentionally skips an unavailable model and may still emit a packet
        // from the remaining models; that must not turn an incomplete object
        // into a passing audit.
        return switch (resolution.geometryStatus()) {
            case DEFINITION_UNRESOLVED -> resolution.definitionStatus()
                    == ObjectDefinitionResolver.Status.MISSING_PLACED_DEFINITION
                    ? Stage.PLACED_DEFINITION_MISSING
                    : Stage.DEFINITION_UNRESOLVED;
            case NO_MODEL_FOR_SHAPE -> Stage.NO_MODEL_FOR_SHAPE;
            case MISSING_MODEL_GEOMETRY -> Stage.MISSING_MODEL_GEOMETRY;
            case EMPTY_RENDERABLE_GEOMETRY -> Stage.EMPTY_RENDERABLE_GEOMETRY;
            case PARTIAL_GEOMETRY -> Stage.PARTIAL_MODEL_GEOMETRY;
            case READY -> packetCount > 0 ? Stage.PACKET_SUBMITTED : Stage.PACKET_MISSING;
        };
    }

    public record Report(List<Entry> entries, int failureCount, int warningCount) {
        public Report {
            entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
            if (failureCount < 0 || warningCount < 0) {
                throw new IllegalArgumentException("Audit counts cannot be negative");
            }
        }

        public int submittedCount() {
            return (int) entries.stream()
                    .filter(entry -> entry.stage() == Stage.PACKET_SUBMITTED)
                    .count();
        }

        public List<Entry> transformedEntries() {
            return entries.stream()
                    .filter(entry -> entry.resolution().transformed())
                    .toList();
        }

        public List<Entry> problems() {
            return entries.stream()
                    .filter(entry -> entry.stage().severity() != Severity.PASS)
                    .toList();
        }

        /**
         * Problems grouped by stage and selected models, largest group first,
         * so every distinct failure cause can be reviewed without reading one
         * line per placement.
         */
        public List<ProblemGroup> problemGroups() {
            Map<ProblemKey, List<Entry>> grouped = new LinkedHashMap<>();
            for (Entry entry : problems()) {
                grouped.computeIfAbsent(new ProblemKey(entry.stage(),
                                entry.resolution().definitionStatus(),
                                entry.resolution().selectedModelIds()),
                        key -> new ArrayList<>()).add(entry);
            }
            return grouped.entrySet().stream()
                    .map(group -> new ProblemGroup(group.getKey().stage(),
                            group.getKey().definitionStatus(),
                            group.getKey().selectedModelIds(), group.getValue()))
                    .sorted(Comparator.comparingInt((ProblemGroup group) -> group.entries().size())
                            .reversed())
                    .toList();
        }

        private record ProblemKey(Stage stage, ObjectDefinitionResolver.Status definitionStatus,
                                  List<Integer> selectedModelIds) {
        }
    }

    public record ProblemGroup(Stage stage, ObjectDefinitionResolver.Status definitionStatus,
                               List<Integer> selectedModelIds, List<Entry> entries) {
        public ProblemGroup {
            Objects.requireNonNull(stage, "stage");
            Objects.requireNonNull(definitionStatus, "definitionStatus");
            selectedModelIds = List.copyOf(Objects.requireNonNull(selectedModelIds, "selectedModelIds"));
            entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        }

        public String diagnostic() {
            return entries.size() + "x " + stage + " (" + stage.severity() + ")"
                    + " definition=" + definitionStatus
                    + " models=" + selectedModelIds
                    + " objectIds=" + entries.stream()
                    .map(entry -> entry.object().id()).distinct().sorted().toList();
        }
    }

    public record Entry(
            WorldObject object,
            int occurrence,
            ObjectResolutionSummary resolution,
            boolean projected,
            int packetCount,
            Stage stage
    ) {
        public Entry {
            Objects.requireNonNull(object, "object");
            Objects.requireNonNull(resolution, "resolution");
            Objects.requireNonNull(stage, "stage");
            if (occurrence < 0 || packetCount < 0) {
                throw new IllegalArgumentException("Occurrence and packet count cannot be negative");
            }
        }

        public String diagnostic() {
            return "id=" + object.id()
                    + " type=" + object.type()
                    + " rot=" + object.rotation()
                    + " p=" + object.plane()
                    + " x=" + object.x()
                    + " y=" + object.y()
                    + " occurrence=" + occurrence
                    + " stage=" + stage
                    + " definition=" + resolution.definitionStatus()
                    + " transform=" + resolution.transformPath()
                    + " models=" + resolution.selectedModelIds();
        }
    }

    public enum Severity {
        PASS,
        WARN,
        FAIL
    }

    public enum Stage {
        PACKET_SUBMITTED(Severity.PASS),
        /** The map places an id the selected cache does not define. */
        PLACED_DEFINITION_MISSING(Severity.FAIL),
        /** State-dependent multiloc without an editor default, or a missing transform target. */
        DEFINITION_UNRESOLVED(Severity.WARN),
        NO_MODEL_FOR_SHAPE(Severity.WARN),
        MISSING_MODEL_GEOMETRY(Severity.FAIL),
        PARTIAL_MODEL_GEOMETRY(Severity.FAIL),
        /**
         * Every selected model decodes but has no triangles. Real caches use
         * such authored-empty models for invisible blockers and icon-only
         * floor decorations (revision 240 models 1105, 2214, 2215, 4873).
         */
        EMPTY_RENDERABLE_GEOMETRY(Severity.WARN),
        PACKET_MISSING(Severity.FAIL),
        SCENE_PROJECTION_MISSING(Severity.FAIL);

        private final Severity severity;

        Stage(Severity severity) {
            this.severity = severity;
        }

        public Severity severity() {
            return severity;
        }
    }

    private record PlacementKey(int id, int type, int rotation,
                                int plane, int x, int y) {
        static PlacementKey of(WorldObject object) {
            return new PlacementKey(object.id(), object.type(), object.rotation(),
                    object.plane(), object.x(), object.y());
        }
    }

    private record IdentityKey(int id, int type, int rotation,
                               int plane, int x, int y, int occurrence) {
        static IdentityKey of(WorldObject object, int occurrence) {
            return new IdentityKey(object.id(), object.type(), object.rotation(),
                    object.plane(), object.x(), object.y(), occurrence);
        }

        static IdentityKey of(SceneObjectIdentity identity) {
            return new IdentityKey(identity.objectId(), identity.shape(), identity.rotation(),
                    identity.authoredPlane(), identity.anchorX(), identity.anchorY(),
                    identity.occurrence());
        }
    }
}
