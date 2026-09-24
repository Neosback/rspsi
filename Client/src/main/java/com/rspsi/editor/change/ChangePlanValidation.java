package com.rspsi.editor.change;

import com.rspsi.editor.model.WorldTile;

import java.util.List;
import java.util.Objects;

/** Non-destructive validation result for a world-space {@link ChangePlan}. */
public record ChangePlanValidation(
        List<Conflict> conflicts
) {
    public ChangePlanValidation {
        conflicts = List.copyOf(Objects.requireNonNull(conflicts, "conflicts"));
    }

    public boolean canCommit() {
        return conflicts.isEmpty();
    }

    public enum ConflictCode {
        UNLOADED_REGION,
        READ_ONLY_REGION,
        STALE_SOURCE
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
                throw new IllegalArgumentException("Conflict message cannot be blank");
            }
        }
    }
}
