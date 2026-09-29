/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup.doctor

/**
 * Execution context provided to all system diagnostic inspectors.
 *
 * @property distro Target WSL distribution or OS environment identifier.
 * @property home User home directory in the target environment (e.g., "/home/lti").
 * @property binDir Tools binary directory (e.g., "$home/LtiRomTools/bin").
 * @property binariesPresent Indicates whether compiled project binaries already exist.
 * @property systemCommands Commands found in standard system PATH directories (/usr/bin, /bin, etc.).
 * @property projectBinaries Binaries found specifically in [binDir].
 * @property deviceNodes Device nodes found in /dev.
 * @property availableCommands Union of all discovered commands, retained for backward compatibility.
 */
public data class DiagnosticContext(
    val distro: String,
    val home: String,
    val binDir: String,
    val binariesPresent: Boolean,
    val systemCommands: Set<String> = emptySet(),
    val projectBinaries: Set<String> = emptySet(),
    val deviceNodes: Set<String> = emptySet(),
    val availableCommands: Set<String> = systemCommands,
)
