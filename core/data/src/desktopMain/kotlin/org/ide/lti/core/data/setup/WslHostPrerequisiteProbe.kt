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

import io.ltirom.tooling.client.wsl.WslCliExecutor
import io.ltirom.tooling.client.wsl.WslEnvironmentDetector
import org.ide.lti.core.domain.ports.HostPrerequisitePort
import org.ide.lti.core.model.setup.DistroStatus
import org.ide.lti.core.model.setup.PrerequisiteReport
import org.ide.lti.core.model.setup.RequirementCheck
import org.ide.lti.core.model.setup.RequirementStatus
import org.ide.lti.core.model.setup.SetupEnvironment
import org.ide.lti.core.model.setup.SystemRequirement

/**
 * Checks [RequirementCatalog] over `wsl.exe` in one shell round trip, without the build service.
 *
 * The script prints one `<id>=present|missing` line per requirement, `PACKAGES_FRESH=1|0`, the Java
 * homes found under `/usr/lib/jvm`, and `APT_POLICY:<pkg>:<candidate>` for the packages of every
 * missing requirement. A requirement with no line (the script died, wsl.exe failed) is
 * [RequirementStatus.CheckFailed], never assumed present.
 */
public class WslHostPrerequisiteProbe(
    private val detector: WslEnvironmentDetector = WslEnvironmentDetector(),
    private val cli: WslCliExecutor = WslCliExecutor(),
    private val requirements: List<SystemRequirement> = RequirementCatalog.REQUIREMENTS,
) : HostPrerequisitePort {
    override suspend fun detect(): List<DistroStatus> = detector.detectAll()

    /**
     * Strictly requires the WSL default distro to be usable Ubuntu LTS.
     */
    override suspend fun selectEnvironment(statuses: List<DistroStatus>): SetupEnvironment? {
        val defaultDistro = detector.getDefaultDistro() ?: return null
        val usable = statuses.filterIsInstance<DistroStatus.Usable>()
        return usable.firstOrNull { it.environment.distro == defaultDistro }?.environment
    }

    override suspend fun probe(environment: SetupEnvironment): PrerequisiteReport {
        val result =
            cli.execute(
                environment.distro,
                listOf("sh", "-c", buildScript()),
                timeoutSeconds = PROBE_TIMEOUT_SECONDS,
            )
        val output = ProbeOutput(result.output.lines().map { it.trim() })
        val failureReason = result.error.ifBlank { "exit code ${result.exitCode}" }
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

    /** Parsed script output; see the class comment for the line format. */
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

        /** Null when the script never reported this requirement (it died, or wsl.exe failed). */
        fun isPresent(req: SystemRequirement): Boolean? = reported[req.id]

        /** Only fresh package lists can prove apt has no candidate. */
        fun isUnavailable(packages: List<String>): Boolean = packageListsFresh &&
            packages.any { pkg ->
                aptCandidates[pkg]?.let { it.isEmpty() || it == "none" || it == "(none)" } == true
            }
    }

    private fun buildScript(): String = buildString {
        appendLine(
            "if ls /var/lib/apt/lists/*_Packages >/dev/null 2>&1; " +
                "then echo PACKAGES_FRESH=1; else echo PACKAGES_FRESH=0; fi",
        )
        appendLine(
            "policy() { for p in \"\$@\"; do " +
                "c=\$(apt-cache policy \"\$p\" 2>/dev/null | sed -n 's/^ *Candidate: *//p'); " +
                "echo \"$APT_POLICY\$p:\${c:-none}\"; done; }",
        )
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
