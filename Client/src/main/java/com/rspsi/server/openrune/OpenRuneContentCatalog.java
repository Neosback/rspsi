package com.rspsi.server.openrune;

import com.rspsi.editor.integration.content.ContentDiscoveryService;
import com.rspsi.editor.integration.content.ProjectLayoutResolver;

import java.nio.file.Path;
import java.util.Objects;

/** One-shot declarative content inventory for an OpenRune project. */
public final class OpenRuneContentCatalog {
    private final ProjectLayoutResolver.ResolvedLayout layout;
    private final ContentDiscoveryService.Discovery discovery;

    public OpenRuneContentCatalog(Path projectRoot) {
        Objects.requireNonNull(projectRoot, "projectRoot");
        this.layout = new OpenRuneProjectLayoutResolver().resolve(projectRoot)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Not a recognized OpenRune declarative data layout: " + projectRoot));
        this.discovery = new ContentDiscoveryService().discover(projectRoot, layout);
    }

    public ProjectLayoutResolver.ResolvedLayout layout() { return layout; }
    public ContentDiscoveryService.Discovery discovery() { return discovery; }
}
