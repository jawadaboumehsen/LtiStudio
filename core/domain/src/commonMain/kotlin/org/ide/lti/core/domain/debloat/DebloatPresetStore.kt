/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.debloat

import org.ide.lti.core.domain.pipeline.toolkit.RemovalList

/**
 * Named debloat picks the app keeps (not a workspace), so the same apps can be picked again on another firmware or
 * workspace. Entries are package names where known, else `partition:path` (`RemovalListMatcher` takes both).
 */
public interface DebloatPresetStore {
    public suspend fun load(): List<RemovalList>

    public suspend fun save(presets: List<RemovalList>)
}
