package com.rspsi.studio;

import com.rspsi.cache.workspace.CacheSessionState;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceManagerTest {

    @Test
    void startsWithDashboardAsTheOnlyOpenWorkspace() {
        WorkspaceManager manager = new WorkspaceManager();

        assertEquals(WorkspaceManager.Workspace.DASHBOARD, manager.active());
        assertEquals(Set.of(WorkspaceManager.Workspace.DASHBOARD), manager.open());
        assertTrue(manager.isOpen(WorkspaceManager.Workspace.DASHBOARD));
        assertNull(manager.consumePendingFocus());
    }

    @Test
    void onlyReadyCachesCanOpenCacheBackedWorkspaces() {
        WorkspaceManager manager = new WorkspaceManager();
        Set<WorkspaceManager.Workspace> openView = manager.open();

        assertFalse(manager.openMapEditor(CacheSessionState.LOADING));
        assertEquals(Set.of(WorkspaceManager.Workspace.DASHBOARD), openView);

        assertTrue(manager.openMapEditor(CacheSessionState.READY));
        assertTrue(manager.openInterfaceStudio(CacheSessionState.READY));
        assertTrue(manager.openObjectStudio(CacheSessionState.READY));

        assertTrue(openView.contains(WorkspaceManager.Workspace.MAP_EDITOR));
        assertTrue(openView.contains(WorkspaceManager.Workspace.INTERFACE_STUDIO));
        assertTrue(openView.contains(WorkspaceManager.Workspace.OBJECT_STUDIO));
        assertThrows(
                UnsupportedOperationException.class,
                () -> openView.add(WorkspaceManager.Workspace.MAP_EDITOR));
        assertEquals(WorkspaceManager.Workspace.OBJECT_STUDIO, manager.active());
    }

    @Test
    void focusRequestsAreConsumedExactlyOnce() {
        WorkspaceManager manager = new WorkspaceManager();

        manager.focus(WorkspaceManager.Workspace.INTERFACE_STUDIO);

        assertEquals(WorkspaceManager.Workspace.INTERFACE_STUDIO, manager.active());
        assertEquals(
                WorkspaceManager.Workspace.INTERFACE_STUDIO,
                manager.consumePendingFocus());
        assertNull(manager.consumePendingFocus());
    }

    @Test
    void closingTheActiveWorkspaceFallsBackToDashboard() {
        WorkspaceManager manager = new WorkspaceManager();
        manager.openMapEditor(CacheSessionState.READY);
        manager.consumePendingFocus();

        manager.close(WorkspaceManager.Workspace.MAP_EDITOR);

        assertFalse(manager.isOpen(WorkspaceManager.Workspace.MAP_EDITOR));
        assertEquals(WorkspaceManager.Workspace.DASHBOARD, manager.active());
        assertEquals(
                WorkspaceManager.Workspace.DASHBOARD,
                manager.consumePendingFocus());

        manager.close(WorkspaceManager.Workspace.DASHBOARD);
        assertTrue(manager.isOpen(WorkspaceManager.Workspace.DASHBOARD));
    }

    @Test
    void resetClosesProjectWorkspacesAndRequestsDashboardFocus() {
        WorkspaceManager manager = new WorkspaceManager();
        manager.openMapEditor(CacheSessionState.READY);
        manager.openObjectStudio(CacheSessionState.READY);

        manager.reset();

        assertEquals(Set.of(WorkspaceManager.Workspace.DASHBOARD), manager.open());
        assertEquals(WorkspaceManager.Workspace.DASHBOARD, manager.active());
        assertEquals(
                WorkspaceManager.Workspace.DASHBOARD,
                manager.consumePendingFocus());
    }
}
