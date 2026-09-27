package com.rspsi.studio

import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL30
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.Platform

/**
 * The single application window and its OpenGL 3.3 core context (forward-compatible on macOS).
 * Everything Studio draws, ImGui and the scene viewport alike, shares this context.
 */
class NativeWindow(width: Int, height: Int, title: String) : AutoCloseable {
    private val errorCallback: GLFWErrorCallback
    private val handle: Long
    private var closed = false

    init {
        require(width >= 1 && height >= 1) { "Window dimensions must be positive" }
        errorCallback = GLFWErrorCallback.createPrint(System.err).set()
        if (!GLFW.glfwInit()) {
            errorCallback.free()
            throw IllegalStateException("Unable to initialize GLFW")
        }
        GLFW.glfwDefaultWindowHints()
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_TRUE)
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE)
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3)
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3)
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE)
        if (Platform.get() == Platform.MACOSX) GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE)
        handle = GLFW.glfwCreateWindow(width, height, title, 0L, 0L)
        if (handle == 0L) {
            GLFW.glfwTerminate()
            errorCallback.free()
            throw IllegalStateException("Unable to create the OpenGL 3.3 window")
        }
        GLFW.glfwMakeContextCurrent(handle)
        GL.createCapabilities()
        GLFW.glfwSwapInterval(1)
        GLFW.glfwShowWindow(handle)
    }

    fun handle(): Long = handle

    fun pollEvents() {
        ensureOpen()
        GLFW.glfwPollEvents()
    }

    /** Clears the default framebuffer before Dear ImGui submits the frame. */
    fun clearFrame() {
        ensureOpen()
        val size = framebufferSize()
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0)
        GL11.glViewport(0, 0, size[0], size[1])
        GL11.glClearColor(0.063f, 0.094f, 0.153f, 1.0f)
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT or GL11.GL_DEPTH_BUFFER_BIT)
    }

    fun swapBuffers() {
        ensureOpen()
        GLFW.glfwSwapBuffers(handle)
    }

    fun shouldClose(): Boolean {
        ensureOpen()
        return GLFW.glfwWindowShouldClose(handle)
    }

    fun requestClose() {
        ensureOpen()
        GLFW.glfwSetWindowShouldClose(handle, true)
    }

    /** Window size in screen coordinates. */
    fun windowSize(): IntArray = size(GLFW::glfwGetWindowSize)

    /** Framebuffer size in pixels (larger than [windowSize] on HiDPI screens). */
    fun framebufferSize(): IntArray = size(GLFW::glfwGetFramebufferSize)

    override fun close() {
        if (closed) return
        closed = true
        GLFW.glfwDestroyWindow(handle)
        GLFW.glfwTerminate()
        errorCallback.free()
    }

    private inline fun size(query: (Long, java.nio.IntBuffer, java.nio.IntBuffer) -> Unit): IntArray {
        ensureOpen()
        MemoryStack.stackPush().use { stack ->
            val width = stack.mallocInt(1)
            val height = stack.mallocInt(1)
            query(handle, width, height)
            return intArrayOf(width.get(0), height.get(0))
        }
    }

    private fun ensureOpen() = check(!closed) { "Native window is closed" }
}
