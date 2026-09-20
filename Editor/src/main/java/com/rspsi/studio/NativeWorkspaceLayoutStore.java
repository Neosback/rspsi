package com.rspsi.studio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rspsi.core.misc.JsonUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Native-only adapter for the versioned ImGui workspace layout payload. */
final class NativeWorkspaceLayoutStore {
    // Bump whenever the dock contract changes so an old movable shell cannot
    // reintroduce unlocked rails or a titled viewport.
    static final int CURRENT_VERSION = 7;
    private final Path file;
    private final ObjectMapper mapper = JsonUtil.getDefaultMapper();

    NativeWorkspaceLayoutStore() {
        this(Path.of(System.getProperty("user.home"), ".rspsi", "ui", "native-map-layout.json"));
    }

    NativeWorkspaceLayoutStore(Path file) {
        this.file = file;
    }

    State load() {
        if (!Files.isRegularFile(file)) return null;
        try {
            State state = mapper.readValue(file.toFile(), State.class);
            return state == null || state.version() != CURRENT_VERSION ? null : state;
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    void save(State state) {
        try {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), state);
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException | UnsupportedOperationException atomicFailure) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            // UI preferences are best effort and never block editing.
        }
    }

    void reset() {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // Reset is best effort, matching the existing UI preference policy.
        }
    }

    record State(int version, String nativeIni, boolean bottomDrawerVisible) {
        State {
            nativeIni = nativeIni == null ? "" : nativeIni;
        }
    }
}
