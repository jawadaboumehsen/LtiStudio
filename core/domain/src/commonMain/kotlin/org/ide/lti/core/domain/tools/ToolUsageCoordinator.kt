/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.tools

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

public class ToolUsageCoordinator {
    private val mutex = Mutex()
    private val activeJobIds = mutableSetOf<String>()
    private var isReconciling: Boolean = false

    private val _jobActive = MutableStateFlow(false)

    /** Whether a ROM job (or its reconciliation) owns the tools right now; screens follow it. */
    public val jobActive: StateFlow<Boolean> = _jobActive.asStateFlow()

    private var activeMutationsCount: Int = 0

    public val isJobActive: Boolean
        get() = _jobActive.value

    public val isMutating: Boolean
        get() = activeMutationsCount > 0

    public suspend fun <T> admitJob(jobId: String, capturePins: suspend () -> T): T = mutex.withLock {
        if (activeMutationsCount > 0) {
            throw IllegalStateException("Tool mutation is in progress")
        }
        check(jobId !in activeJobIds) { "ROM job $jobId is already admitted" }
        val result = capturePins()
        activeJobIds.add(jobId)
        _jobActive.value = true
        result
    }

    public suspend fun finishJob(jobId: String): Unit = withContext(NonCancellable) {
        mutex.withLock {
            activeJobIds.remove(jobId)
            _jobActive.value = activeJobIds.isNotEmpty() || isReconciling
        }
    }

    /**
     * Coordinates every mutation of the component-tool store with ROM execution.
     * Guards admission without holding the mutex across the entire build.
     */
    public suspend fun <T> withToolMutationAdmission(block: suspend () -> T): Result<T> {
        mutex.withLock {
            if (isJobActive) {
                return Result.failure(IllegalStateException("A ROM job is running"))
            }
            activeMutationsCount++
        }
        return try {
            Result.success(block())
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            withContext(NonCancellable) {
                mutex.withLock {
                    if (activeMutationsCount > 0) {
                        activeMutationsCount--
                    }
                }
            }
        }
    }

    /** Compatibility name for callers whose mutation is specifically a removal. */
    public suspend fun <T> withRemovalAdmission(block: suspend () -> T): Result<T> = withToolMutationAdmission(block)

    public suspend fun markReconciling(reconciling: Boolean) {
        mutex.withLock {
            isReconciling = reconciling
            _jobActive.value = activeJobIds.isNotEmpty() || isReconciling
        }
    }
}
