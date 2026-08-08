package org.pockettts.android.engine

import java.nio.file.FileSystems
import java.nio.file.Path

internal object ImportSecurity {
    fun resolveArchiveTarget(root: Path, entryName: String, errorMessage: String): Path {
        val normalizedRoot = root.toAbsolutePath().normalize()
        val target = normalizedRoot.resolve(entryName).normalize()
        require(target != normalizedRoot && target.startsWith(normalizedRoot)) { errorMessage }
        return target
    }

    fun normalizeDocumentPath(rawPath: String): Path =
        FileSystems.getDefault().getPath(rawPath).normalize()

}
