package com.rspsi.studio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DashboardViewTest {
    @Test
    void regionTextDefaultsToLumbridge() {
        DashboardView view = new DashboardView();
        assertEquals("50,50", view.regionText());
    }

    @Test
    void workspaceResetReturnsToPermanentDashboardHome() {
        WorkspaceManager workspaces = new WorkspaceManager();
        workspaces.openMapEditor(com.rspsi.cache.workspace.CacheSessionState.READY);
        workspaces.openInterfaceStudio(com.rspsi.cache.workspace.CacheSessionState.READY);

        workspaces.reset();

        assertEquals(WorkspaceManager.Workspace.DASHBOARD, workspaces.active());
        assertEquals(java.util.Set.of(WorkspaceManager.Workspace.DASHBOARD), workspaces.open());
    }
}
