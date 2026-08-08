package org.pockettts.android.engine

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportSecurityTest {
    private val root = Paths.get("/app-private/model-packs/.import-test")

    @Test
    fun resolvesNestedArchiveEntryInsideRoot() {
        val entryName = "models/flow_lm_main.onnx"
        val target = root.resolve(entryName).normalize()

        assertEquals(root.resolve("models/flow_lm_main.onnx"), target)
        assertTrue(ImportSecurity.isArchiveTargetInsideRoot(root, target))
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
        val normalized = Paths.get("/safe/../../data/data/private.txt").normalize()

        assertEquals(Paths.get("/data/data/private.txt"), normalized)
        assertTrue(ImportSecurity.isBlockedDocumentPath(normalized))
    }

    @Test
    fun blocksSensitiveSystemRootsAndAllowsRegularDocumentPaths() {
        listOf("/data/file", "/proc/self/maps", "/sys/kernel", "/dev/null").forEach {
            assertTrue(ImportSecurity.isBlockedDocumentPath(Paths.get(it)))
        }
        assertFalse(ImportSecurity.isBlockedDocumentPath(Paths.get("/document/primary:Download/model.zip")))
    }

    private fun assertRejected(entryName: String) {
        val target = root.resolve(entryName).normalize()
        assertFalse(ImportSecurity.isArchiveTargetInsideRoot(root, target))
    }
}
