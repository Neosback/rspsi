package com.rspsi.server;

/** Read-only Git metadata captured while inspecting a server checkout. */
public record ServerGitState(
        boolean available,
        String commit,
        String branch,
        boolean dirty) {
    public ServerGitState {
        commit = commit == null ? "" : commit.trim();
        branch = branch == null ? "" : branch.trim();
    }

    public static ServerGitState unavailable() {
        return new ServerGitState(false, "", "", false);
    }
}
