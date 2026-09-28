/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.setup

import org.ide.lti.core.model.setup.SetupLogBounds

/**
 * Activity retention bounds as seen by domain and presentation code (FR-011, FR-012, SC-004).
 * The numbers are defined once in [SetupLogBounds] (core/model) so the persisted journal shares them;
 * this object is the domain-facing name feature code depends on.
 */
public object SetupLogConstants {
    /** "Each producer and presentation buffer retains at most 10,000 lines" (FR-011). */
    public const val MAX_BUFFER_LINES: Int = SetupLogBounds.MAX_BUFFER_LINES

    /** "and 8 MiB of UTF-8 payload" (FR-011). */
    public const val MAX_PAYLOAD_BYTES: Long = SetupLogBounds.MAX_PAYLOAD_BYTES

    /** "each line is at most 16 KiB including its truncation marker" (FR-011). */
    public const val MAX_LINE_BYTES: Int = SetupLogBounds.MAX_LINE_BYTES

    public const val TRUNCATION_MARKER: String = SetupLogBounds.TRUNCATION_MARKER

    /** See [SetupLogBounds.truncateToUtf8Bytes]. */
    public fun truncateToUtf8Bytes(
        text: String,
        maxBytes: Int = MAX_LINE_BYTES,
        marker: String = TRUNCATION_MARKER,
    ): String = SetupLogBounds.truncateToUtf8Bytes(text, maxBytes, marker)
}

/**
 * Semantic kind of a setup activity line (data-model.md § Activity event kinds).
 */
public enum class SetupLogKind {
    CHECK,
    RUN,
    ERROR,
}

/**
 * Immutable activity line with stable identity (FR-011, FR-012, data-model.md).
 *
 * Invariant: "Log identity is childRunId plus sequence within an attempt."
 */
public data class SetupLogEvent(
    val attemptId: String,
    val childRunId: String,
    val sequence: Long,
    val text: String,
    val kind: SetupLogKind = SetupLogKind.RUN,
) {
    /** Stable identity: attempt, child run and server sequence. */
    val id: String get() = "$attemptId:$childRunId:$sequence"

    public companion object {
        /** Builds an event whose text respects the 16 KiB UTF-8 line limit without splitting a code point. */
        public fun create(
            attemptId: String,
            childRunId: String,
            sequence: Long,
            text: String,
            kind: SetupLogKind = SetupLogKind.RUN,
        ): SetupLogEvent = SetupLogEvent(
            attemptId = attemptId,
            childRunId = childRunId,
            sequence = sequence,
            text = SetupLogConstants.truncateToUtf8Bytes(text),
            kind = kind,
        )
    }
}
