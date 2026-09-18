package com.rspsi.studio;

import com.rspsi.cache.workspace.CacheSessionState;

/** Application-level navigation between the dashboard and editor workspaces. */
public final class WorkspaceManager {
    public enum Workspace { DASHBOARD, MAP_EDITOR }

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
}
