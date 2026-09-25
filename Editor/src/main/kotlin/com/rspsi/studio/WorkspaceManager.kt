package com.rspsi.studio

import com.rspsi.cache.workspace.CacheSessionState
import java.util.Collections
import java.util.LinkedHashSet

/**
 * Tracks which workspaces are open as persistent tabs and which one is focused.
 *
 * Opening a workspace that is already open only changes focus; it never implies
 * reloading that workspace's content. Callers use [isOpen] before doing expensive
 * (re)load work, and [close] to actually dispose one.
 */
class WorkspaceManager {
    enum class Workspace {
        DASHBOARD,
        MAP_EDITOR,
        INTERFACE_STUDIO,
        OBJECT_STUDIO,
    }

    private val openWorkspaces = LinkedHashSet<Workspace>().apply {
        add(Workspace.DASHBOARD)
    }

    private var activeWorkspace = Workspace.DASHBOARD
    private var pendingFocus: Workspace? = null

    fun active(): Workspace = activeWorkspace

    /** Open workspaces (tabs), in the order first opened. Dashboard is always present. */
    fun open(): Set<Workspace> = Collections.unmodifiableSet(openWorkspaces)

    fun isOpen(workspace: Workspace): Boolean = workspace in openWorkspaces

    fun openDashboard() {
        focus(Workspace.DASHBOARD)
    }

    fun openMapEditor(cacheState: CacheSessionState): Boolean =
        openWhenReady(cacheState, Workspace.MAP_EDITOR)

    fun openInterfaceStudio(cacheState: CacheSessionState): Boolean =
        openWhenReady(cacheState, Workspace.INTERFACE_STUDIO)

    fun openObjectStudio(cacheState: CacheSessionState): Boolean =
        openWhenReady(cacheState, Workspace.OBJECT_STUDIO)

    /** Resets project workspaces when switching or closing the active Studio project. */
    fun reset() {
        openWorkspaces.clear()
        openWorkspaces.add(Workspace.DASHBOARD)
        activeWorkspace = Workspace.DASHBOARD
        pendingFocus = Workspace.DASHBOARD
    }

    /** Removes a tab. Dashboard can never be closed; it is the permanent home tab. */
    fun close(workspace: Workspace) {
        if (workspace == Workspace.DASHBOARD) return

        openWorkspaces.remove(workspace)
        if (activeWorkspace == workspace) {
            focus(Workspace.DASHBOARD)
        }
    }

    fun focus(workspace: Workspace) {
        activeWorkspace = workspace
        pendingFocus = workspace
    }

    /** Consumes the pending focus request used to force-select a native tab exactly once. */
    fun consumePendingFocus(): Workspace? =
        pendingFocus.also {
            pendingFocus = null
        }

    /** Reconciles focus with whichever tab the native tab bar reports as selected this frame. */
    fun setActiveFromTabBar(workspace: Workspace) {
        activeWorkspace = workspace
    }

    private fun openWhenReady(
        cacheState: CacheSessionState,
        workspace: Workspace,
    ): Boolean {
        if (cacheState != CacheSessionState.READY) return false

        openWorkspaces.add(workspace)
        focus(workspace)
        return true
    }
}
