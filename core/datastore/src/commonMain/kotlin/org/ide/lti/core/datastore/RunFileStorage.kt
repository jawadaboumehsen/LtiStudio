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

/**
 * Storage interface for persisting full BuildRun states and event logs as local files.
 */
interface RunFileStorage {
    suspend fun saveRun(runId: String, json: String)
    suspend fun getRun(runId: String): String?
    suspend fun appendEvents(runId: String, events: List<String>)
    suspend fun getEvents(runId: String, fromSeq: Long = 0L): List<String>
    suspend fun deleteRun(runId: String)
}

expect fun defaultRunFileStorage(): RunFileStorage
