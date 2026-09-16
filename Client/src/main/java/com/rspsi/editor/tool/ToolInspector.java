package com.rspsi.editor.tool;

import java.util.List;

/** Describes tool settings for JavaFX, ImGui, or another frontend. */
public interface ToolInspector {
    List<PropertyDescriptor> properties();
}
