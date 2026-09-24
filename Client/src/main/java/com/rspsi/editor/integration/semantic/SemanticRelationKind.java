package com.rspsi.editor.integration.semantic;

/** Directed relationship types used by the first read-only content graph. */
public enum SemanticRelationKind {
    DECLARED_IN,
    OWNS,
    TARGETS,
    REFERENCES,
    BINDS_STATE,
    USES_STATE
}
