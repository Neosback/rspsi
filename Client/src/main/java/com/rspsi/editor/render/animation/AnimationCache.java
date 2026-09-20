package com.rspsi.editor.render.animation;

import com.rspsi.cache.definition.AnimationFrameView;
import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.ModelGeometryView;
import com.rspsi.cache.definition.SequenceDefinitionView;
import com.rspsi.cache.definition.SkeletonDefinitionView;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized memory cache for animation sequences, skeleton bone transforms,
 * animation frames, and base model geometry.
 *
 * <p>Shared between Map Studio, Interface Studio, and Object Studio to avoid
 * re-decoding models or textures during dynamic entity pose updates.</p>
 */
public final class AnimationCache {
    private final Map<Integer, Optional<SequenceDefinitionView>> sequences = new ConcurrentHashMap<>();
    private final Map<Integer, Optional<SkeletonDefinitionView>> skeletons = new ConcurrentHashMap<>();
    private final Map<Integer, Optional<AnimationFrameView>> frames = new ConcurrentHashMap<>();
    private final Map<Integer, Optional<ModelGeometryView>> models = new ConcurrentHashMap<>();

    public Optional<SequenceDefinitionView> sequence(int sequenceId, DefinitionProvider definitions) {
        if (sequenceId < 0 || definitions == null) return Optional.empty();
        return sequences.computeIfAbsent(sequenceId, definitions::sequence);
    }

    public Optional<SkeletonDefinitionView> skeleton(int skeletonId, DefinitionProvider definitions) {
        if (skeletonId < 0 || definitions == null) return Optional.empty();
        return skeletons.computeIfAbsent(skeletonId, definitions::skeleton);
    }

    public Optional<AnimationFrameView> frame(int frameId, DefinitionProvider definitions) {
        if (frameId < 0 || definitions == null) return Optional.empty();
        return frames.computeIfAbsent(frameId, definitions::animationFrame);
    }

    public Optional<ModelGeometryView> modelGeometry(int modelId, DefinitionProvider definitions) {
        if (modelId < 0 || definitions == null) return Optional.empty();
        return models.computeIfAbsent(modelId, definitions::modelGeometry);
    }

    public void clear() {
        sequences.clear();
        skeletons.clear();
        frames.clear();
        models.clear();
    }
}
