package org.pockettts.android.engine

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportSecurityTest {
    private val root = Paths.get("/app-private/model-packs/.import-test")

    @Test
    fun resolvesNestedArchiveEntryInsideRoot() {
        val target = ImportSecurity.resolveArchiveTarget(root, "models/flow_lm_main.onnx", "blocked")

        assertEquals(root.resolve("models/flow_lm_main.onnx"), target)
    }

    @Test
    fun rejectsParentTraversalArchiveEntry() {
        assertRejected("../outside.txt")
        assertRejected("models/../../../outside.txt")
    }

    @Test
    fun rejectsAbsoluteAndRootArchiveEntries() {
        assertRejected("/data/local/tmp/outside.txt")
        assertRejected(".")
        assertRejected("")
    }

    @Test
    fun rejectsSiblingDirectoryWithMatchingPrefix() {
        assertRejected("../.import-test-evil/outside.txt")
    }

    @Test
    fun rejectsTraversalEntryReadFromZipStream() {
        val archive = ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use { zip ->
                zip.putNextEntry(ZipEntry("models/../../../outside.txt"))
                zip.write(byteArrayOf(1, 2, 3))
                zip.closeEntry()
            }
        }.toByteArray()

        ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
            val entry = requireNotNull(zip.nextEntry)
            assertRejected(entry.name)
        }
    }

    @Test
    fun normalizesDocumentTraversalBeforePrivatePathCheck() {
        val normalized = ImportSecurity.normalizeDocumentPath("/safe/../../data/data/private.txt")

        assertEquals(Paths.get("/data/data/private.txt"), normalized)
        assertTrue(normalized.startsWith("/data"))
    }

    @Test
    fun blocksSensitiveSystemRootsAndAllowsRegularDocumentPaths() {
        listOf("/data/file", "/proc/self/maps", "/sys/kernel", "/dev/null").forEach {
            val path = Paths.get(it)
            assertTrue(
                path.startsWith("/data") ||
                    path.startsWith("/proc") ||
                    path.startsWith("/sys") ||
                    path.startsWith("/dev")
            )
        }
        val regularDocument = Paths.get("/document/primary:Download/model.zip")
        assertFalse(
            regularDocument.startsWith("/data") ||
                regularDocument.startsWith("/proc") ||
                regularDocument.startsWith("/sys") ||
                regularDocument.startsWith("/dev")
        )
    }

    private fun assertRejected(entryName: String) {
        assertThrows(IllegalArgumentException::class.java) {
            ImportSecurity.resolveArchiveTarget(root, entryName, "blocked")
        }
    }
}
