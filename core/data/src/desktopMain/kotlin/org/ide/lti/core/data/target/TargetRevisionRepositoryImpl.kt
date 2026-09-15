/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.target

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.ide.lti.core.domain.pipeline.configuration.Severity
import org.ide.lti.core.domain.pipeline.configuration.ValidationError
import org.ide.lti.core.domain.pipeline.configuration.ValidationReport
import org.ide.lti.core.domain.target.TargetRevisionDeleteOutcome
import org.ide.lti.core.domain.target.TargetRevisionRef
import org.ide.lti.core.domain.target.TargetRevisionRepository
import org.ide.lti.core.model.target.TargetDevice
import java.io.File
import java.net.URLEncoder

/**
 * Desktop file-system-backed implementation of [TargetRevisionRepository] persisting
 * target revisions as JSON documents in the platform's local application data directory.
 */
public class TargetRevisionRepositoryImpl(
    public val baseDir: File = resolveDefaultBaseDir(),
    private val activeBindingsProvider: (suspend (profileId: String, revision: Int) -> List<String>)? = null,
) : TargetRevisionRepository {

    init {
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
        prettyPrint = false
    }

    override suspend fun archive(target: TargetDevice): TargetRevisionRef = withContext(Dispatchers.IO) {
        val profileDir = File(baseDir, encodeProfileId(target.id))
        if (!profileDir.exists()) {
            profileDir.mkdirs()
        }
        val existingRevisions = listRevisions(target.id)
        val nextRevision = if (existingRevisions.isEmpty()) {
            target.revision.coerceAtLeast(1)
        } else {
            existingRevisions.maxOf { it.revision } + 1
        }
        val archivedTarget = target.copy(revision = nextRevision)
        val digest = TargetRevisionRef.computeDigest(archivedTarget)
        val ref = TargetRevisionRef(
            profileId = archivedTarget.id,
            revision = nextRevision,
            contentDigest = digest,
        )
        val file = File(profileDir, "$nextRevision.json")
        file.writeText(json.encodeToString(archivedTarget), Charsets.UTF_8)
        ref
    }

    override suspend fun get(profileId: String, revision: Int): TargetDevice? = withContext(Dispatchers.IO) {
        val file = File(File(baseDir, encodeProfileId(profileId)), "$revision.json")
        if (!file.exists() || !file.isFile) return@withContext null
        try {
            val target = json.decodeFromString<TargetDevice>(file.readText(Charsets.UTF_8))
            if (target.revision == revision && target.id == profileId) target else null
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun listRevisions(profileId: String): List<TargetRevisionRef> = withContext(Dispatchers.IO) {
        val profileDir = File(baseDir, encodeProfileId(profileId))
        if (!profileDir.exists() || !profileDir.isDirectory) return@withContext emptyList()
        val files = profileDir.listFiles { file ->
            file.isFile && file.extension == "json" && file.nameWithoutExtension.toIntOrNull() != null
        } ?: return@withContext emptyList()

        files.mapNotNull { file ->
            try {
                val target = json.decodeFromString<TargetDevice>(file.readText(Charsets.UTF_8))
                TargetRevisionRef(
                    profileId = target.id,
                    revision = target.revision,
                    contentDigest = TargetRevisionRef.computeDigest(target),
                )
            } catch (_: Exception) {
                null
            }
        }.sortedBy { it.revision }
    }

    override suspend fun delete(
        profileId: String,
        revision: Int,
        referencedBy: List<String>,
    ): TargetRevisionDeleteOutcome = withContext(Dispatchers.IO) {
        val effectiveReferenced = if (referencedBy.isNotEmpty()) {
            referencedBy
        } else {
            activeBindingsProvider?.invoke(profileId, revision).orEmpty()
        }

        if (effectiveReferenced.isNotEmpty()) {
            return@withContext TargetRevisionDeleteOutcome.Refused(
                ValidationReport(
                    errors = listOf(
                        ValidationError(
                            stageId = null,
                            objectId = "$profileId:$revision",
                            fieldPath = "targetBinding",
                            code = TargetRevisionRepository.REFERENCED_TARGET_REVISION,
                            severity = Severity.ERROR,
                            message = "Target profile '$profileId' revision $revision is referenced " +
                                "by workspace(s): ${effectiveReferenced.joinToString()}",
                        ),
                    ),
                ),
            )
        }

        val file = File(File(baseDir, encodeProfileId(profileId)), "$revision.json")
        if (!file.exists() || !file.isFile) {
            return@withContext TargetRevisionDeleteOutcome.NotFound(profileId, revision)
        }

        if (file.delete()) {
            TargetRevisionDeleteOutcome.Deleted(profileId, revision)
        } else {
            TargetRevisionDeleteOutcome.Refused(
                ValidationReport(
                    errors = listOf(
                        ValidationError(
                            stageId = null,
                            objectId = "$profileId:$revision",
                            fieldPath = "targetBinding",
                            code = "DELETE_FAILED",
                            severity = Severity.ERROR,
                            message = "Failed to delete target revision file for '$profileId' revision $revision",
                        ),
                    ),
                ),
            )
        }
    }

    public companion object {
        public const val REFERENCED_TARGET_REVISION: String = TargetRevisionRepository.REFERENCED_TARGET_REVISION

        private fun encodeProfileId(profileId: String): String = URLEncoder.encode(profileId, "UTF-8")

        public fun resolveDefaultBaseDir(): File {
            val localAppData = System.getenv("LOCALAPPDATA")
            return if (!localAppData.isNullOrBlank()) {
                File(localAppData, "LtiRomGui/target-revisions")
            } else {
                val userHome = System.getProperty("user.home") ?: "."
                val osName = System.getProperty("os.name").orEmpty().lowercase()
                when {
                    osName.contains("mac") -> File(userHome, "Library/Application Support/LtiRomGui/target-revisions")
                    else -> File(userHome, ".local/share/LtiRomGui/target-revisions")
                }
            }
        }
    }
}
