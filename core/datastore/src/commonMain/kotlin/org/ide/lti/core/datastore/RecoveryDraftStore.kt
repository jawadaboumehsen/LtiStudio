/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.datastore

import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.ide.lti.core.model.target.DefaultTargetCatalog
import org.ide.lti.core.model.workspace.AcquisitionSettings
import org.ide.lti.core.model.workspace.AssemblySettings
import org.ide.lti.core.model.workspace.BuildSettings
import org.ide.lti.core.model.workspace.CustomizationSettings
import org.ide.lti.core.model.workspace.DebloatSettings
import org.ide.lti.core.model.workspace.ExtractionSettings
import org.ide.lti.core.model.workspace.PublishSettings
import org.ide.lti.core.model.workspace.ReleaseSettings
import java.security.MessageDigest
public typealias RecoveryDraftRecord = org.ide.lti.core.model.draft.RecoveryDraftRecord
public typealias RecoveryDraftReadResult = org.ide.lti.core.model.draft.RecoveryDraftReadResult
public typealias RecoveryDraftStorePort = org.ide.lti.core.domain.ports.RecoveryDraftStorePort

/** Settings-backed, checksummed recovery records. Generation checks are enforced here as the final race barrier. */
public class RecoveryDraftStore(
    private val settings: Settings = Settings(),
    private val json: Json = defaultJson(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : RecoveryDraftStorePort {
    private val mutex = Mutex()
    private val loaded = readRecordsSafe()
    private val _records = MutableStateFlow(loaded.records)
    private val tombstonesState = MutableStateFlow(loaded.tombstones)
    public val records = _records.asStateFlow()

    override suspend fun persist(record: RecoveryDraftRecord): Result<RecoveryDraftRecord> = withContext(ioDispatcher) {
        mutex.withLock {
            val current = _records.value[record.workspaceId]
            val tombstoneGeneration = tombstonesState.value[record.workspaceId] ?: current?.discardGeneration ?: -1L
            if (isStaleWrite(record, current, tombstoneGeneration)) {
                return@withLock Result.failure(
                    IllegalStateException("Recovery write belongs to an older discard generation"),
                )
            }
            val stamped = record.copy(lastPersistedAt = Clock.System.now())
            val saved = stamped.copy(checksum = checksum(stamped))
            writeRecords(_records.value + (record.workspaceId to saved)).map { saved }
        }
    }

    private fun isStaleWrite(
        record: RecoveryDraftRecord,
        current: RecoveryDraftRecord?,
        tombstoneGeneration: Long,
    ): Boolean {
        val belowTombstone = record.discardGeneration < tombstoneGeneration
        val reTombstoning = current != null &&
            current.tombstoned && record.discardGeneration <= current.discardGeneration
        val staleRevision = current != null &&
            current.discardGeneration == record.discardGeneration && current.draftRevision > record.draftRevision
        return belowTombstone || reTombstoning || staleRevision
    }

    override suspend fun get(
        workspaceId: String,
        expectedBaseSnapshotId: String?,
        expectedBaseSnapshotDigest: String?,
    ): RecoveryDraftReadResult? = withContext(ioDispatcher) {
        mutex.withLock {
            val record = _records.value[workspaceId]
            if (record == null) {
                return@withLock if (loaded.wasCorrupt) RecoveryDraftReadResult(null, wasCorrupt = true) else null
            }
            if (record.tombstoned) return@withLock null
            val baseDiverged = expectedBaseSnapshotId != null && (
                record.baseSnapshotId != expectedBaseSnapshotId ||
                    record.baseSnapshotDigest != expectedBaseSnapshotDigest
                )
            RecoveryDraftReadResult(record = record, baseDiverged = baseDiverged, wasCorrupt = loaded.wasCorrupt)
        }
    }

    override suspend fun advanceDiscardGeneration(workspaceId: String, generation: Long): Result<Unit> =
        withContext(ioDispatcher) {
            mutex.withLock {
                val current = _records.value[workspaceId]
                if (current != null && generation <= current.discardGeneration) {
                    return@withLock Result.failure(IllegalStateException("Discard generation must advance"))
                }
                val tombstone = (current ?: emptyRecord(workspaceId, generation)).copy(
                    discardGeneration = generation,
                    tombstoned = true,
                )
                writeRecords(
                    _records.value + (workspaceId to tombstone.copy(checksum = checksum(tombstone))),
                    tombstonesState.value + (workspaceId to generation),
                )
            }
        }

    override suspend fun delete(workspaceId: String): Result<Unit> = withContext(ioDispatcher) {
        mutex.withLock {
            val current = _records.value[workspaceId]
            if (current == null) return@withLock Result.success(Unit)
            writeRecords(_records.value - workspaceId, tombstonesState.value)
        }
    }

    override suspend fun retireIfRevision(workspaceId: String, savedRevision: Long): Result<Boolean> =
        withContext(ioDispatcher) {
            mutex.withLock {
                val current = _records.value[workspaceId]
                if (current == null || current.tombstoned || current.draftRevision != savedRevision) {
                    return@withLock Result.success(false)
                }
                writeRecords(_records.value - workspaceId).map { true }
            }
        }

    private fun writeRecords(
        records: Map<String, RecoveryDraftRecord>,
        tombstones: Map<String, Long> = tombstonesState.value,
    ): Result<Unit> = try {
        val encoded = json.encodeToString(RecoveryDraftMap(records = records, tombstones = tombstones))
        settings.putString(KEY_RECORDS, encoded)
        settings.putString(KEY_BACKUP, encoded)
        _records.value = records
        tombstonesState.value = tombstones
        Result.success(Unit)
    } catch (exception: Exception) {
        co.touchlab.kermit.Logger.w(exception) { "Failed to persist recovery drafts." }
        Result.failure(exception)
    }

    private fun readRecordsSafe(): LoadedRecords {
        val primary = decodeRecords(settings.getStringOrNull(KEY_RECORDS))
        val backup = primary ?: decodeRecords(settings.getStringOrNull(KEY_BACKUP))?.copy(wasCorrupt = true)
        if (backup != null) return backup
        val present = !settings.getStringOrNull(KEY_RECORDS).isNullOrBlank()
        if (present) co.touchlab.kermit.Logger.w { "Failed to decode persisted recovery drafts. Discarding." }
        return LoadedRecords(emptyMap(), emptyMap(), wasCorrupt = present)
    }

    private fun decodeRecords(raw: String?): LoadedRecords? = try {
        if (raw.isNullOrBlank()) return null
        val wrapper = json.decodeFromString<RecoveryDraftMap>(raw)
        val valid = wrapper.records.filterValues { it.schema == 1 && it.checksum == checksum(it) }
        if (valid.size != wrapper.records.size) null else LoadedRecords(valid, wrapper.tombstones, wasCorrupt = false)
    } catch (exception: Exception) {
        co.touchlab.kermit.Logger.w(exception) { "Failed to decode persisted recovery drafts." }
        null
    }

    private fun emptyRecord(workspaceId: String, generation: Long) = RecoveryDraftRecord(
        workspaceId = workspaceId,
        baseSnapshotId = null,
        baseSnapshotDigest = null,
        draftRevision = 0L,
        discardGeneration = generation,
        acquisition = AcquisitionSettings(
            region = DefaultTargetCatalog.PQ84P01_DEFAULT.availableRegions.first(),
            firmware = DefaultTargetCatalog.PQ84P01_GLOBAL_FIRMWARES.first(),
        ),
        extraction = ExtractionSettings(), assembly = AssemblySettings(), debloat = DebloatSettings(),
        customization = CustomizationSettings(), build = BuildSettings(), release = ReleaseSettings(),
        publish = PublishSettings(), lastPersistedAt = Clock.System.now(),
    )

    private fun checksum(record: RecoveryDraftRecord): String {
        val payload = record.copy(checksum = "")
        val bytes = json.encodeToString(payload).toByteArray(Charsets.UTF_8)
        return "sha256:" + MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }

    @Serializable private data class RecoveryDraftMap(
        val records: Map<String, RecoveryDraftRecord> = emptyMap(),
        val tombstones: Map<String, Long> = emptyMap(),
    )
    private data class LoadedRecords(
        val records: Map<String, RecoveryDraftRecord>,
        val tombstones: Map<String, Long>,
        val wasCorrupt: Boolean,
    )

    public companion object {
        public const val KEY_RECORDS: String = "lti_recovery_drafts_v1"
        public const val KEY_BACKUP: String = "lti_recovery_drafts_v1_backup"
        public fun defaultJson(): Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
        }
    }
}
