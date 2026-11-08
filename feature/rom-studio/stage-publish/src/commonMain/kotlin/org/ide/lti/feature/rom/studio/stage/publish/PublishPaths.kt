/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.publish

/** A local export's `/mnt/d/ROM` as the `D:\ROM` the person typed; a GitHub destination stays as it is. */
internal fun shownPath(destination: String): String =
    Regex("/mnt/([a-z])(/.*)?").matchEntire(destination)?.let { match ->
        "${match.groupValues[1].uppercase()}:" + match.groupValues[2].ifEmpty { "/" }.replace('/', '\\')
    } ?: destination

/** [text] with its `/mnt/...` paths as [shownPath] shows them. ponytail: stops at a space in a folder name. */
internal fun withShownPaths(text: String): String =
    text.replace(Regex("""/mnt/[a-z](/[^\s:]*)?""")) { shownPath(it.value) }
