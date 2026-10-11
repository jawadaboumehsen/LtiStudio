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

import org.ide.lti.core.domain.setup.ToolGroupCatalog
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Opt-in (`LTI_LIVE_WSL=1`): two groups switch at the same moment, each under its own lock, into the same
 * `tools.d`. The generated scripts really run in the distro; neither may remove the other's staged manifests.
 */
class LiveSwitchConcurrencyTest {
    private val distro = System.getenv("LTI_TEST_DISTRO") ?: "Ubuntu"

    private fun mountPath(file: File): String =
        file.absolutePath.replace(File.separatorChar, '/').replace(Regex("^([A-Za-z]):")) {
            "/mnt/" + it.groupValues[1].lowercase()
        }

    private fun wsl(script: String): Pair<Int, String> {
        val file = File.createTempFile("switch", ".sh").apply {
            deleteOnExit()
            writeText(script.replace("\r", "") + "\n")
        }
        val process = ProcessBuilder("wsl.exe", "-d", distro, "--", "sh", mountPath(file))
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.readBytes().toString(Charsets.UTF_8)
        check(process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "timed out" }
        return process.exitValue() to output.replace("\u0000", "")
    }

    @Test
    fun `groups switching at the same moment do not remove each other's staged manifests`() {
        org.junit.Assume.assumeTrue("set LTI_LIVE_WSL=1", System.getenv("LTI_LIVE_WSL") == "1")
        val groups = ToolGroupCatalog.allGroups.filter { it.id.value in setOf("android-tools", "erofs-utils") }
        val base = "/tmp/ltirom-switch-race"
        val scripts = groups.map { group ->
            val dir = "$base/bin/${group.id.value}"
            KotlinStoreLayout.buildSwitchScript(
                qTargetVerDir = "'$dir/v1'",
                groupDir = dir,
                qVersionId = "'v1'",
                checkOuts = "true",
                groupDef = group,
                toolsDDir = "'$base/tools.d'",
            )
        }
        val setup = groups.joinToString("\n") { "mkdir -p $base/bin/${it.id.value}/v1" }
        val expected = groups.flatMap { g -> g.outputs.map { "${it.toolId}.json" } }
        val rounds = ROUNDS
        val driver = buildString {
            appendLine("set +e; rm -rf $base; $setup; mkdir -p $base/tools.d $base/home; export HOME=$base/home")
            appendLine("cat > $base/a.sh <<'EOS'\n${scripts[0]}\nEOS")
            appendLine("cat > $base/b.sh <<'EOS'\n${scripts[1]}\nEOS")
            appendLine("bad=0; i=0")
            appendLine("while [ \$i -lt $rounds ]; do")
            appendLine("  rm -f $base/tools.d/*; sh $base/a.sh & pa=\$!; sh $base/b.sh & pb=\$!")
            appendLine("  wait \$pa; ra=\$?; wait \$pb; rb=\$?")
            appendLine("  if [ \$ra -ne 0 ] || [ \$rb -ne 0 ]; then bad=\$((bad+1)); fi")
            appendLine("  i=\$((i+1))")
            appendLine("done")
            appendLine("echo failed_rounds=\$bad; ls $base/tools.d | sort | tr '\\n' ' '")
        }

        val (code, output) = wsl(driver)

        assertEquals(0, code, output)
        assertTrue("failed_rounds=0" in output, "a switch lost its staged manifests to another group: $output")
        expected.forEach { assertTrue(it in output, "$it missing after both switched: $output") }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 300L
        const val ROUNDS = 60
    }
}
