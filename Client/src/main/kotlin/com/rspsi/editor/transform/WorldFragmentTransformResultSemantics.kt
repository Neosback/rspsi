package com.rspsi.editor.transform

import com.rspsi.editor.model.WorldFragment

/**
 * Canonical validation and defensive-copy semantics for [WorldFragmentTransformResult].
 *
 * The public result remains a minimal Java record shell because its compact constructor replaces
 * the diagnostics component with an immutable copy before the record stores it. Kotlin JVM records
 * cannot preserve that constructor-normalization behavior exactly.
 */
object WorldFragmentTransformResultSemantics {
    @JvmStatic
    fun requireFragment(fragment: WorldFragment?): WorldFragment =
        fragment ?: throw NullPointerException("fragment")

    @JvmStatic
    fun copyDiagnostics(
        diagnostics: List<WorldFragmentTransformResult.Diagnostic>?,
    ): List<WorldFragmentTransformResult.Diagnostic> =
        java.util.List.copyOf(
            diagnostics ?: throw NullPointerException("diagnostics"),
        )

    @JvmStatic
    fun requireDiagnosticCode(
        code: WorldFragmentTransformResult.DiagnosticCode?,
    ): WorldFragmentTransformResult.DiagnosticCode =
        code ?: throw NullPointerException("code")

    @JvmStatic
    fun requireDiagnosticMessage(message: String?): String {
        val safeMessage = message ?: throw NullPointerException("message")
        if (safeMessage.isBlank()) {
            throw IllegalArgumentException("Transform diagnostic cannot be blank")
        }
        return safeMessage
    }
}
