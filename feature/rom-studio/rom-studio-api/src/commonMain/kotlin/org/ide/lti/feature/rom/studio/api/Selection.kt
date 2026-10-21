/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.api

/**
 * Ticks or unticks [name] in a selection of [options] where an empty [selected] means "all of them". All ticked is
 * stored as empty again; the last one cannot be unticked.
 */
public fun toggledSelection(options: List<String>, selected: List<String>, name: String): List<String> {
    val effective = selected.ifEmpty { options }
    val next = if (name in effective) effective - name else options.filter { it in effective || it == name }
    return when {
        next.isEmpty() -> effective
        next.size == options.size -> emptyList()
        else -> next
    }
}
