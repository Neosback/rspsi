package com.rspsi.editor.integration.semantic;

import java.nio.file.Path;
import java.util.Objects;

/** Exact source provenance for a semantic fact. Lines and columns are one-based. */
public record SourceSpan(
        Path file,
        int startOffset,
        int endOffset,
        int startLine,
        int startColumn,
        int endLine,
        int endColumn) {

    public SourceSpan {
        file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        if (startOffset < 0 || endOffset < startOffset) {
            throw new IllegalArgumentException("invalid source offsets");
        }
        if (startLine < 1 || endLine < startLine || startColumn < 1 || endColumn < 1) {
            throw new IllegalArgumentException("invalid source line/column");
        }
    }
}
