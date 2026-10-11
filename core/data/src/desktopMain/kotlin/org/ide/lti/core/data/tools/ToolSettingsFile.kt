/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.tools

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.ide.lti.core.data.tools.KotlinStoreLayout.shellQuote
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.tools.ChoiceUpdate
import org.ide.lti.core.domain.tools.ResolvedSource
import org.ide.lti.core.domain.tools.ToolError
import org.ide.lti.core.model.setup.ToolRef
import java.util.Base64

@Serializable
public data class ToolSettingsDto(val schema: Int = 1, val selections: Map<String, ToolSelectionEntryDto> = emptyMap())

@Serializable
public data class ToolSelectionEntryDto(val id: String, val source: ToolSourceDto)

@Serializable
public data class ToolSourceDto(
    val kind: String,
    val repo: String? = null,
    val ref: ToolRefDto? = null,
    val commit: String? = null,
    val submodules: Map<String, String> = emptyMap(),
    val version: String? = null,
    val url: String? = null,
    val sha256: String? = null,
    val member: String? = null,
)

@Serializable
public data class ToolRefDto(val type: String, val name: String)

public class ToolSettingsFile(
    private val runner: RemoteCommandRunner,
    rawBaseDir: String,
    private val json: Json =
        Json {
            ignoreUnknownKeys = false
            encodeDefaults = true
            prettyPrint = true
        },
) {
    private val baseDir: String = io.ltirom.tooling.client.WslPathTranslator().toWslPath(rawBaseDir)
    private val settingsPath: String = "$baseDir/settings.json"
    private val tmpPath: String = "$baseDir/settings.json.tmp"

    /** The picks that could be read, and the groups whose entry could not. */
    public data class Settings(
        val picks: Map<ToolGroupId, Pair<String, ResolvedSource>>,
        val unreadable: Set<ToolGroupId> = emptySet(),
    )

    private fun unreadableError(groups: Collection<ToolGroupId>) = ToolErrorException(
        ToolError.Internal(
            "$settingsPath can't be read: unusable pick for ${groups.map {
                it.value
            }.sorted().joinToString()}",
        ),
    )

    /** Every pick, or a failure if any entry is unusable: never "no pick" for something the user chose. */
    public suspend fun readSelections(): Result<Map<ToolGroupId, Pair<String, ResolvedSource>>> =
        readSettings().mapCatching { settings ->
            if (settings.unreadable.isNotEmpty()) throw unreadableError(settings.unreadable)
            settings.picks
        }

    /**
     * Like [readSelections], but a damaged entry only marks its own group (the inventory shows that group as
     * needing an explicit choice) instead of failing every group. An unreadable file is still a failure.
     */
    @Suppress("ReturnCount")
    public suspend fun readSettings(): Result<Settings> {
        val script = "if [ -f ${shellQuote(settingsPath)} ]; then cat ${shellQuote(settingsPath)}; else exit 44; fi"
        val res = runner.execute(script, CommandCategory.SHORT)
        if (res.isFailure) {
            val ex = res.exceptionOrNull()
            return Result.failure(ex ?: ToolErrorException(ToolError.Internal("$settingsPath can't be read")))
        }
        val out = res.getOrThrow()
        if (out.exitCode == 44) {
            // File does not exist yet -> empty selections
            return Result.success(Settings(emptyMap()))
        }
        if (out.exitCode != 0) {
            return Result.failure(ToolErrorException(ToolError.Internal("$settingsPath can't be read")))
        }

        return try {
            val dto = json.decodeFromString<ToolSettingsDto>(out.stdout)
            if (dto.schema != 1) {
                return Result.failure(
                    ToolErrorException(ToolError.Internal("$settingsPath can't be read: unknown schema ${dto.schema}")),
                )
            }
            val picks = mutableMapOf<ToolGroupId, Pair<String, ResolvedSource>>()
            val unreadable = mutableSetOf<ToolGroupId>()
            for ((groupStr, entry) in dto.selections) {
                val source = dtoToSource(entry.source)
                if (source ==
                    null
                ) {
                    unreadable += ToolGroupId(groupStr)
                } else {
                    picks[ToolGroupId(groupStr)] = entry.id to source
                }
            }
            Result.success(Settings(picks, unreadable))
        } catch (t: Throwable) {
            Result.failure(ToolErrorException(ToolError.Internal("$settingsPath can't be read: ${t.message}")))
        }
    }

    /**
     * A write replaces the whole file, so it may only drop the entry of the group being changed: choosing a
     * version for a group with a damaged entry repairs it, but another group's damaged entry blocks the write.
     */
    private suspend fun picksForWrite(
        group: ToolGroupId,
    ): Result<MutableMap<ToolGroupId, Pair<String, ResolvedSource>>> = readSettings().mapCatching { settings ->
        val others = settings.unreadable - group
        if (others.isNotEmpty()) throw unreadableError(others)
        settings.picks.toMutableMap()
    }

    public suspend fun record(group: ToolGroupId, versionId: String, source: ResolvedSource): Result<Unit> =
        writeLock.withLock {
            val picks = picksForWrite(group).getOrElse { return@withLock Result.failure(it) }
            picks[group] = versionId to source
            writeSelections(picks)
        }

    /** Applies [choice] to the saved picks. Record needs the version's [source]; without it nothing is written. */
    public suspend fun applyChoice(
        choice: ChoiceUpdate,
        group: ToolGroupId,
        versionId: String,
        source: ResolvedSource?,
    ): Result<Unit> = when (choice) {
        ChoiceUpdate.Keep -> Result.success(Unit)
        ChoiceUpdate.Clear -> clear(group)
        ChoiceUpdate.Record ->
            if (source == null) {
                Result.failure(
                    ToolErrorException(
                        ToolError.UnsupportedLayout(listOf("Missing or unreadable version.json for $versionId")),
                    ),
                )
            } else {
                record(group, versionId, source)
            }
    }

    /**
     * Puts [previous] back as the pick of [group] (none if null) after a change that did not happen. A failure is
     * returned, not dropped: it means the saved pick and the active link now disagree, and the caller must say so.
     */
    public suspend fun restore(group: ToolGroupId, previous: Pair<String, ResolvedSource>?): Result<Unit> =
        withContext(NonCancellable) {
            try {
                if (previous != null) record(group, previous.first, previous.second) else clear(group)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    public suspend fun clear(group: ToolGroupId): Result<Unit> = writeLock.withLock {
        val picks = picksForWrite(group).getOrElse { return@withLock Result.failure(it) }
        picks.remove(group)
        writeSelections(picks)
    }

    private suspend fun writeSelections(selections: Map<ToolGroupId, Pair<String, ResolvedSource>>): Result<Unit> {
        val dto =
            ToolSettingsDto(
                schema = 1,
                selections =
                selections.map { (grp, pair) ->
                    grp.value to
                        ToolSelectionEntryDto(
                            id = pair.first,
                            source = sourceToDto(pair.second),
                        )
                }.toMap(),
            )
        val jsonStr = json.encodeToString(dto)
        val b64 = Base64.getEncoder().encodeToString(jsonStr.toByteArray(Charsets.UTF_8))
        val script =
            """
            mkdir -p ${shellQuote(baseDir)} && \
            echo '$b64' | base64 -d > ${shellQuote(tmpPath)}${'$'}${'$'} && \
            mv ${shellQuote(tmpPath)}${'$'}${'$'} ${shellQuote(settingsPath)}
            """.trimIndent()
        val res = runner.execute(script, CommandCategory.SHORT)
        return if (res.isSuccess && res.getOrThrow().exitCode == 0) {
            Result.success(Unit)
        } else {
            Result.failure(ToolErrorException(ToolError.Internal("Failed to write $settingsPath")))
        }
    }

    private fun sourceToDto(source: ResolvedSource): ToolSourceDto = when (source) {
        is ResolvedSource.Git -> {
            val refDto =
                when (val ref = source.ref) {
                    is ToolRef.Tag -> ToolRefDto(type = "tag", name = ref.name)
                    is ToolRef.Branch -> ToolRefDto(type = "branch", name = ref.name)
                    is ToolRef.Commit -> ToolRefDto(type = "commit", name = ref.sha)
                    is ToolRef.ReleaseVersion -> ToolRefDto(type = "release", name = ref.version)
                }
            ToolSourceDto(
                kind = "git",
                repo = source.repoUrl,
                ref = refDto,
                commit = source.commit,
                submodules = source.submodules,
            )
        }
        is ResolvedSource.Release ->
            ToolSourceDto(
                kind = "release",
                version = source.version,
                url = source.url,
                sha256 = source.sha256,
                member = source.member,
            )
    }

    private fun dtoToSource(dto: ToolSourceDto): ResolvedSource? = when (dto.kind) {
        "git" -> {
            val repo = dto.repo ?: return null
            // Only an immutable id is a version: "main" in a saved pick would move under it. Unreadable, then.
            val commit = dto.commit?.takeIf { COMMIT_SHA.matches(it) } ?: return null
            if (!dto.submodules.values.all { COMMIT_SHA.matches(it) }) return null
            val ref =
                when (dto.ref?.type) {
                    "tag" -> ToolRef.Tag(dto.ref.name)
                    "branch" -> ToolRef.Branch(dto.ref.name)
                    "commit" -> ToolRef.Commit(dto.ref.name)
                    else -> ToolRef.Commit(commit)
                }
            ResolvedSource.Git(
                repoUrl = repo,
                ref = ref,
                commit = commit,
                submodules = dto.submodules,
            )
        }
        "release" -> {
            val ver = dto.version ?: return null
            val url = dto.url ?: return null
            val sha = dto.sha256 ?: return null
            val member = dto.member ?: return null
            ResolvedSource.Release(
                version = ver,
                url = url,
                sha256 = sha,
                member = member,
            )
        }
        else -> null
    }

    private companion object {
        /** record/clear read, change and rewrite the file; two groups finishing together lost one pick. */
        val writeLock = Mutex()
    }
}

private val COMMIT_SHA = Regex("[0-9a-f]{40}")
