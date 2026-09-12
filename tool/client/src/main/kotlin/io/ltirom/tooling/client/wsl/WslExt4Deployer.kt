package io.ltirom.tooling.client.wsl

/**
 * Resolves the LtiRomServer daemon binary's path on WSL2's native ext4-backed filesystem
 * (as opposed to the slower Windows-mounted 9p path), deploying it there if not already present.
 *
 * Reconstruction note: no surviving caller or transcript pins the actual first-install
 * deployment mechanism (how the server binary gets copied/built into WSL). This
 * implementation honestly resolves and verifies an existing install; it does not invent
 * a copy/build step it has no evidence for — a missing binary fails with a clear,
 * actionable message instead of silently pretending to install one.
 */
public class WslExt4Deployer(
    private val cli: WslCliExecutor = WslCliExecutor(),
) {
    public fun ensureInstalled(distro: String, home: String, serverBinaryWslPath: String?): String {
        val candidatePaths = buildList {
            if (!serverBinaryWslPath.isNullOrBlank()) add(serverBinaryWslPath)
            add("$home/.ltirom/bin/ltirom-server")
        }

        for (path in candidatePaths) {
            val check = cli.execute(distro, listOf("test", "-x", path), timeoutSeconds = 5)
            if (check.exitCode == 0) {
                return path
            }
        }

        error(
            "LtiRomServer binary not found in WSL distro '$distro' at any of: " +
                "${candidatePaths.joinToString(", ")}. Build/deploy the server binary to " +
                "\"$home/.ltirom/bin/ltirom-server\" before starting the daemon."
        )
    }
}
