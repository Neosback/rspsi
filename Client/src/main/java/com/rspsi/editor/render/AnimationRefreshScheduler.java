package com.rspsi.editor.render;

import com.rspsi.cache.definition.DefinitionProvider;
import com.rspsi.cache.definition.SequenceDefinitionView;
import com.rspsi.osrs.rules.model.AnimationResolver;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Finds the next client cycle at which a rendered object animation can change
 * presentation.
 *
 * <p>Legacy sequences often hold one frame for many client cycles. Rebuilding
 * model packets on every cycle wastes the scene-loader thread and continuously
 * creates new geometry-plan identities even though the visible frame is
 * unchanged. Cached skeletal sequences still advance at their real per-cycle
 * cadence.</p>
 */
public final class AnimationRefreshScheduler {
    public static final int NONE = -1;

    private AnimationRefreshScheduler() {
    }

    public static int nextPresentationCycle(RenderWindowScene scene,
                                            DefinitionProvider definitions,
                                            int clientCycle) {
        Objects.requireNonNull(scene, "scene");
        Objects.requireNonNull(definitions, "definitions");
        if (clientCycle < 0) {
            throw new IllegalArgumentException("Client cycle cannot be negative");
        }

        int next = NONE;
        Set<Integer> visitedSequences = new HashSet<>();
        for (var packets : scene.modelPackets().values()) {
            for (ModelRenderPacket packet : packets) {
                ModelAnimationState state = packet.animationState();
                if (!state.active() || !visitedSequences.add(state.sequenceId())) {
                    continue;
                }
                SequenceDefinitionView sequence =
                        definitions.sequence(state.sequenceId()).orElse(null);
                if (sequence == null) {
                    // Definitions are immutable for the loaded cache session;
                    // unresolved metadata cannot become animated by polling.
                    continue;
                }

                int candidate = nextPresentationCycle(sequence, clientCycle);
                if (candidate >= 0 && (next < 0 || candidate < next)) {
                    next = candidate;
                }
            }
        }
        return next;
    }

    static int nextPresentationCycle(SequenceDefinitionView sequence, int clientCycle) {
        Objects.requireNonNull(sequence, "sequence");
        if (clientCycle < 0) {
            throw new IllegalArgumentException("Client cycle cannot be negative");
        }

        if (sequence.cachedSkeletal()) {
            return AnimationResolver.nextCachedFrameChangeCycle(
                    sequence.cachedFrameCount(), sequence.frameStep(), clientCycle);
        }

        int[] frameIds = sequence.frameIds();
        if (frameIds.length == 0) {
            return NONE;
        }
        return AnimationResolver.nextAnimationFrameChangeCycle(
                frameIds.length, sequence.frameLengths(), sequence.frameStep(), clientCycle);
    }
}
