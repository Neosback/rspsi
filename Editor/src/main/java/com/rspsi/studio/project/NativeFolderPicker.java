package com.rspsi.studio.project;

import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Path;
import java.util.Optional;

/** Small native folder-picker boundary for the GLFW launcher. */
public final class NativeFolderPicker {
    private NativeFolderPicker() {
    }

    public static Optional<Path> choose(String title, Path initialDirectory) {
        String initial = initialDirectory == null ? null
                : initialDirectory.toAbsolutePath().normalize().toString();
        String selected = TinyFileDialogs.tinyfd_selectFolderDialog(title, initial);
        if (selected == null || selected.isBlank()) return Optional.empty();
        try {
            return Optional.of(Path.of(selected).toAbsolutePath().normalize());
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }
}
