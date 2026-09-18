package com.rspsi.ui.workspace;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rspsi.core.misc.JsonUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Atomic JSON layout storage under the user's RSPSi configuration directory. */
public final class JsonWorkspaceLayoutStore implements WorkspaceLayoutStore {
    private final Path file;
    private final ObjectMapper mapper;
    private final Map<String, WorkspaceLayout> layouts = new LinkedHashMap<>();

    public JsonWorkspaceLayoutStore() {
        this(Path.of(System.getProperty("user.home"), ".rspsi", "ui", "layouts.json"));
    }

    public JsonWorkspaceLayoutStore(Path file) {
        this.file = Objects.requireNonNull(file, "file");
        this.mapper = JsonUtil.getDefaultMapper();
        read();
    }

    @Override
    public WorkspaceLayout load(String workspaceId) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        WorkspaceLayout layout = layouts.get(workspaceId);
        if (layout == null || layout.version() != WorkspaceLayout.CURRENT_VERSION) {
            return null;
        }
        return layout;
    }

    @Override
    public void save(WorkspaceLayout layout) {
        Objects.requireNonNull(layout, "layout");
        layouts.put(layout.workspaceId(), layout);
        write();
    }

    @Override
    public void reset(String workspaceId) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        layouts.remove(workspaceId);
        write();
    }

    private void read() {
        if (!Files.isRegularFile(file)) return;
        try {
            Map<String, WorkspaceLayout> loaded = mapper.readValue(file.toFile(),
                    new TypeReference<Map<String, WorkspaceLayout>>() { });
            if (loaded != null) layouts.putAll(loaded);
        } catch (IOException | RuntimeException ignored) {
            // A corrupt UI preference must never prevent the editor from opening.
            layouts.clear();
        }
    }

    private void write() {
        try {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), layouts);
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | UnsupportedOperationException atomicFailure) {
            try {
                Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
                mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), layouts);
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // UI preferences are best-effort and must not break editing.
            }
        }
    }
}
