package com.rspsi.studio;

import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Path;
import java.util.Optional;

/** Small cross-platform native chooser facade used by the pre-project launcher. */
public final class NativeFileDialogs {
    private NativeFileDialogs() {
    }

    public static Optional<Path> chooseDirectory(String title, Path initialDirectory) {
        String initial = initialDirectory == null ? null
                : initialDirectory.toAbsolutePath().normalize().toString();
        String selected = TinyFileDialogs.tinyfd_selectFolderDialog(title, initial);
        if (selected == null || selected.isBlank()) return Optional.empty();
        return Optional.of(Path.of(selected).toAbsolutePath().normalize());
    }
}
