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
public interface RunFileStorage {
    public suspend fun saveRun(runId: String, json: String)
    public suspend fun getRun(runId: String): String?
    public suspend fun appendEvents(runId: String, events: List<String>)
    public suspend fun getEvents(runId: String, fromSeq: Long = 0L): List<String>
    public suspend fun deleteRun(runId: String)
}

public expect fun defaultRunFileStorage(): RunFileStorage
