package com.rspsi.osrs.rules.model;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.ObjectAppearanceView;
import com.rspsi.editor.render.GpuDrawCommand;
import com.rspsi.editor.render.LightingProfile;
import com.rspsi.editor.render.ModelRenderPacket;
import com.rspsi.editor.render.ModelTriangle;
import com.rspsi.editor.render.ModelVertex;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Formal OSRS normal merging service using spatial hashing at shared seams.
 *
 * <p>In OSRS, model vertex normal merging occurs across adjacent model instances
 * sharing transformed world coordinates <em>before</em> lighting is baked.
 * Only models opting in via opcode 22 ({@code mergeNormals}) participate.</p>
 */
public final class NormalMergeService {

    public record VertexKey(int plane, int x, int y, int z) implements Comparable<VertexKey> {
        @Override
        public int compareTo(VertexKey other) {
            int cmp = Integer.compare(plane, other.plane);
            if (cmp != 0) return cmp;
            cmp = Integer.compare(x, other.x);
            if (cmp != 0) return cmp;
            cmp = Integer.compare(y, other.y);
            if (cmp != 0) return cmp;
            return Integer.compare(z, other.z);
        }
    }

    public record FaceKey(VertexKey v1, VertexKey v2, VertexKey v3) {
        public static FaceKey canonical(VertexKey a, VertexKey b, VertexKey c) {
            VertexKey x = a;
            VertexKey y = b;
            VertexKey z = c;
            if (x.compareTo(y) > 0) { VertexKey t = x; x = y; y = t; }
            if (y.compareTo(z) > 0) { VertexKey t = y; y = z; z = t; }
            if (x.compareTo(y) > 0) { VertexKey t = x; x = y; y = t; }
            return new FaceKey(x, y, z);
        }
    }

    public record VertexReference(int packetIndex, int vertexIndex, ModelVertex vertex) {}
    public record FaceReference(int packetIndex, int faceIndex) {}
    public record PositionKey(int x, int y, int z) {}

    private NormalMergeService() {}

    /**
     * Merges normals at shared vertices across adjacent models in world space.
     */
    public static List<ModelRenderPacket> mergeCrossPacketNormals(
            List<ModelRenderPacket> packets,
            DefinitionProvider definitions,
            LightingProfile lighting
    ) {
        Objects.requireNonNull(packets, "packets");
        Objects.requireNonNull(definitions, "definitions");
        if (packets.size() < 2) return packets;

        Map<VertexKey, List<VertexReference>> references = new LinkedHashMap<>();
        boolean[] mergeEnabled = new boolean[packets.size()];

        for (int packetIndex = 0; packetIndex < packets.size(); packetIndex++) {
            ModelRenderPacket packet = packets.get(packetIndex);
            mergeEnabled[packetIndex] = definitions.objectAppearance(packet.objectId())
                    .map(ObjectAppearanceView::mergeNormals).orElse(false);

            for (int vertexIndex = 0; vertexIndex < packet.vertices().size(); vertexIndex++) {
                ModelVertex vertex = packet.vertices().get(vertexIndex);
                if (vertex.normalMagnitude() == 0) continue;
                VertexKey key = new VertexKey(
                        packet.anchor().plane(),
                        packet.anchor().x() * 128 + vertex.x(),
                        packet.placementHeight() + vertex.y(),
                        packet.anchor().y() * 128 + vertex.z()
                );
                references.computeIfAbsent(key, ignored -> new ArrayList<>())
                        .add(new VertexReference(packetIndex, vertexIndex, vertex));
            }
        }

        @SuppressWarnings("unchecked")
        List<ModelVertex>[] mergedVertices = new List[packets.size()];
        @SuppressWarnings("unchecked")
        List<ModelTriangle>[] packetTriangles = new List[packets.size()];
        boolean[] changed = new boolean[packets.size()];

        for (int index = 0; index < packets.size(); index++) {
            mergedVertices[index] = new ArrayList<>(packets.get(index).vertices());
            packetTriangles[index] = new ArrayList<>(packets.get(index).triangles());
        }

        for (List<VertexReference> group : references.values()) {
            if (group.size() < 2 || group.stream().noneMatch(ref -> mergeEnabled[ref.packetIndex()])) {
                continue;
            }
            for (VertexReference target : group) {
                if (!mergeEnabled[target.packetIndex()]) continue;

                int accX = target.vertex().normalX();
                int accY = target.vertex().normalY();
                int accZ = target.vertex().normalZ();
                int accMag = target.vertex().normalMagnitude();
                boolean foundOtherPacket = false;

                for (VertexReference source : group) {
                    if (source.packetIndex() == target.packetIndex()
                            || !mergeEnabled[source.packetIndex()]
                            || source.vertex().normalMagnitude() == 0) continue;

                    accX += source.vertex().normalX();
                    accY += source.vertex().normalY();
                    accZ += source.vertex().normalZ();
                    accMag += source.vertex().normalMagnitude();
                    foundOtherPacket = true;
                }

                if (!foundOtherPacket) continue;

                ModelVertex original = target.vertex();
                mergedVertices[target.packetIndex()].set(target.vertexIndex(),
                        new ModelVertex(original.x(), original.y(), original.z(),
                                accX, accY, accZ, accMag, original.u(), original.v()));
                changed[target.packetIndex()] = true;
            }
        }

        // Hide occluded coplanar duplicate faces at seams
        Map<FaceKey, List<FaceReference>> faces = new LinkedHashMap<>();
        for (int packetIndex = 0; packetIndex < packets.size(); packetIndex++) {
            if (!mergeEnabled[packetIndex]) continue;
            ModelRenderPacket packet = packets.get(packetIndex);
            List<ModelVertex> vertices = packet.vertices();
            int plane = packet.anchor().plane();
            int anchorX = packet.anchor().x() * 128;
            int height = packet.placementHeight();
            int anchorZ = packet.anchor().y() * 128;

            for (int faceIndex = 0; faceIndex < packet.triangles().size(); faceIndex++) {
                ModelTriangle face = packet.triangles().get(faceIndex);
                if (face.renderType() == 2) continue;
                ModelVertex vA = vertices.get(face.a());
                ModelVertex vB = vertices.get(face.b());
                ModelVertex vC = vertices.get(face.c());
                VertexKey kA = new VertexKey(plane, anchorX + vA.x(), height + vA.y(), anchorZ + vA.z());
                VertexKey kB = new VertexKey(plane, anchorX + vB.x(), height + vB.y(), anchorZ + vB.z());
                VertexKey kC = new VertexKey(plane, anchorX + vC.x(), height + vC.y(), anchorZ + vC.z());
                FaceKey faceKey = FaceKey.canonical(kA, kB, kC);
                faces.computeIfAbsent(faceKey, ignored -> new ArrayList<>())
                        .add(new FaceReference(packetIndex, faceIndex));
            }
        }

        for (List<FaceReference> group : faces.values()) {
            if (group.size() < 2 || group.stream().map(FaceReference::packetIndex).distinct().count() < 2) {
                continue;
            }
            for (FaceReference ref : group) {
                ModelTriangle original = packetTriangles[ref.packetIndex()].get(ref.faceIndex());
                if (original.renderType() != 2) {
                    packetTriangles[ref.packetIndex()].set(ref.faceIndex(), original.withRenderType(2));
                    changed[ref.packetIndex()] = true;
                }
            }
        }

        List<ModelRenderPacket> result = new ArrayList<>(packets.size());
        for (int index = 0; index < packets.size(); index++) {
            ModelRenderPacket packet = packets.get(index);
            if (changed[index]) {
                result.add(relight(packet, List.copyOf(mergedVertices[index]),
                        List.copyOf(packetTriangles[index]), definitions, lighting));
            } else {
                result.add(packet);
            }
        }
        return List.copyOf(result);
    }

