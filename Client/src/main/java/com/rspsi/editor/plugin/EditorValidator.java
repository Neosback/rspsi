package com.rspsi.editor.plugin;

import com.rspsi.editor.validation.ValidationIssue;

import java.util.List;

/** Neutral document validator contributed by a plugin. */
@FunctionalInterface
public interface EditorValidator {
    List<ValidationIssue> validate(EditorPluginContext context);
}
