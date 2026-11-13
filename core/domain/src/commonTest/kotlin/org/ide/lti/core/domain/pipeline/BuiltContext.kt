/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline

import org.ide.lti.core.domain.pipeline.stages.ReusedImage

/** [ctx] with every partition's fingerprint read as changed (so it is built), unless [ctx] already has one. */
internal fun built(ctx: StageContext): StageContext = ctx.copy(
    runtimeValues = ctx.target.dynamicPartitions.associate { ReusedImage.key(it) to "changed fp" } + ctx.runtimeValues,
)
