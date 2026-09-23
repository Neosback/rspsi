package com.rspsi.editor.render;

import com.rspsi.editor.model.InstanceChunkTemplate;
import com.rspsi.editor.model.WorldRegionWindow;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable scene-context contract shared by software and GPU renderers.
 *
 * <p>The source region window and destination scene window are deliberately
 * separate. An instance may render source regions at a different base,
 * plane, or rotation, while a normal scene still has a useful source-region
 * set and no instance templates.</p>
 */
public record SceneWindow(
        WorldRegionWindow sourceRegions,
        int sceneBaseX,
        int sceneBaseY,
        int planes,
        int border,
        int minimumRenderLevel,
        int worldViewId,
        Set<Integer> sourceRegionIds,
        List<InstanceChunkTemplate> instanceTemplates
) {
    /** Compatibility constructor before scene-level min-level/world-view state was explicit. */
    public SceneWindow(WorldRegionWindow sourceRegions,
                       int sceneBaseX,
                       int sceneBaseY,
                       int planes,
                       int border,
                       Set<Integer> sourceRegionIds,
                       List<InstanceChunkTemplate> instanceTemplates) {
        this(sourceRegions, sceneBaseX, sceneBaseY, planes, border, 0, -1,
                sourceRegionIds, instanceTemplates);
    }

    public SceneWindow {
        sourceRegions = Objects.requireNonNull(sourceRegions, "sourceRegions");
        if (sceneBaseX < 0 || sceneBaseY < 0 || planes <= 0 || border < 0) {
            throw new IllegalArgumentException("Invalid scene window coordinates or dimensions");
        }
        if (minimumRenderLevel < 0 || minimumRenderLevel >= planes) {
            throw new IllegalArgumentException("Minimum render level must be inside the scene plane range");
        }
        Set<Integer> ids = new LinkedHashSet<>(Objects.requireNonNull(sourceRegionIds, "sourceRegionIds"));
        if (ids.stream().anyMatch(id -> id == null || id < 0 || id > 0xFFFF)) {
            throw new IllegalArgumentException("Source region IDs must be unsigned 16-bit values");
        }
        sourceRegionIds = Set.copyOf(ids);
        instanceTemplates = List.copyOf(Objects.requireNonNull(instanceTemplates, "instanceTemplates"));
    }

    public static SceneWindow from(WorldRegionWindow sourceRegions) {
        Objects.requireNonNull(sourceRegions, "sourceRegions");
        return new SceneWindow(sourceRegions,
                sourceRegions.worldWindow().originX(),
                sourceRegions.worldWindow().originY(),
                4,
                1,
                0,
                -1,
                sourceRegions.regions().keySet(),
                List.of());
    }

    public boolean instance() {
        return !instanceTemplates.isEmpty();
    }

    public boolean completeSourceWindow() {
        return sourceRegions.complete();
    }

    /** Client-shaped RuneLite instance-template API view. */
    public InstanceTemplateGrid instanceTemplateGrid() {
        return InstanceTemplateGrid.from(this);
    }

    /** RuneLite's documented 104x104 normal / 184x184 extended coordinate layout. */
    public ExtendedSceneLayout extendedSceneLayout() {
        return ExtendedSceneLayout.runeLite();
    }

    /** RuneLite's 23x23 top-level GPU zone projection over absolute world zones. */
    public ExtendedSceneZoneLayout extendedSceneZoneLayout() {
        return new ExtendedSceneZoneLayout(extendedSceneLayout());
    }

    public SceneContract contract() {
        return SceneContract.from(this);
    }
}
