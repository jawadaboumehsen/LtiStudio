/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup

import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.remote.RunPurpose
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import org.ide.lti.core.model.setup.PrerequisiteReport
import org.ide.lti.core.model.setup.RequirementCheck
import org.ide.lti.core.model.setup.RequirementStatus
import org.ide.lti.core.model.setup.SetupEnvironment
import org.ide.lti.core.model.setup.SystemRequirement

/**
 * Checks system package prerequisites through the running build service [RemoteTransportPort],
 * ensuring all command execution runs on the remote server instead of local wsl.exe.
 */
public class RemotePrerequisiteProbe(
    private val transport: RemoteTransportPort,
    private val requirements: List<SystemRequirement> = RequirementCatalog.REQUIREMENTS,
) {
    public suspend fun probe(environment: SetupEnvironment): PrerequisiteReport {
        val script = buildScript()
        val execRes =
            transport.execute(
                ToolExecutionRequest(
                    toolId = "sh",
                    arguments = listOf("-c", script),
                    workingDirectory = environment.home,
                    timeoutMs = PROBE_TIMEOUT_SECONDS * 1_000L,
                    purpose = RunPurpose.SETUP,
                ),
            )

        if (execRes.exitCode != 0) {
            val failureReason = execRes.stderr.ifBlank { execRes.stdout.ifBlank { "exit code ${execRes.exitCode}" } }
            error("Remote prerequisite probe failed with exit code ${execRes.exitCode}: $failureReason")
        }

        val lines = execRes.stdout.lines().map { it.trim() }
        if (lines.none { it.startsWith("PACKAGES_FRESH=") }) {
            error("Remote probe output did not contain expected markers: ${execRes.stdout.ifBlank { execRes.stderr }}")
        }
        val output = ProbeOutput(lines)
        val failureReason = execRes.stderr.ifBlank { "exit code ${execRes.exitCode}" }

        val results =
            requirements.associateTo(linkedMapOf()) { req ->
                req.id to
                    when (output.isPresent(req)) {
                        null -> RequirementStatus.CheckFailed(failureReason)
                        true -> RequirementStatus.Present
                        false ->
                            if (output.isUnavailable(req.aptPackages)) {
                                RequirementStatus.Unavailable(release = environment.osVersionId)
                            } else {
                                RequirementStatus.Missing
                            }
                    }
            }

        val missingPackages =
            requirements.filter { results[it.id] == RequirementStatus.Missing }
                .flatMap { it.aptPackages }
                .distinct().sorted()

        return PrerequisiteReport(
            environment = environment,
            results = results,
            missingPackages = missingPackages,
            installCommand = missingPackages.takeIf { it.isNotEmpty() }?.let(AptCommandBuilder::install),
            javaHome = null,
            packageListsFresh = output.packageListsFresh,
            checkedAt = System.currentTimeMillis(),
        )
    }

    private class ProbeOutput(private val lines: List<String>) {
        val packageListsFresh = "PACKAGES_FRESH=1" in lines
        private val aptCandidates: Map<String, String> =
            lines
                .filter { it.startsWith(APT_POLICY) }
                .associate { line ->
                    val parts = line.removePrefix(APT_POLICY).split(":", limit = 2)
                    parts[0].trim() to parts.getOrElse(1) { "" }.trim()
                }

        private val reported: Map<String, Boolean> =
            lines.mapNotNull { line ->
                line.split("=", limit = 2)
                    .takeIf { it.size == 2 && (it[1] == "present" || it[1] == "missing") }
                    ?.let { it[0] to (it[1] == "present") }
            }.toMap()

        fun isPresent(req: SystemRequirement): Boolean? = reported[req.id]

        fun isUnavailable(packages: List<String>): Boolean = packageListsFresh &&
            packages.any { pkg ->
                aptCandidates[pkg]?.let { it.isEmpty() || it == "none" || it == "(none)" } == true
            }
    }

    private fun buildScript(): String = buildString {
        // apt may keep its package indexes compressed (.lz4, .gz, .xz); any of them means "lists exist". A loop, not
        // one `ls` over all the patterns: ls fails if any single pattern matches nothing.
        appendLine("fresh=0")
        appendLine(
            "for f in /var/lib/apt/lists/*_Packages /var/lib/apt/lists/*_Packages.lz4 " +
                "/var/lib/apt/lists/*_Packages.gz /var/lib/apt/lists/*_Packages.xz; do",
        )
        appendLine("  if [ -e \"\$f\" ]; then fresh=1; break; fi")
        appendLine("done")
        appendLine("echo PACKAGES_FRESH=\$fresh")
        appendLine("policy() {")
        appendLine("  for p in \"\$@\"; do")
        appendLine("    c=\$(apt-cache policy \"\$p\" 2>/dev/null | sed -n 's/^ *Candidate: *//p')")
        appendLine("    echo \"$APT_POLICY\$p:\${c:-none}\"")
        appendLine("  done")
        appendLine("}")
        for (req in requirements) {
            val test =
                when (val check = req.check) {
                    is RequirementCheck.Command -> "command -v ${check.name} >/dev/null 2>&1"
                    is RequirementCheck.Package ->
                        "dpkg-query -W -f='\${Status}' ${check.name} 2>/dev/null | grep -Eq ' ok installed$'"
                    is RequirementCheck.PythonImport ->
                        "python3 -c '${check.modules.joinToString(";") { "import $it" }}' >/dev/null 2>&1"
                }
            val packages = req.aptPackages.joinToString(" ")
            val missing = "echo '${req.id}=missing'; policy $packages"
            appendLine("if $test; then echo '${req.id}=present'; else $missing; fi")
        }
    }

    private companion object {
        const val PROBE_TIMEOUT_SECONDS = 90L
        const val APT_POLICY = "APT_POLICY:"
    }
}
