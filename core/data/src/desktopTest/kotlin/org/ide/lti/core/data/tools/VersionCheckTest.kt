/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.tools

import org.ide.lti.core.domain.setup.ToolOutput
import org.ide.lti.core.domain.setup.ToolOutputKind
import org.ide.lti.core.domain.setup.ToolProbe
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The one version validator, run by a real shell against real files (needs `sh`, `timeout`, `mktemp`, `grep`). */
class VersionCheckTest {
    private lateinit var dir: File

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("version-check").toFile()
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun shAvailable() = runCatching {
        ProcessBuilder("sh", "-c", "timeout 1 true && mktemp -u >/dev/null").start().waitFor() == 0
    }.getOrDefault(false)

    private fun posix(file: File) = file.absolutePath.replace('\\', '/').replace(Regex("^([A-Za-z]):")) {
        "/" + it.groupValues[1].lowercase()
    }

    /** An executable shell script standing in for a tool: it prints [output] and exits with [exitCode]. */
    private fun tool(name: String, output: String, exitCode: Int) {
        val file = File(dir, name)
        file.writeText("#!/bin/sh\nprintf '%s\\n' '$output'\nexit $exitCode\n")
        file.setExecutable(true)
    }

    private fun check(outputs: List<ToolOutput>, support: List<String> = emptyList(), probe: Boolean = true): Int {
        val script = File.createTempFile("check", ".sh").apply {
            deleteOnExit()
            writeText(KotlinStoreLayout.versionCheck(posix(dir), outputs, support, probe) + "\n")
        }
        val process = ProcessBuilder("sh", script.absolutePath).redirectErrorStream(true).start()
        process.inputStream.readBytes()
        return process.waitFor()
    }

    private fun output(name: String, probe: ToolProbe) = ToolOutput(name, name, ToolOutputKind.NATIVE, probe)

    @Test
    fun `a stub that only exits with an accepted code is not the tool`() {
        org.junit.Assume.assumeTrue("needs a POSIX shell", shAvailable())
        tool("lpmake", output = "", exitCode = 1)
        val probe = ToolProbe(listOf("--help"), setOf(0, 1))

        assertEquals(1, check(listOf(output("lpmake", probe))), "silent exit 1 passes on the exit code alone")
    }

    @Test
    fun `a tool that identifies itself passes with any accepted exit code`() {
        org.junit.Assume.assumeTrue("needs a POSIX shell", shAvailable())
        tool("lpmake", output = "lpmake - command-line tool. Usage: lpmake [options]", exitCode = 1)

        assertEquals(0, check(listOf(output("lpmake", ToolProbe(listOf("--help"), setOf(0, 1))))))
    }

    @Test
    fun `an exit code the install did not accept fails even with the right words`() {
        org.junit.Assume.assumeTrue("needs a POSIX shell", shAvailable())
        tool("lpmake", output = "Usage: lpmake", exitCode = 139)

        assertEquals(1, check(listOf(output("lpmake", ToolProbe(listOf("--help"), setOf(0, 1))))))
    }

    @Test
    fun `a probe without arguments still runs the tool, bare`() {
        org.junit.Assume.assumeTrue("needs a POSIX shell", shAvailable())
        val bare = ToolProbe(emptyList(), setOf(1, 255))
        tool("simg2img", output = "Usage: simg2img <sparse_image_files> <raw_image>", exitCode = 1)
        assertEquals(0, check(listOf(output("simg2img", bare))))

        tool("simg2img", output = "", exitCode = 1)
        assertEquals(1, check(listOf(output("simg2img", bare))), "an empty file that exits 1 is not simg2img")
    }

    @Test
    fun `an explicit signature replaces the default words`() {
        org.junit.Assume.assumeTrue("needs a POSIX shell", shAvailable())
        tool("make_f2fs", output = "F2FS-tools: mkfs.f2fs Ver: 1.16.0", exitCode = 0)

        val probe = ToolProbe(listOf("-V"), signature = "f2fs")
        assertEquals(0, check(listOf(output("make_f2fs", probe))))
        val other = ToolProbe(listOf("-V"), signature = "[0-9]+[.][0-9]+ stable")
        assertEquals(1, check(listOf(output("make_f2fs", other))))
    }

    @Test
    fun `a missing support file fails the version, whatever the probes say`() {
        org.junit.Assume.assumeTrue("needs a POSIX shell", shAvailable())
        tool("mkbootimg", output = "usage: mkbootimg", exitCode = 0)
        val outputs = listOf(output("mkbootimg", ToolProbe(listOf("--help"))))

        assertEquals(1, check(outputs, support = listOf("gki/")), "gki/ is missing")

        File(dir, "gki").mkdirs()
        assertEquals(0, check(outputs, support = listOf("gki/")))
    }

    @Test
    fun `without probes only files are checked`() {
        org.junit.Assume.assumeTrue("needs a POSIX shell", shAvailable())
        tool("lpmake", output = "", exitCode = 1)

        assertEquals(0, check(listOf(output("lpmake", ToolProbe(listOf("--help")))), probe = false))
    }
}
