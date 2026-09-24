package com.rspsi.editor.transform;

import com.rspsi.editor.model.WorldFragment;

import java.util.List;
import java.util.Objects;

/** Result of an exact/native-or-best-representable fragment transform. */
public record WorldFragmentTransformResult(
        WorldFragment fragment,
        List<Diagnostic> diagnostics
) {
    public WorldFragmentTransformResult {
        fragment = Objects.requireNonNull(fragment, "fragment");
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
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
            code = Objects.requireNonNull(code, "code");
            message = Objects.requireNonNull(message, "message");
            if (message.isBlank()) {
                throw new IllegalArgumentException("Transform diagnostic cannot be blank");
            }
        }
    }
}
