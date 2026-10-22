/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.plugin

import org.ide.lti.core.model.plugin.AssetRef
import org.ide.lti.core.model.plugin.FileMetadata
import org.ide.lti.core.model.plugin.PartitionPath
import org.ide.lti.core.model.plugin.SmaliEdit

/**
 * Concrete mutation plan resolved from a plugin plan template.
 *
 * Preview and execution both consume this same ConcreteMutationPlan; there is no second resolution path.
 */
data class ConcreteMutationPlan(
    val publisher: String,
    val id: String,
    val version: String,
    val operations: List<ConcreteOperation>,
    val notApplicable: List<NotApplicable>,
    val digest: String,
)

data class NotApplicable(val operationId: String, val reason: String)

sealed interface ConcreteOperation {
    val id: String

    data class Copy(
        override val id: String,
        val source: AssetRef,
        val destination: PartitionPath,
        val metadata: FileMetadata? = null,
    ) : ConcreteOperation

    data class Replace(
        override val id: String,
        val source: AssetRef,
        val destination: PartitionPath,
        val expectedSha256: String,
    ) : ConcreteOperation

    data class Delete(override val id: String, val target: PartitionPath) : ConcreteOperation

    data class PropertyPatch(override val id: String, val target: PartitionPath, val key: String, val value: String) :
        ConcreteOperation

    data class TextPatch(
        override val id: String,
        val target: PartitionPath,
        val context: String,
        val replacement: String,
        val expectedSha256: String,
    ) : ConcreteOperation

    data class ApkJarPatch(override val id: String, val target: PartitionPath, val edits: List<SmaliEdit>) :
        ConcreteOperation
}
