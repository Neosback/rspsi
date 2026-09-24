package com.rspsi.editor.integration.semantic;

/** Directed relationship types used by the first read-only content graph. */
public enum SemanticRelationKind {
    DECLARED_IN,
    OWNS,
    TARGETS,
    IDENTIFIED_BY,
    INHERITS,
    CONTENT_GROUP,
    HAS_PARAM,
    PARAM_VALUE,
    REFERENCES,
    BINDS_STATE,
    USES_STATE
}
