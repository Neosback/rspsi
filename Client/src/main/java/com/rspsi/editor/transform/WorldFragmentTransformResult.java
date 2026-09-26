package com.rspsi.editor.transform;

import com.rspsi.editor.model.WorldFragment;

import java.util.List;

/**
 * Result of an exact/native-or-best-representable fragment transform.
 *
 * <p>This remains a minimal Java record shell because its compact constructor historically
 * replaces the diagnostics component with a defensive immutable copy before storing record
 * components. Behavioral validation is centralized in
 * {@link WorldFragmentTransformResultSemantics}.</p>
 */
public record WorldFragmentTransformResult(
        WorldFragment fragment,
        List<Diagnostic> diagnostics
) {
    public WorldFragmentTransformResult {
        fragment = WorldFragmentTransformResultSemantics.requireFragment(fragment);
        diagnostics = WorldFragmentTransformResultSemantics.copyDiagnostics(diagnostics);
    }

    public enum DiagnosticCode {
        /**
         * OSRS locations support quarter-turn rotation but no arbitrary model
         * mirror. The structure layout/orientation was reflected, while model
         * chirality may remain visually unmirrored.
         */
        OBJECT_MODEL_MIRROR_NOT_NATIVE,

        /**
         * The semantically reflected orientation would swap a non-square
         * definition footprint. The transformer preserved the mirrored
         * occupied footprint and selected the closest representable rotation.
         */
        OBJECT_ORIENTATION_APPROXIMATED
    }

    public record Diagnostic(
            DiagnosticCode code,
            String message
    ) {
        public Diagnostic {
            code = WorldFragmentTransformResultSemantics.requireDiagnosticCode(code);
            message = WorldFragmentTransformResultSemantics.requireDiagnosticMessage(message);
        }
    }
}
