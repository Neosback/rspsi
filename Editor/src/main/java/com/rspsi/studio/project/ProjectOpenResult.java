package com.rspsi.studio.project;

import com.rspsi.cache.workspace.LoadedOsrsCacheSession;
import com.rspsi.project.StudioProjectDescriptor;
import com.rspsi.server.ServerProjectInspection;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public record ProjectOpenResult(
        StudioProjectDescriptor project,
        Path cachePath,
        LoadedOsrsCacheSession cache,
        Optional<ServerProjectInspection> integrationInspection) {

    public ProjectOpenResult {
        project = Objects.requireNonNull(project, "project");
        cachePath = Objects.requireNonNull(cachePath, "cachePath").toAbsolutePath().normalize();
        cache = Objects.requireNonNull(cache, "cache");
        integrationInspection = integrationInspection == null
                ? Optional.empty() : integrationInspection;
    }
}
