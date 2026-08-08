package org.pockettts.android.engine

import java.nio.file.Path

internal object ImportSecurity {
    fun isArchiveTargetInsideRoot(root: Path, target: Path): Boolean =
        target != root && target.startsWith(root)

    fun isBlockedDocumentPath(path: Path): Boolean =
        path.startsWith("/data") ||
            path.startsWith("/proc") ||
            path.startsWith("/sys") ||
            path.startsWith("/dev")
}
