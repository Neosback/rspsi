/**
 * Native Dear ImGui workspace projection for OpenRune Studio.
 *
 * <p>Workspace regions express workflow: the right rail owns inspection/exact-property editing
 * and settings; the left rail owns shared brush mechanics; the bottom owns the Primary Tool Rail
 * plus one active authoring Context Drawer; the floating rail/Quick Palette owns pickers and
 * compact contextual choices; HUDs remain independent viewport information.</p>
 *
 * <p>Business logic should remain in Client-neutral tools/services/descriptors. This package
 * projects those contracts into ImGui and must not create competing authored state, tool
 * registries, or renderer settings.</p>
 */
package com.rspsi.studio.ui;
