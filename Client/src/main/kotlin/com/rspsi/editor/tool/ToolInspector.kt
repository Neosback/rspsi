package com.rspsi.editor.tool

/**
 * Frontend-neutral description source for a tool's editable settings.
 *
 * JavaFX, Studio/ImGui, or another frontend may project the same descriptors differently;
 * the tool contract exposes metadata only and never a frontend widget type.
 */
fun interface ToolInspector {
    fun properties(): List<PropertyDescriptor>
}
