package com.rspsi.studio

import org.lwjgl.util.tinyfd.TinyFileDialogs
import java.nio.file.Path
import java.util.Optional

/** Small cross-platform native chooser facade used by the pre-project launcher. */
object NativeFileDialogs {
    @JvmStatic
    fun chooseDirectory(
        title: String?,
        initialDirectory: Path?,
    ): Optional<Path> {
        val initial = initialDirectory?.normalizedAbsolutePath()?.toString()
        val selected = TinyFileDialogs.tinyfd_selectFolderDialog(title, initial)

        return selected
            ?.takeIf { it.isNotBlank() }
            ?.let { Optional.of(Path.of(it).normalizedAbsolutePath()) }
            ?: Optional.empty()
    }

    private fun Path.normalizedAbsolutePath(): Path = toAbsolutePath().normalize()
}
