/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.ports

/** What the running app is and where it keeps its data, as Settings › About shows it. */
public data class AppEnvironment(
    /** The packaged version, or null for a development run (`gradlew run`). */
    val version: String?,
    val runtime: String,
    val operatingSystem: String,
    val dataDirectory: String,
    val logsDirectory: String,
)

public interface AppEnvironmentPort {
    public fun environment(): AppEnvironment

    /** Opens [path] in the system file manager; false when it cannot. */
    public fun openFolder(path: String): Boolean
}
