package com.rspsi.editor.paste;

import com.rspsi.editor.WorldRegionSessionWindow;
import com.rspsi.editor.change.ChangePlan;
import com.rspsi.editor.model.WorldTile;

import java.util.List;
import java.util.Objects;

/** Non-mutating output of fragment paste planning. */
public record WorldFragmentPasteResult(
        ChangePlan candidatePlan,
        List<Conflict> conflicts,
        WorldFragmentPastePolicy.ConflictMode conflictMode
) {
    public WorldFragmentPasteResult {
        candidatePlan = Objects.requireNonNull(candidatePlan, "candidatePlan");
        conflicts = List.copyOf(Objects.requireNonNull(conflicts, "conflicts"));
        conflictMode = Objects.requireNonNull(conflictMode, "conflictMode");
    }

    /**
     * True when handing {@link #candidatePlan()} to the world-window commit
     * boundary is allowed by this paste policy.
     */
    public boolean canCommit() {
        return conflicts.isEmpty()
                || conflictMode == WorldFragmentPastePolicy.ConflictMode.SKIP;
    }

    public boolean partial() {
        return !conflicts.isEmpty()
                && conflictMode == WorldFragmentPastePolicy.ConflictMode.SKIP;
    }

    public ChangePlan requireCommittablePlan() {
        if (!canCommit()) {
            throw new IllegalStateException(
                    "Fragment paste has " + conflicts.size()
                            + " unresolved planning conflict(s)");
        }
        return candidatePlan;
    }

    /**
     * Safe commit convenience that enforces the planning conflict policy
     * before entering the canonical multi-region transaction boundary.
     */
    public boolean commit(WorldRegionSessionWindow window) {
        return Objects.requireNonNull(window, "window")
                .commit(requireCommittablePlan());
    }

    public enum ConflictCode {
        UNLOADED_REGION,
        READ_ONLY_REGION,
        PLANE_UNAVAILABLE,
        DUPLICATE_SOURCE_TILE,
        HEIGHT_ANCHOR_UNAVAILABLE
    }

    public record Conflict(
            ConflictCode code,
            WorldTile tile,
            String message
    ) {
        public Conflict {
            code = Objects.requireNonNull(code, "code");
            tile = Objects.requireNonNull(tile, "tile");
            message = Objects.requireNonNull(message, "message");
            if (message.isBlank()) {
                throw new IllegalArgumentException("Paste conflict message cannot be blank");
            }
        }
    }
}
