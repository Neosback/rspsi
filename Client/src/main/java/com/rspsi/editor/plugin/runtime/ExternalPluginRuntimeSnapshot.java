package com.rspsi.editor.plugin.runtime;

import com.rspsi.editor.plugin.EditorPlugin;
import com.rspsi.editor.plugin.PluginLoadFailure;

import java.util.ArrayList;
import java.util.List;

/** Owned snapshot of one external-plugin scan/load pass. */
public final class ExternalPluginRuntimeSnapshot implements AutoCloseable {
    private final List<ExternalPluginHandle> handles;
    private final List<PluginLoadFailure> failures;
    private boolean closed;

    ExternalPluginRuntimeSnapshot(List<ExternalPluginHandle> handles,
                                  List<PluginLoadFailure> failures) {
        this.handles = List.copyOf(handles);
        this.failures = List.copyOf(failures);
    }

    public List<ExternalPluginHandle> handles() { return handles; }
    public List<PluginLoadFailure> failures() { return failures; }

    public List<EditorPlugin> plugins() {
        return handles.stream().flatMap(handle -> handle.plugins().stream()).toList();
    }

    public List<ExternalPluginManifest> manifests() {
        return handles.stream().flatMap(handle -> handle.manifest().stream()).toList();
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        List<Throwable> errors = new ArrayList<>();
        for (int index = handles.size() - 1; index >= 0; index--) {
            try {
                handles.get(index).close();
            } catch (Throwable error) {
                errors.add(error);
            }
        }
        if (!errors.isEmpty()) {
            IllegalStateException failure =
                    new IllegalStateException("Unable to unload one or more external plugins");
            errors.forEach(failure::addSuppressed);
            throw failure;
        }
    }
}
