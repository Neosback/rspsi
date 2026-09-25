/**
 * Renderer-neutral scene compilation and presentation contracts.
 *
 * <p>Authored truth lives in {@code EditorSession}/{@code WorldDocument}. This package converts
 * resolved semantic state into renderer-neutral packets and immutable configuration. Renderer
 * preferences enter through typed {@code RenderSettingKeys}, are compiled by
 * {@code RenderConfigCompiler}, and are consumed through {@code RenderConfig} rather than
 * ad-hoc native booleans.</p>
 *
 * <p>Native OpenGL implementation details belong in the Editor module. Do not move GPU/window
 * state into the authored model or expose renderer bookkeeping through ordinary tool APIs.</p>
 */
package com.rspsi.editor.render;
