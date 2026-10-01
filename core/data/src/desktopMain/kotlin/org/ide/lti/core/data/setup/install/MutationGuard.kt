/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup.install

/**
 * Runtime guard enforcing R29: No mutation path may bypass [ArtifactStore] or [InstallationManager]
 * when toolchainLayoutV2 is enabled.
 */
public object MutationGuard {

    private val MUTATION_COMMANDS = setOf(
        "cp", "mv", "mkdir", "rm", "rmdir", "touch", "tee", "ln", "install", "chmod", "chown",
    )

    private val TRUSTED_CALLERS = setOf("ArtifactStore", "InstallationManager", "SwitchToolchainHandler")

    public fun checkCommand(toolchainLayoutV2: Boolean, command: List<String>, caller: String? = null) {
        if (!toolchainLayoutV2 || command.isEmpty() || caller in TRUSTED_CALLERS) {
            return
        }

        val effectiveCommand = if (command.first().substringAfterLast('/') == "sudo") {
            command.dropWhile { it == "sudo" || it.startsWith("-") }
        } else {
            command
        }

        if (effectiveCommand.isNotEmpty() && effectiveCommand.first().substringAfterLast('/') in MUTATION_COMMANDS) {
            checkMutationTargets(effectiveCommand, effectiveCommand.first().substringAfterLast('/'))
        }
    }

    private fun checkMutationTargets(effectiveCommand: List<String>, effectiveCmd: String) {
        for (arg in effectiveCommand.drop(1)) {
            if (arg.startsWith("-")) continue
            val normalized = arg.replace('\\', '/')
            if (isProtectedTarget(normalized)) {
                throw SecurityException(
                    "MutationGuard violation: Direct mutation of '$arg' via '$effectiveCmd' " +
                        "is prohibited when toolchainLayoutV2 is enabled.",
                )
            }
        }
    }

    public fun isProtectedTarget(path: String): Boolean {
        val trimmed = path.trimEnd('/')
        return trimmed.contains("LtiRomTools/bin") ||
            trimmed.contains("/artifacts") ||
            trimmed.startsWith("artifacts") ||
            trimmed.contains("/installs") ||
            trimmed.startsWith("installs")
    }
}
