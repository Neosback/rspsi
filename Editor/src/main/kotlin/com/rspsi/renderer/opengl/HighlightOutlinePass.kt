package com.rspsi.renderer.opengl

import com.rspsi.renderer.opengl.shader.GlShaderProgram
import com.rspsi.renderer.opengl.shader.ShaderSourceLoader
import org.lwjgl.opengl.GL11.GL_BLEND
import org.lwjgl.opengl.GL11.GL_FLOAT
import org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER
import org.lwjgl.opengl.GL15.GL_STREAM_DRAW
import org.lwjgl.opengl.GL15.glBindBuffer
import org.lwjgl.opengl.GL15.glBufferData
import org.lwjgl.opengl.GL15.glDeleteBuffers
import org.lwjgl.opengl.GL15.glGenBuffers
import org.lwjgl.opengl.GL20.glEnableVertexAttribArray
import org.lwjgl.opengl.GL20.glVertexAttribPointer
import org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT
import org.lwjgl.opengl.GL11.GL_CULL_FACE
import org.lwjgl.opengl.GL11.GL_DEPTH_TEST
import org.lwjgl.opengl.GL11.GL_FILL
import org.lwjgl.opengl.GL11.GL_FRONT_AND_BACK
import org.lwjgl.opengl.GL11.GL_NEAREST
import org.lwjgl.opengl.GL11.GL_ONE
import org.lwjgl.opengl.GL11.GL_RGBA
import org.lwjgl.opengl.GL11.GL_TEXTURE_2D
import org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER
import org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER
import org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S
import org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T
import org.lwjgl.opengl.GL11.GL_TRIANGLES
import org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE
import org.lwjgl.opengl.GL11.GL_VIEWPORT
import org.lwjgl.opengl.GL11.glBindTexture
import org.lwjgl.opengl.GL11.glBlendFunc
import org.lwjgl.opengl.GL11.glClear
import org.lwjgl.opengl.GL11.glClearColor
import org.lwjgl.opengl.GL11.glDeleteTextures
import org.lwjgl.opengl.GL11.glDisable
import org.lwjgl.opengl.GL11.glDrawArrays
import org.lwjgl.opengl.GL11.glEnable
import org.lwjgl.opengl.GL11.glGenTextures
import org.lwjgl.opengl.GL11.glGetIntegerv
import org.lwjgl.opengl.GL11.glPolygonMode
import org.lwjgl.opengl.GL11.glTexImage2D
import org.lwjgl.opengl.GL11.glTexParameteri
import org.lwjgl.opengl.GL11.glViewport
import org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE
import org.lwjgl.opengl.GL13.GL_TEXTURE0
import org.lwjgl.opengl.GL13.glActiveTexture
import org.lwjgl.opengl.GL20.glDeleteProgram
import org.lwjgl.opengl.GL20.glGetUniformLocation
import org.lwjgl.opengl.GL20.glUniform1f
import org.lwjgl.opengl.GL20.glUniform1i
import org.lwjgl.opengl.GL20.glUniform2f
import org.lwjgl.opengl.GL20.glUniform4f
import org.lwjgl.opengl.GL20.glUseProgram
import org.lwjgl.opengl.GL30.GL_COLOR_ATTACHMENT0
import org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER
import org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER_BINDING
import org.lwjgl.opengl.GL30.GL_FRAMEBUFFER
import org.lwjgl.opengl.GL30.glBindFramebuffer
import org.lwjgl.opengl.GL30.glBindVertexArray
import org.lwjgl.opengl.GL30.glDeleteFramebuffers
import org.lwjgl.opengl.GL30.glDeleteVertexArrays
import org.lwjgl.opengl.GL30.glFramebufferTexture2D
import org.lwjgl.opengl.GL30.glGenFramebuffers
import org.lwjgl.opengl.GL30.glGenVertexArrays
import org.lwjgl.opengl.GL31.GL_INVALID_INDEX
import org.lwjgl.opengl.GL31.glGetUniformBlockIndex
import org.lwjgl.opengl.GL31.glUniformBlockBinding

