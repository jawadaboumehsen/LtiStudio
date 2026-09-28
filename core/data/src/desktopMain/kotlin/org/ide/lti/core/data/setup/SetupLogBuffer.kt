/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onSubscription
import org.ide.lti.core.data.common.resolveAppDataDir
import org.ide.lti.core.domain.setup.SetupLogConstants
import org.ide.lti.core.domain.setup.SetupLogEvent
import org.ide.lti.core.domain.setup.SetupLogKind
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * Receiver of streamed run output carrying its real journal identity, so that reconnect/replay
 * deduplicates by (attemptId, childRunId, sequence) instead of re-appending under a synthetic counter.
 */
public fun interface SetupActivitySink {
    public fun record(attemptId: String, childRunId: String, sequence: Long, text: String)
}

/**
 * Bounded activity log buffer enforcing producer-side retention and deduplication (FR-011, FR-012, SC-004).
 *
 * Invariants:
 * - "Log identity is childRunId plus sequence within an attempt."
 * - "Each producer and presentation buffer retains at most 10,000 lines and 8 MiB of UTF-8 payload;
 *    each line is at most 16 KiB including its truncation marker."
 * - Eviction is constant-size per event (ArrayDeque head removal); snapshots never exceed the retention caps.
 * - Persisted attempt log: appends every event to a size-capped file `<app data>/logs/setup/<attemptId>.log`
 *   with cap and rotation recorded in the file header (FR-036, T067).
 *
 * [onRecorded] is invoked for every newly retained event (after deduplication) so the owner can mirror
 * a short tail into its immutable state without re-reading the whole buffer.
 */
