package org.pockettts.android.engine

import java.nio.file.FileSystems
import java.nio.file.Path

internal object ImportSecurity {
    fun isSafeArchiveEntryName(entryName: String): Boolean = runCatching {
        val entryPath = FileSystems.getDefault().getPath(entryName)
        val normalized = entryPath.normalize()
        entryName.isNotBlank() &&
            normalized.toString().isNotBlank() &&
            !entryPath.isAbsolute &&
            !normalized.startsWith("..")
    }.getOrDefault(false)

    fun isBlockedDocumentPath(path: Path): Boolean =
        path.startsWith("/data") ||
            path.startsWith("/proc") ||
            path.startsWith("/sys") ||
            path.startsWith("/dev")

}
