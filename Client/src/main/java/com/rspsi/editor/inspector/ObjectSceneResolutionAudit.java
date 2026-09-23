package com.rspsi.editor.inspector;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.editor.model.WorldDocument;
import com.rspsi.editor.model.WorldObject;
import com.rspsi.editor.render.ModelRenderPacket;
import com.rspsi.editor.render.RenderObject;
import com.rspsi.editor.render.RenderScene;
import com.rspsi.editor.render.SceneObjectIdentity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Accounts for every authored location through deterministic definition/model
 * resolution and neutral scene submission.
 *
 * <p>Camera-dependent occlusion and final native draw visibility deliberately
 * remain outside this audit. A missing packet for geometry that is otherwise
 * ready is an error; a state-dependent/unresolved definition is retained as an
 * explicit warning instead of silently disappearing.</p>
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
        if (packetCount > 0) return Stage.PACKET_SUBMITTED;
        return switch (resolution.geometryStatus()) {
            case DEFINITION_UNRESOLVED -> Stage.DEFINITION_UNRESOLVED;
            case NO_MODEL_FOR_SHAPE -> Stage.NO_MODEL_FOR_SHAPE;
            case MISSING_MODEL_GEOMETRY -> Stage.MISSING_MODEL_GEOMETRY;
            case EMPTY_RENDERABLE_GEOMETRY -> Stage.EMPTY_RENDERABLE_GEOMETRY;
            case PARTIAL_GEOMETRY, READY -> Stage.PACKET_MISSING;
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
        DEFINITION_UNRESOLVED(Severity.WARN),
        NO_MODEL_FOR_SHAPE(Severity.WARN),
        MISSING_MODEL_GEOMETRY(Severity.FAIL),
        EMPTY_RENDERABLE_GEOMETRY(Severity.FAIL),
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
