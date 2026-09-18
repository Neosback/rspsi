package com.rspsi.editor.render;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Applies conservative camera occlusion while preserving the immutable plan boundary. */
public final class OcclusionPlanFilter {
    private OcclusionPlanFilter() {
    }

    public static GpuUploadPlan filter(GpuUploadPlan plan, CameraState camera) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(camera, "camera");
        if (plan.occluders().isEmpty()) return plan;

        List<Integer> indices = new ArrayList<>();
        List<GpuDrawCommand> commands = new ArrayList<>();
        for (GpuDrawCommand command : plan.commands()) {
            for (int offset = command.firstIndex();
                 offset < command.firstIndex() + command.indexCount(); offset += 3) {
                GpuSceneVertex first = plan.vertices().get(plan.indices().get(offset));
                GpuSceneVertex second = plan.vertices().get(plan.indices().get(offset + 1));
                GpuSceneVertex third = plan.vertices().get(plan.indices().get(offset + 2));
                if (SceneOcclusionResolver.occludesTriangle(command, first, second, third,
                        camera, plan.occluders())) continue;
                int firstIndex = indices.size();
                indices.add(plan.indices().get(offset));
                indices.add(plan.indices().get(offset + 1));
                indices.add(plan.indices().get(offset + 2));
                commands.add(new GpuDrawCommand(command.tile(), command.layer(), command.pass(),
                        firstIndex, 3, command.textureId(), command.priority(), command.depthBias(),
                        command.objectId()));
            }
        }
        String fingerprint = fingerprint(plan.fingerprint(), camera, indices, commands);
        return new GpuUploadPlan(plan.vertices(), indices, commands, plan.textureTriangles(),
                plan.textures(), plan.occluders(), fingerprint);
    }

    private static String fingerprint(String source, CameraState camera,
                                      List<Integer> indices, List<GpuDrawCommand> commands) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String value = source + "|camera=" + camera + "|indices=" + indices
                    + "|commands=" + commands;
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte valueByte : bytes) result.append(String.format("%02x", valueByte & 0xFF));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
