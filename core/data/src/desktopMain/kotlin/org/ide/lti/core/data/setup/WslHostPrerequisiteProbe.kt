package org.ide.lti.core.data.setup

import io.ltirom.tooling.client.wsl.WslCliExecutor
import io.ltirom.tooling.client.wsl.WslEnvironmentDetector
import org.ide.lti.core.domain.ports.HostPrerequisitePort
import org.ide.lti.core.model.setup.DistroStatus
import org.ide.lti.core.model.setup.PrerequisiteReport
import org.ide.lti.core.model.setup.SetupEnvironment

public class WslHostPrerequisiteProbe(
    private val detector: WslEnvironmentDetector = WslEnvironmentDetector(),
    private val cli: WslCliExecutor = WslCliExecutor()
) : HostPrerequisitePort {

    override suspend fun detect(): List<DistroStatus> {
        return detector.detectAll()
    }

    /**
     * Resolves the selected environment from detected distribution statuses:
     * - If exactly one distro is [DistroStatus.Usable], selects it.
     * - Else, if the WSL default distro is [DistroStatus.Usable], selects it.
     * - Else returns null, prompting user selection.
     */
    public fun selectEnvironment(statuses: List<DistroStatus>): SetupEnvironment? {
        val usable = statuses.filterIsInstance<DistroStatus.Usable>()
        if (usable.size == 1) {
            return usable.single().environment
        }
        val defaultDistro = detector.getDefaultDistro()
        if (defaultDistro != null) {
            val matchingDefault = usable.firstOrNull { it.environment.distro == defaultDistro }
            if (matchingDefault != null) {
                return matchingDefault.environment
            }
        }
        return null
    }

    override suspend fun probe(environment: SetupEnvironment): PrerequisiteReport {
        val freshCheck = cli.execute(environment.distro, listOf("test", "-e", "/var/lib/apt/lists/*_Packages"))
        val separateFreshCheck = freshCheck.exitCode == 0

        val script = buildString {
            appendLine("echo \"JAVA_HOMES_BEGIN\"")
            appendLine("for d in /usr/lib/jvm/*; do")
            appendLine("    if [ -d \"\$d\" ] && [ -x \"\$d/bin/java\" ]; then")
            appendLine("        echo \"\$d\"")
            appendLine("    fi")
            appendLine("done")
            appendLine("echo \"JAVA_HOMES_END\"")
            for (req in RequirementCatalog.REQUIREMENTS) {
                when (val check = req.check) {
                    is org.ide.lti.core.model.setup.RequirementCheck.Command -> {
                        appendLine("if command -v ${check.name} >/dev/null 2>&1; then echo '${req.id}=present'; else echo '${req.id}=missing'; fi")
                    }
                    is org.ide.lti.core.model.setup.RequirementCheck.Package -> {
                        appendLine("if dpkg-query -W -f='\${Status}' ${check.name} 2>/dev/null | grep -q 'install ok installed'; then echo '${req.id}=present'; else echo '${req.id}=missing'; fi")
                    }
                    is org.ide.lti.core.model.setup.RequirementCheck.PythonImport -> {
                        val imports = check.modules.joinToString(";") { "import $it" }
                        appendLine("if python3 -c '$imports' >/dev/null 2>&1; then echo '${req.id}=present'; else echo '${req.id}=missing'; fi")
                    }
                    is org.ide.lti.core.model.setup.RequirementCheck.JavaMajor -> {
                        // Evaluated from discovered Java homes
                    }
                }
            }
        }

        val result = cli.execute(environment.distro, listOf("sh", "-c", script))
        val output = result.output

        val packageListsFresh = if (output.lines().any { it.trim() == "PACKAGES_FRESH=1" }) {
            true
        } else if (output.lines().any { it.trim() == "PACKAGES_FRESH=0" }) {
            false
        } else {
            separateFreshCheck
        }

        val javaLines = mutableListOf<String>()
        var inJavaSection = false
        for (line in output.lines()) {
            val trimmed = line.trim()
            if (trimmed == "JAVA_HOMES_BEGIN") {
                inJavaSection = true
                continue
            }
            if (trimmed == "JAVA_HOMES_END") {
                inJavaSection = false
                continue
            }
            if (inJavaSection && trimmed.isNotBlank()) {
                javaLines.add(trimmed)
            }
        }

        val javaCandidates = javaLines.mapNotNull { path ->
            val major = Regex("""(?:java-|jdk-)(\d+)""").find(path)?.groupValues?.get(1)?.toIntOrNull()
                ?: Regex("""\b(\d+)\b""").find(path)?.groupValues?.get(1)?.toIntOrNull()
            if (major != null && major >= 21) {
                path to major
            } else {
                null
            }
        }
        val javaHome = javaCandidates.maxByOrNull { it.second }?.first

        val aptPolicies = mutableMapOf<String, String>()
        for (line in output.lines()) {
            if (line.startsWith("APT_POLICY:")) {
                val parts = line.split(":", limit = 3)
                if (parts.size >= 3) {
                    aptPolicies[parts[1].trim()] = parts[2].trim()
                }
            }
        }

        val rawResults = mutableMapOf<String, String>()
        for (line in output.lines()) {
            val trimmed = line.trim()
            if (trimmed.contains("=")) {
                val parts = trimmed.split("=", limit = 2)
                if (parts.size == 2 && (parts[1] == "present" || parts[1] == "missing")) {
                    rawResults[parts[0].trim()] = parts[1].trim()
                }
            }
        }

        val results = mutableMapOf<String, org.ide.lti.core.model.setup.RequirementStatus>()
        for ((id, status) in rawResults) {
            if (status == "present") {
                results[id] = org.ide.lti.core.model.setup.RequirementStatus.Present
            } else {
                val req = RequirementCatalog.REQUIREMENTS.firstOrNull { it.id == id }
                val pkgs = req?.aptPackages ?: listOf(id)
                val isUnavailable = packageListsFresh && pkgs.any { pkg ->
                    val policy = aptPolicies[pkg] ?: aptPolicies[id]
                    policy != null && (policy.equals("none", ignoreCase = true) || policy.contains("(none)"))
                }
                if (isUnavailable) {
                    results[id] = org.ide.lti.core.model.setup.RequirementStatus.Unavailable(release = environment.osVersionId)
                } else {
                    results[id] = org.ide.lti.core.model.setup.RequirementStatus.Missing
                }
            }
        }

        val missingPkgs = mutableListOf<String>()
        for ((id, status) in results) {
            if (status is org.ide.lti.core.model.setup.RequirementStatus.Missing) {
                val req = RequirementCatalog.REQUIREMENTS.firstOrNull { it.id == id }
                if (req != null) {
                    missingPkgs.addAll(req.aptPackages)
                } else {
                    missingPkgs.add(id)
                }
            }
        }
        val missingPackages = missingPkgs.distinct().sorted()
        val installCommand = if (missingPackages.isEmpty()) null else AptCommandBuilder.install(missingPackages)

        return PrerequisiteReport(
            environment = environment,
            results = results,
            missingPackages = missingPackages,
            installCommand = installCommand,
            javaHome = javaHome,
            packageListsFresh = packageListsFresh,
            checkedAt = System.currentTimeMillis()
        )
    }
}
