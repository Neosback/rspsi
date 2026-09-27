package com.rspsi.studio

import com.rspsi.studio.theme.StudioFonts
import com.rspsi.studio.theme.StudioTheme
import imgui.ImGui
import imgui.flag.ImGuiConfigFlags
import imgui.gl3.ImGuiImplGl3
import imgui.glfw.ImGuiImplGlfw

/** Binds Dear ImGui to the application's GLFW window and OpenGL context. */
class ImGuiHost : AutoCloseable {
    private val glfw = ImGuiImplGlfw()
    private val gl3 = ImGuiImplGl3()
    private var initialized = false
    private var closed = false

    fun initialize(window: NativeWindow) {
        check(!initialized) { "ImGui is already initialized" }
        ImGui.createContext()
        StudioTheme.apply()
        StudioFonts.apply(window.handle())
        val io = ImGui.getIO()
        io.addConfigFlags(ImGuiConfigFlags.DockingEnable)
        // One window, one GL context: panels never become native OS windows, so they share the
        // scene viewport's context.
        io.removeConfigFlags(ImGuiConfigFlags.ViewportsEnable)
        // NativeWorkspaceLayoutStore owns the versioned layout under ~/.rspsi/ui; ImGui never
        // writes its own ini beside the project.
        io.iniFilename = null
        // Windows move only by their title bar, so dragging inside one (rotating a model
        // preview, painting a swatch) never drags the whole window.
        io.configWindowsMoveFromTitleBarOnly = true
        if (!glfw.initForOpenGL(window.handle(), true)) {
            ImGui.destroyContext()
            throw IllegalStateException("Unable to initialize the Dear ImGui GLFW backend")
        }
        if (!gl3.init("#version 330 core")) {
            glfw.shutdown()
            ImGui.destroyContext()
            throw IllegalStateException("Unable to initialize the Dear ImGui OpenGL 3 backend")
        }
        initialized = true
    }

    fun beginFrame() {
        ensureReady()
        glfw.newFrame()
        gl3.newFrame()
        ImGui.newFrame()
    }

    fun endFrame() {
        ensureReady()
        ImGui.render()
        gl3.renderDrawData(ImGui.getDrawData())
    }

    override fun close() {
        if (closed) return
        closed = true
        if (initialized) {
            gl3.shutdown()
            glfw.shutdown()
            ImGui.destroyContext()
            initialized = false
        }
    }

    private fun ensureReady() = check(initialized && !closed) { "Dear ImGui host is not available" }
}
