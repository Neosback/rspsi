package com.rspsi.ui.workspace;

/** Persists user interface layout only; implementations must not touch project data. */
public interface WorkspaceLayoutStore {
    WorkspaceLayout load(String workspaceId);

    void save(WorkspaceLayout layout);

    void reset(String workspaceId);
}
