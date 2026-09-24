package com.rspsi.studio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContentStudioViewTest {
    @Test
    void regionTextDefaultsToLumbridge() {
        ContentStudioView view = new ContentStudioView();
        assertEquals("50,50", view.regionText());
    }

    @Test
    void workspaceResetReturnsToPermanentContentStudioHome() {
        WorkspaceManager workspaces = new WorkspaceManager();
        workspaces.openMapEditor(com.rspsi.cache.workspace.CacheSessionState.READY);
        workspaces.openInterfaceStudio(com.rspsi.cache.workspace.CacheSessionState.READY);

        workspaces.reset();

        assertEquals(WorkspaceManager.Workspace.DASHBOARD, workspaces.active());
        assertEquals(java.util.Set.of(WorkspaceManager.Workspace.DASHBOARD), workspaces.open());
    }
}
