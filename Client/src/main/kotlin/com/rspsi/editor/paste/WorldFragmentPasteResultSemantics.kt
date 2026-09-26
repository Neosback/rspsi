package com.rspsi.editor.paste

import com.rspsi.editor.WorldRegionSessionWindow
import com.rspsi.editor.change.ChangePlan
import com.rspsi.editor.model.WorldTile

/**
 * Canonical behavior for [WorldFragmentPasteResult].
 *
 * The public result remains a minimal Java record shell because its compact constructor stores a
 * defensive immutable copy of conflicts. Commit-policy behavior and nested conflict validation are
 * centralized here.
 */
object WorldFragmentPasteResultSemantics {
    @JvmStatic
    fun requireCandidatePlan(plan: ChangePlan?): ChangePlan =
        plan ?: throw NullPointerException("candidatePlan")

    @JvmStatic
    fun copyConflicts(
        conflicts: List<WorldFragmentPasteResult.Conflict>?,
    ): List<WorldFragmentPasteResult.Conflict> =
        java.util.List.copyOf(
            conflicts ?: throw NullPointerException("conflicts"),
        )

    @JvmStatic
    fun requireConflictMode(
        mode: WorldFragmentPastePolicy.ConflictMode?,
    ): WorldFragmentPastePolicy.ConflictMode =
        mode ?: throw NullPointerException("conflictMode")

    @JvmStatic
    fun canCommit(result: WorldFragmentPasteResult): Boolean =
        result.conflicts().isEmpty() ||
            result.conflictMode() == WorldFragmentPastePolicy.ConflictMode.SKIP

    @JvmStatic
    fun partial(result: WorldFragmentPasteResult): Boolean =
        result.conflicts().isNotEmpty() &&
            result.conflictMode() == WorldFragmentPastePolicy.ConflictMode.SKIP

    @JvmStatic
    fun requireCommittablePlan(result: WorldFragmentPasteResult): ChangePlan {
        if (!canCommit(result)) {
            throw IllegalStateException(
                "Fragment paste has " + result.conflicts().size +
                    " unresolved planning conflict(s)",
            )
        }
        return result.candidatePlan()
    }

    /**
     * Preserves the historical validation order: null-check the commit window before evaluating
     * whether the candidate plan is committable.
     */
    @JvmStatic
    fun commit(
        result: WorldFragmentPasteResult,
        window: WorldRegionSessionWindow?,
    ): Boolean {
        val safeWindow = window ?: throw NullPointerException("window")
        return safeWindow.commit(requireCommittablePlan(result))
    }

    @JvmStatic
    fun requireConflictCode(
        code: WorldFragmentPasteResult.ConflictCode?,
    ): WorldFragmentPasteResult.ConflictCode =
        code ?: throw NullPointerException("code")

    @JvmStatic
    fun requireConflictTile(tile: WorldTile?): WorldTile =
        tile ?: throw NullPointerException("tile")

    @JvmStatic
    fun requireConflictMessage(message: String?): String {
        val safeMessage = message ?: throw NullPointerException("message")
        if (safeMessage.isBlank()) {
            throw IllegalArgumentException("Paste conflict message cannot be blank")
        }
        return safeMessage
    }
}
