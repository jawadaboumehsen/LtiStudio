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

import org.ide.lti.core.common.utils.ByteFormatter

/** A file size for people: `512 B`, `8.4 KB`, `31 MB`, `3.9 GB` (binary units, one decimal below 100). */
public fun formatBytes(bytes: Long): String = ByteFormatter.format(bytes)
