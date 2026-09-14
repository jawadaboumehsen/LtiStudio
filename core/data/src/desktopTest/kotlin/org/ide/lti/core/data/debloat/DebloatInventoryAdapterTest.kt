/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.debloat

import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DebloatInventoryAdapterTest {

    private lateinit var workTreeRoot: Path
    private val adapter = DebloatInventoryAdapter()

    @BeforeTest
    fun setUp() {
        workTreeRoot = Files.createTempDirectory("debloat-inventory-test")

        val systemRoot = workTreeRoot.resolve("system")
        val productRoot = workTreeRoot.resolve("product")
        val configsRoot = workTreeRoot.resolve("configs")

        Files.createDirectories(systemRoot.resolve("app/YouTube"))
        Files.createDirectories(systemRoot.resolve("priv-app/Settings"))
        Files.createDirectories(systemRoot.resolve("bin"))
        Files.createDirectories(productRoot.resolve("app/Maps"))
        Files.createDirectories(configsRoot)

        Files.write(systemRoot.resolve("app/YouTube/YouTube.apk"), ByteArray(1234) { 1 })
        Files.write(systemRoot.resolve("priv-app/Settings/Settings.apk"), ByteArray(5678) { 2 })
        Files.write(systemRoot.resolve("bin/toybox"), ByteArray(100) { 3 })
        Files.write(productRoot.resolve("app/Maps/Maps.apk"), ByteArray(4321) { 4 })

        Files.writeString(
            configsRoot.resolve("fs_config-system"),
            """
            system/app/YouTube/YouTube.apk 0 0 644
            system/bin/toybox 0 0 755
            """.trimIndent() + "\n",
        )
        Files.writeString(
            configsRoot.resolve("file_context-system"),
            """
            /system/app/YouTube/YouTube\.apk u:object_r:system_file:s0
            /system/bin/toybox u:object_r:toybox_exec:s0
            """.trimIndent() + "\n",
        )
    }

    @AfterTest
    fun tearDown() {
        deleteRecursively(workTreeRoot)
    }

    private fun deleteRecursively(path: Path) {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return
        Files.walkFileTree(
            path,
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    Files.deleteIfExists(file)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    Files.deleteIfExists(dir)
                    return FileVisitResult.CONTINUE
                }
            },
        )
    }

    @Test
    fun `small work tree yields candidates with sizes, derives app packageId, and skips symlinks`() {
        runBlocking {
            // Attempt to create a symlink to verify it is skipped
            val testProbe = workTreeRoot.resolve("symlink_probe")
            val testTarget = workTreeRoot.resolve("symlink_target")
            Files.writeString(testTarget, "target")
            val canSymlink = try {
                Files.createSymbolicLink(testProbe, testTarget)
                Files.deleteIfExists(testProbe)
                Files.deleteIfExists(testTarget)
                true
            } catch (_: Exception) {
                false
            }

            if (canSymlink) {
                val symlinkPath = workTreeRoot.resolve("system/app/SymlinkApp.apk")
                Files.createSymbolicLink(symlinkPath, workTreeRoot.resolve("system/bin/toybox"))
            }

            val partitionRoots = mapOf(
                "system" to "system",
                "product" to "product",
            )

            val candidates = adapter.inventory(workTreeRoot, partitionRoots)

            // Verify candidates count (YouTube, Settings, toybox in system; Maps in product)
            assertEquals(4, candidates.size)

            val yt = candidates.firstOrNull { it.relativePath == "app/YouTube/YouTube.apk" }
            assertNotNull(yt)
            assertEquals("system", yt.partition)
            assertEquals(1234L, yt.sizeBytes)
            assertEquals("YouTube", yt.packageId)

            val settings = candidates.firstOrNull { it.relativePath == "priv-app/Settings/Settings.apk" }
            assertNotNull(settings)
            assertEquals("system", settings.partition)
            assertEquals(5678L, settings.sizeBytes)
            assertEquals("Settings", settings.packageId)

            val toybox = candidates.firstOrNull { it.relativePath == "bin/toybox" }
            assertNotNull(toybox)
            assertEquals("system", toybox.partition)
            assertEquals(100L, toybox.sizeBytes)
            assertNull(toybox.packageId)

            val maps = candidates.firstOrNull { it.relativePath == "app/Maps/Maps.apk" }
            assertNotNull(maps)
            assertEquals("product", maps.partition)
            assertEquals(4321L, maps.sizeBytes)
            assertEquals("Maps", maps.packageId)

            // Verify symlinks are not in candidates
            assertFalse(candidates.any { it.relativePath.contains("SymlinkApp") })
        }
    }

    @Test
    fun `sidecarEntriesFor returns matching lines from fs_config and file_context`() {
        runBlocking {
            val ytLines = adapter.sidecarEntriesFor(workTreeRoot, "system", "app/YouTube/YouTube.apk")
            assertEquals(2, ytLines.size)
            assertTrue(ytLines.any { it.contains("system/app/YouTube/YouTube.apk 0 0 644") })
            assertTrue(ytLines.any { it.contains("/system/app/YouTube/YouTube\\.apk u:object_r:system_file:s0") })

            val toyboxLines = adapter.sidecarEntriesFor(workTreeRoot, "system", "bin/toybox")
            assertEquals(2, toyboxLines.size)

            val nonExistentLines = adapter.sidecarEntriesFor(workTreeRoot, "system", "bin/nonexistent")
            assertTrue(nonExistentLines.isEmpty())

            val nonExistentPartitionLines = adapter.sidecarEntriesFor(workTreeRoot, "vendor", "bin/toybox")
            assertTrue(nonExistentPartitionLines.isEmpty())
        }
    }
}
