package com.rspsi.studio;

import com.rspsi.cache.workspace.CacheSessionState;

/** Application-level navigation between the dashboard and editor workspaces. */
public final class WorkspaceManager {
    public enum Workspace {
        DASHBOARD,
        MAP_EDITOR,
        INTERFACE_STUDIO,
        OBJECT_STUDIO,
        SIMULATION_STUDIO
    }

    private Workspace active = Workspace.DASHBOARD;

    public Workspace active() {
        return active;
    }

    public void openDashboard() {
        active = Workspace.DASHBOARD;
    }

    public boolean openMapEditor(CacheSessionState cacheState) {
        if (cacheState != CacheSessionState.READY) return false;
        active = Workspace.MAP_EDITOR;
        return true;
    }

    public boolean openInterfaceStudio(CacheSessionState cacheState) {
        if (cacheState != CacheSessionState.READY) return false;
        active = Workspace.INTERFACE_STUDIO;
        return true;
    }

    public boolean openObjectStudio(CacheSessionState cacheState) {
        if (cacheState != CacheSessionState.READY) return false;
        active = Workspace.OBJECT_STUDIO;
        return true;
    }
}