/**
 * Hover and selection outlines drawn on the GPU, in the style of RuneLite's
 * `ModelOutlineRenderer` and `InteractHighlight` defaults.
 *
 * Only the highlighted index ranges are re-submitted, into a single-sample mask with no depth
 * test (red = hovered, green = selected), so a silhouette stays readable behind walls exactly as
 * RuneLite's outline does. A full-screen pass dilates the mask into a feathered outline in a
 * transparent overlay texture that the viewport layers over the cached scene image, so moving
 * the pointer never redraws the scene. Tiles use their own terrain triangles, so an outline
 * follows the tile's real overlay shape and slope.
 */
internal class HighlightOutlinePass : AutoCloseable {
    private var maskProgram = 0
    private var outlineProgram = 0
    private var maskColorLocation = -1
    private var framebuffer = 0
    private var maskTexture = 0
    private var overlayFramebuffer = 0
    private var overlayTexture = 0
    private var emptyVao = 0
    private var geometryVao = 0
    private var geometryVbo = 0
    private var width = 0
    private var height = 0

    fun initialize(sources: ShaderSourceLoader) {
        if (maskProgram != 0) return
        maskProgram = GlShaderProgram.link(sources.load("highlight/mask.vert"), sources.load("highlight/mask.frag"))
        outlineProgram = GlShaderProgram.link(
            sources.load("highlight/fullscreen.vert"),
            sources.load("highlight/outline.frag"),
        )
        val block = glGetUniformBlockIndex(maskProgram, "FrameUniforms")
        if (block != GL_INVALID_INDEX) glUniformBlockBinding(maskProgram, block, FrameUniformBuffer.BINDING_POINT)
        maskColorLocation = glGetUniformLocation(maskProgram, "uMaskColor")
        emptyVao = glGenVertexArrays()
        geometryVao = glGenVertexArrays()
        geometryVbo = glGenBuffers()
        glBindVertexArray(geometryVao)
        glBindBuffer(GL_ARRAY_BUFFER, geometryVbo)
        glEnableVertexAttribArray(0)
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 12, 0L)
        glBindVertexArray(0)
        glBindBuffer(GL_ARRAY_BUFFER, 0)
    }

    /**
     * Renders outlines of the given world-space triangles (x, y, z per vertex) into the
     * overlay texture and returns it, or 0 when both are empty. The frame uniform block must
     * hold the camera of the scene image it overlays. The triangles come from a stable pick
     * plan, so animation swapping the scene plan never forces this geometry to be rebuilt.
     */
    fun render(hovered: FloatArray, selected: FloatArray, targetWidth: Int, targetHeight: Int): Int {
        if (maskProgram == 0 || (hovered.isEmpty() && selected.isEmpty()) || targetWidth <= 0 || targetHeight <= 0) {
            return 0
        }
        val previousFramebuffer = IntArray(1)
        glGetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING, previousFramebuffer)
        val previousViewport = IntArray(4)
        glGetIntegerv(GL_VIEWPORT, previousViewport)
        ensureTargets(targetWidth, targetHeight)

        // One upload: hovered vertices first, then selected.
        val combined = FloatArray(hovered.size + selected.size)
        System.arraycopy(hovered, 0, combined, 0, hovered.size)
        System.arraycopy(selected, 0, combined, hovered.size, selected.size)
        glBindBuffer(GL_ARRAY_BUFFER, geometryVbo)
        glBufferData(GL_ARRAY_BUFFER, combined, GL_STREAM_DRAW)
        glBindBuffer(GL_ARRAY_BUFFER, 0)

        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer)
        glViewport(0, 0, width, height)
        glClearColor(0f, 0f, 0f, 0f)
        glClear(GL_COLOR_BUFFER_BIT)
        glDisable(GL_DEPTH_TEST)
        glDisable(GL_CULL_FACE)
        glPolygonMode(GL_FRONT_AND_BACK, GL_FILL)
        glEnable(GL_BLEND)
        glBlendFunc(GL_ONE, GL_ONE)
        glUseProgram(maskProgram)
        glBindVertexArray(geometryVao)
        if (hovered.isNotEmpty()) {
            glUniform4f(maskColorLocation, 1f, 0f, 0f, 1f)
            glDrawArrays(GL_TRIANGLES, 0, hovered.size / 3)
        }
        if (selected.isNotEmpty()) {
            glUniform4f(maskColorLocation, 0f, 1f, 0f, 1f)
            glDrawArrays(GL_TRIANGLES, hovered.size / 3, selected.size / 3)
        }
        glBindVertexArray(0)
        glDisable(GL_BLEND)

        glBindFramebuffer(GL_FRAMEBUFFER, overlayFramebuffer)
        glClear(GL_COLOR_BUFFER_BIT)
        glUseProgram(outlineProgram)
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, maskTexture)
        glUniform1i(glGetUniformLocation(outlineProgram, "uMask"), 0)
        glUniform2f(glGetUniformLocation(outlineProgram, "uTexel"), 1f / width, 1f / height)
        setColor("uHoverColor", HOVER_RGBA)
        setColor("uSelectColor", SELECT_RGBA)
        glUniform1f(glGetUniformLocation(outlineProgram, "uWidth"), OUTLINE_WIDTH)
        glUniform1f(glGetUniformLocation(outlineProgram, "uFeather"), OUTLINE_FEATHER)
        glUniform1f(glGetUniformLocation(outlineProgram, "uSelectFill"), SELECT_FILL)
        glBindVertexArray(emptyVao)
        glDrawArrays(GL_TRIANGLES, 0, 3)
        glBindVertexArray(0)
        glBindTexture(GL_TEXTURE_2D, 0)
        glUseProgram(0)
        glEnable(GL_DEPTH_TEST)

        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, previousFramebuffer[0])
        glViewport(previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3])
        return overlayTexture
    }

    private fun setColor(name: String, rgba: Int) {
        glUniform4f(
            glGetUniformLocation(outlineProgram, name),
            ((rgba ushr 24) and 0xFF) / 255f,
            ((rgba ushr 16) and 0xFF) / 255f,
            ((rgba ushr 8) and 0xFF) / 255f,
            (rgba and 0xFF) / 255f,
        )
    }

    private fun ensureTargets(targetWidth: Int, targetHeight: Int) {
        if (framebuffer != 0 && width == targetWidth && height == targetHeight) return
        maskTexture = allocateTexture(maskTexture, targetWidth, targetHeight)
        overlayTexture = allocateTexture(overlayTexture, targetWidth, targetHeight)
        if (framebuffer == 0) framebuffer = glGenFramebuffers()
        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer)
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, maskTexture, 0)
        if (overlayFramebuffer == 0) overlayFramebuffer = glGenFramebuffers()
        glBindFramebuffer(GL_FRAMEBUFFER, overlayFramebuffer)
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, overlayTexture, 0)
        width = targetWidth
        height = targetHeight
    }

    private fun allocateTexture(existing: Int, targetWidth: Int, targetHeight: Int): Int {
        val texture = if (existing == 0) glGenTextures() else existing
        glBindTexture(GL_TEXTURE_2D, texture)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, targetWidth, targetHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0L)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        glBindTexture(GL_TEXTURE_2D, 0)
        return texture
    }

    override fun close() {
        if (maskProgram != 0) glDeleteProgram(maskProgram)
        if (outlineProgram != 0) glDeleteProgram(outlineProgram)
        if (framebuffer != 0) glDeleteFramebuffers(framebuffer)
        if (maskTexture != 0) glDeleteTextures(maskTexture)
        if (overlayFramebuffer != 0) glDeleteFramebuffers(overlayFramebuffer)
        if (overlayTexture != 0) glDeleteTextures(overlayTexture)
        if (emptyVao != 0) glDeleteVertexArrays(emptyVao)
        if (geometryVao != 0) glDeleteVertexArrays(geometryVao)
        if (geometryVbo != 0) glDeleteBuffers(geometryVbo)
        geometryVao = 0
        geometryVbo = 0
        maskProgram = 0
        outlineProgram = 0
        framebuffer = 0
        maskTexture = 0
        overlayFramebuffer = 0
        overlayTexture = 0
        emptyVao = 0
    }

    companion object {
        /** RuneLite InteractHighlight object hover default, 0x9000FFFF (RGBA here). */
        const val HOVER_RGBA = 0x00FFFF90

        /** RuneLite InteractHighlight object interact default, 0x90FF0000 (RGBA here). */
        const val SELECT_RGBA = 0xFF000090.toInt()

        /** RuneLite default border width and feather. */
        const val OUTLINE_WIDTH = 4f
        const val OUTLINE_FEATHER = 4f

        /** Interior tint of selected shapes, as a fraction of the selection colour's alpha. */
        const val SELECT_FILL = 0.35f
    }
}
