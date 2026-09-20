package com.rspsi.studio;

import com.rspsi.cache.workspace.CacheSessionState;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Tracks which workspaces are open as persistent tabs and which one is focused.
 *
 * <p>Opening a workspace that is already open only changes focus; it never implies
 * reloading that workspace's content. Callers use {@link #isOpen(Workspace)} before
 * doing expensive (re)load work, and {@link #close(Workspace)} to actually dispose one.</p>
 */
public final class WorkspaceManager {
    public enum Workspace {
        DASHBOARD,
        MAP_EDITOR,
        INTERFACE_STUDIO,
        OBJECT_STUDIO
    }

    private final LinkedHashSet<Workspace> open = new LinkedHashSet<>(Set.of(Workspace.DASHBOARD));
    private Workspace active = Workspace.DASHBOARD;
    private Workspace pendingFocus;

    public Workspace active() {
        return active;
    }

    /** Open workspaces (tabs), in the order they were first opened. Dashboard is always present. */
    public Set<Workspace> open() {
        return Collections.unmodifiableSet(open);
    }

    public boolean isOpen(Workspace workspace) {
        return open.contains(workspace);
    }

    public void openDashboard() {
        focus(Workspace.DASHBOARD);
    }

    public boolean openMapEditor(CacheSessionState cacheState) {
        if (cacheState != CacheSessionState.READY) return false;
        open.add(Workspace.MAP_EDITOR);
        focus(Workspace.MAP_EDITOR);
        return true;
    }

    public boolean openInterfaceStudio(CacheSessionState cacheState) {
        if (cacheState != CacheSessionState.READY) return false;
        open.add(Workspace.INTERFACE_STUDIO);
        focus(Workspace.INTERFACE_STUDIO);
        return true;
    }

    public boolean openObjectStudio(CacheSessionState cacheState) {
        if (cacheState != CacheSessionState.READY) return false;
        open.add(Workspace.OBJECT_STUDIO);
        focus(Workspace.OBJECT_STUDIO);
        return true;
    }

    /** Removes a tab. Dashboard can never be closed; it is the permanent home tab. */
    public void close(Workspace workspace) {
        if (workspace == Workspace.DASHBOARD) return;
        open.remove(workspace);
        if (active == workspace) {
            focus(Workspace.DASHBOARD);
        }
    }

    public void focus(Workspace workspace) {
        active = workspace;
        pendingFocus = workspace;
    }

    /** Consumes the pending focus request; used to force-select a tab in the native tab bar once. */
    public Workspace consumePendingFocus() {
        Workspace value = pendingFocus;
        pendingFocus = null;
        return value;
    }

    /** Reconciles focus with whichever tab the native tab bar reports as selected this frame. */
    public void setActiveFromTabBar(Workspace workspace) {
        active = workspace;
    }
}
