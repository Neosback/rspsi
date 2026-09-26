package com.rspsi.editor.paste;

import com.rspsi.editor.WorldRegionSessionWindow;
import com.rspsi.editor.change.ChangePlan;
import com.rspsi.editor.model.WorldTile;

import java.util.List;

/**
 * Non-mutating output of fragment paste planning.
 *
 * <p>This remains a minimal Java record shell because its compact constructor historically stores
 * a defensive immutable conflict list. Commit-policy semantics are centralized in
 * {@link WorldFragmentPasteResultSemantics}.</p>
 */
public record WorldFragmentPasteResult(
        ChangePlan candidatePlan,
        List<Conflict> conflicts,
        WorldFragmentPastePolicy.ConflictMode conflictMode
) {
    public WorldFragmentPasteResult {
        candidatePlan = WorldFragmentPasteResultSemantics.requireCandidatePlan(candidatePlan);
        conflicts = WorldFragmentPasteResultSemantics.copyConflicts(conflicts);
        conflictMode = WorldFragmentPasteResultSemantics.requireConflictMode(conflictMode);
    }

    public boolean canCommit() {
        return WorldFragmentPasteResultSemantics.canCommit(this);
    }

    public boolean partial() {
        return WorldFragmentPasteResultSemantics.partial(this);
    }

    public ChangePlan requireCommittablePlan() {
        return WorldFragmentPasteResultSemantics.requireCommittablePlan(this);
    }

    public boolean commit(WorldRegionSessionWindow window) {
        return WorldFragmentPasteResultSemantics.commit(this, window);
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
            code = WorldFragmentPasteResultSemantics.requireConflictCode(code);
            tile = WorldFragmentPasteResultSemantics.requireConflictTile(tile);
            message = WorldFragmentPasteResultSemantics.requireConflictMessage(message);
        }
    }
}