    private static ModelRenderPacket relight(
            ModelRenderPacket packet,
            List<ModelVertex> vertices,
            List<ModelTriangle> triangles,
            DefinitionProvider definitions,
            LightingProfile lighting
    ) {
        ObjectAppearanceView appearance = definitions.objectAppearance(packet.objectId())
                .orElseGet(ObjectAppearanceView::empty);
        int ambient = 64 + appearance.ambient();
        int contrast = 768 + appearance.contrast();
        int intensity = Math.max(1, (lighting.lightMagnitude() * contrast) >> 8);

        List<ModelTriangle> relit = new ArrayList<>(triangles.size());
        for (ModelTriangle triangle : triangles) {
            if (triangle.renderType() == 1 || triangle.renderType() == 2 || triangle.renderType() == 3) {
                relit.add(triangle);
                continue;
            }

            ModelVertex vA = vertices.get(triangle.a());
            ModelVertex vB = vertices.get(triangle.b());
            ModelVertex vC = vertices.get(triangle.c());

            int lightA = calculateLight(vA, lighting, ambient, intensity);
            int lightB = calculateLight(vB, lighting, ambient, intensity);
            int lightC = calculateLight(vC, lighting, ambient, intensity);

            int colorA;
            int colorB;
            int colorC;

            if (triangle.textureId() >= 0) {
                colorA = clamp(lightA, 2, 126);
                colorB = clamp(lightB, 2, 126);
                colorC = clamp(lightC, 2, 126);
            } else {
                int baseColor = triangle.baseColor();
                colorA = blendLight(baseColor, lightA);
                colorB = blendLight(baseColor, lightB);
                colorC = blendLight(baseColor, lightC);
            }

            relit.add(new ModelTriangle(triangle.a(), triangle.b(), triangle.c(),
                    colorA, colorB, colorC, triangle.textureId(), triangle.alpha(),
                    triangle.priority(), triangle.renderType(), triangle.uA(), triangle.vA(),
                    triangle.uB(), triangle.vB(), triangle.uC(), triangle.vC(),
                    triangle.baseColor(), triangle.depthBias()));
        }

        return new ModelRenderPacket(packet.anchor(), packet.objectId(), packet.category(),
                vertices, List.copyOf(relit), packet.textureTriangles(), packet.animationId(),
                packet.minX(), packet.minY(), packet.minZ(), packet.maxX(), packet.maxY(), packet.maxZ(),
                packet.supportsAnimation(), packet.supportsParticles(), packet.placementHeight(),
                packet.roofRelated(), packet.renderMode());
    }

    private static int calculateLight(ModelVertex v, LightingProfile lighting, int ambient, int intensity) {
        return ambient + (lighting.lightX() * v.normalX() + lighting.lightY() * v.normalY()
                + lighting.lightZ() * v.normalZ()) / Math.max(1, intensity * Math.max(1, v.normalMagnitude()));
    }

    private static int blendLight(int hsl, int light) {
        if (hsl == -1) return 12345678;
        if (hsl == -2) return 0;
        int clamped = clamp(light, 0, 127);
        return (hsl & 0xFC7F) | (clamped << 7);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
