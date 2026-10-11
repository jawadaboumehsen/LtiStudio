package org.ide.lti.desktop.realhost

import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Real-host integration test verifying tool store initialization, legacy layout cleanup,
 * and preservation of new-layout directories (T031).
 */
class ToolStoreInitRealTest {
    @Test
    fun initCreatesDirectoriesDeletesFlatBinAndBinOldAndPreservesNewLayout() = runTest {
        if (!RealToolHost.isAvailable()) return@runTest

        RealToolHost.create().use { host ->
            val toolsBase = File(host.tempHome, "LtiRomTools")
            val binDir = File(toolsBase, "bin")
            val oldBin1 = File(toolsBase, "bin.old-1")
            val oldBin2 = File(toolsBase, "bin.old-2")

            // Setup legacy flat bin and bin.old-*
            binDir.mkdirs()
            oldBin1.mkdirs()
            oldBin2.mkdirs()
            File(binDir, "adb").writeText("flat binary")
            File(oldBin1, "old-file").writeText("old")

            // First init
            val res1 = host.store.init()
            assertTrue(res1.isSuccess, "First init must succeed")
            val info1 = res1.getOrThrow()

            // Verify directories exist
            assertTrue(File(toolsBase, "src").exists(), "src must exist")
            assertTrue(File(toolsBase, "work").exists(), "work must exist")
            assertTrue(File(toolsBase, "results").exists(), "results must exist")
            assertTrue(binDir.exists(), "bin must exist")

            // Flat file must be deleted, bin must be recreated empty
            assertFalse(File(binDir, "adb").exists(), "Flat bin/adb must be deleted")
            // bin.old-* folders must be deleted
            assertFalse(oldBin1.exists(), "bin.old-1 must be deleted")
            assertFalse(oldBin2.exists(), "bin.old-2 must be deleted")

            // Check deletedPaths
            assertTrue(info1.deletedPaths.any { it.contains("bin.old-1") }, "bin.old-1 must be in deletedPaths")
            assertTrue(info1.deletedPaths.any { it.contains("bin.old-2") }, "bin.old-2 must be in deletedPaths")
            assertTrue(info1.deletedPaths.any { it.endsWith("adb") }, "adb must be in deletedPaths")
            assertFalse(
                info1.deletedPaths.any { it.endsWith("/bin") || it.endsWith("\\bin") },
                "Entire bin must not be deleted",
            )

            // Create a new-layout folder bin/<tool>/v1.8.1
            val toolDir = File(binDir, "erofs-utils")
            val verDir = File(toolDir, "v1.8.1")
            verDir.mkdirs()
            File(verDir, "mkfs.erofs").writeText("dummy binary")

            // Second init
            val res2 = host.store.init()
            assertTrue(res2.isSuccess, "Second init must succeed")
            val info2 = res2.getOrThrow()

            // Second init deletes nothing
            assertTrue(info2.deletedPaths.isEmpty(), "Second init must delete nothing: ${info2.deletedPaths}")

            // New-layout folder is preserved
            assertTrue(File(verDir, "mkfs.erofs").exists(), "New-layout tool folder must never be deleted")
        }
    }

    @Test
    fun initPreservesInstalledToolVersionsWhenNonToolFileExistsInBin() = runTest {
        if (!RealToolHost.isAvailable()) return@runTest

        RealToolHost.create().use { host ->
            val toolsBase = File(host.tempHome, "LtiRomTools")
            val binDir = File(toolsBase, "bin")
            binDir.mkdirs()

            // Pre-existing valid installed tool
            val verDir = File(File(binDir, "erofs-utils"), "v1.8.1").apply { mkdirs() }
            val binary = File(verDir, "mkfs.erofs").apply { writeText("healthy binary") }

            // A non-tool regular file (e.g. README.txt) in bin
            val readme = File(binDir, "README.txt").apply { writeText("do not delete me") }

            // A legacy flat tool file in bin
            val legacyAdb = File(binDir, "adb").apply { writeText("legacy flat adb") }

            val res = host.store.init()
            assertTrue(res.isSuccess, "Init must succeed")
            val info = res.getOrThrow()

            // Non-tool file must NOT be deleted
            assertTrue(readme.exists(), "README.txt must be preserved")

            // Healthy installed version must NOT be deleted
            assertTrue(binary.exists(), "Healthy installed version must be preserved")

            // Legacy adb MUST be deleted
            assertFalse(legacyAdb.exists(), "Legacy flat adb must be deleted")

            // deletedPaths must record the legacy file, not the entire bin folder
            assertTrue(info.deletedPaths.any { it.endsWith("adb") }, "Deleted paths must include legacy adb")
            assertFalse(
                info.deletedPaths.any { it.endsWith("/bin") || it.endsWith("\\bin") },
                "Entire bin directory must not be deleted",
            )
        }
    }
}