public class SetupLogBuffer(
    private val maxLines: Int = SetupLogConstants.MAX_BUFFER_LINES,
    private val maxPayloadBytes: Long = SetupLogConstants.MAX_PAYLOAD_BYTES,
    private val logDirectory: File? = defaultLogDirectory(),
    private val maxLogFileSizeBytes: Long = DEFAULT_MAX_LOG_FILE_SIZE,
    private val onRecorded: (SetupLogEvent) -> Unit = {},
) : SetupActivitySink {
    public companion object {
        public const val DEFAULT_MAX_LOG_FILE_SIZE: Long = 8 * 1024 * 1024L // 8 MiB

        /** Same app-data root as the journal and run files (`<LOCALAPPDATA>/LtiRomGui/logs/setup`). */
        public fun defaultLogDirectory(): File = resolveAppDataDir("logs/setup").toFile()
    }

    private val lock = Any()

    /** Serializes file appends and rotation; events can arrive from several coroutines at once. */
    private val fileLock = Any()
    private val deque = ArrayDeque<SetupLogEvent>()
    private val retainedIds = HashSet<String>()

    /**
     * Live stream sized to the retention cap: a subscriber slower than the buffer's own eviction loses
     * exactly the lines it would have evicted anyway, never newer ones.
     */
    private val events = MutableSharedFlow<SetupLogEvent>(
        replay = 0,
        extraBufferCapacity = maxLines,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val localSequence = AtomicLong(1L)

    private var payloadBytes: Long = 0L
    private var dropped: Long = 0L

    public val size: Int
        get() = synchronized(lock) { deque.size }

    public val totalPayloadBytes: Long
        get() = synchronized(lock) { payloadBytes }

    public val droppedLineCount: Long
        get() = synchronized(lock) { dropped }

    override fun record(attemptId: String, childRunId: String, sequence: Long, text: String) {
        append(attemptId, childRunId, sequence, text)
    }

    /**
     * Appends [event]; returns false when its identity is already retained (replay duplicate).
     * Applies the 16 KiB line limit, then evicts from the head until both retention caps hold.
     */
    public fun append(event: SetupLogEvent): Boolean {
        val safeEvent = SetupLogEvent.create(
            attemptId = event.attemptId,
            childRunId = event.childRunId,
            sequence = event.sequence,
            text = event.text,
            kind = event.kind,
        )
        val eventBytes = safeEvent.text.encodeToByteArray().size.toLong()

        synchronized(lock) {
            if (!retainedIds.add(safeEvent.id)) return false
            deque.addLast(safeEvent)
            payloadBytes += eventBytes
            while (deque.size > maxLines || payloadBytes > maxPayloadBytes) {
                val evicted = deque.removeFirst()
                retainedIds.remove(evicted.id)
                payloadBytes -= evicted.text.encodeToByteArray().size.toLong()
                dropped++
            }
        }

        persistEventToFile(safeEvent)
        events.tryEmit(safeEvent)
        onRecorded(safeEvent)
        return true
    }

    private fun persistEventToFile(event: SetupLogEvent) {
        val dir = logDirectory ?: return
        synchronized(fileLock) { writeEventLine(dir, event) }
    }

    /** Open writer for the file currently being appended; reopening per line is slow on Windows. */
    private var openFile: File? = null
    private var openWriter: java.io.Writer? = null
    private var openFileBytes: Long = 0L

    private fun writeEventLine(dir: File, event: SetupLogEvent) {
        try {
            val fileAttemptId = fileIdFor(event.attemptId)
            val logFile = File(dir, "$fileAttemptId.log")
            val line = "[${event.kind.name}] ${event.text}\n"
            val lineBytes = line.toByteArray().size
            if (openFile != logFile) openWriterFor(dir, logFile, fileAttemptId)
            if (openFileBytes + lineBytes > maxLogFileSizeBytes) rotate(dir, logFile, fileAttemptId)
            val writer = openWriter ?: return
            writer.write(line)
            writer.flush()
            openFileBytes += lineBytes
        } catch (@Suppress("TooGenericExceptionCaught") _: Exception) {
            // Persisted log file failures must never fail in-memory buffer operations.
            closeWriter()
        }
    }

    private fun openWriterFor(dir: File, logFile: File, fileAttemptId: String) {
        closeWriter()
        if (!dir.exists()) dir.mkdirs()
        val isNew = !logFile.exists() || logFile.length() == 0L
        openWriter = java.io.BufferedWriter(java.io.FileWriter(logFile, Charsets.UTF_8, true))
        openFile = logFile
        openFileBytes = if (isNew) 0L else logFile.length()
        if (isNew) writeHeader(fileAttemptId)
    }

    private fun rotate(dir: File, logFile: File, fileAttemptId: String) {
        closeWriter()
        val backupFile = File(dir, "$fileAttemptId.log.1")
        if (backupFile.exists()) backupFile.delete()
        logFile.renameTo(backupFile)
        openWriterFor(dir, logFile, fileAttemptId)
    }

    private fun writeHeader(fileAttemptId: String) {
        val header = "# Setup Attempt Log: $fileAttemptId\n" +
            "# Max Size: $maxLogFileSizeBytes bytes | Rotation: 1 backup\n"
        openWriter?.write(header)
        openWriter?.flush()
        openFileBytes += header.toByteArray().size
    }

    private fun closeWriter() {
        runCatching { openWriter?.close() }
        openWriter = null
        openFile = null
        openFileBytes = 0L
    }

    private fun fileIdFor(attemptId: String?): String {
        val rawId = attemptId?.trim().orEmpty()
        return if (rawId.isEmpty() || rawId == "local" || rawId == "session") "local" else rawId
    }

    /** The most recently written log file (any attempt, or the `local` checks log); null when none exists. */
    public fun latestLogFile(): File? = logDirectory
        ?.listFiles { file -> file.isFile && file.name.endsWith(".log") }
        ?.maxByOrNull { it.lastModified() }

    public fun getLogFile(attemptId: String?): File? {
        val dir = logDirectory ?: return null
        val file = File(dir, "${fileIdFor(attemptId)}.log")
        return if (file.exists()) file else null
    }

    public fun append(
        attemptId: String,
        childRunId: String,
        sequence: Long,
        text: String,
        kind: SetupLogKind = SetupLogKind.RUN,
    ): Boolean = append(SetupLogEvent(attemptId, childRunId, sequence, text, kind))

    /** Appends narration that has no server run identity, under a process-local monotonic sequence. */
    public fun append(
        text: String,
        attemptId: String,
        childRunId: String,
        kind: SetupLogKind = SetupLogKind.RUN,
    ): Boolean = append(SetupLogEvent(attemptId, childRunId, localSequence.getAndIncrement(), text, kind))

    /** Point-in-time copy of retained events; bounded by [maxLines] and [maxPayloadBytes]. */
    public fun snapshot(): List<SetupLogEvent> = synchronized(lock) { deque.toList() }

    /**
     * Retained history followed by live events. Subscription is registered before the snapshot is
     * taken so no event can fall between them; an event seen in both is a duplicate by identity,
     * which every consumer must ignore (the buffer itself does for its own retention).
     */
    public fun observe(): Flow<SetupLogEvent> = events.onSubscription {
        for (event in snapshot()) emit(event)
    }

    public fun clear() {
        synchronized(lock) {
            deque.clear()
            retainedIds.clear()
            payloadBytes = 0L
            dropped = 0L
        }
    }
}
