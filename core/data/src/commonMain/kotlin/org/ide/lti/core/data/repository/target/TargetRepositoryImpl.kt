/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.repository.target

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.ide.lti.core.datastore.TargetPreferencesDataSource
import org.ide.lti.core.domain.repository.target.TargetRepository
import org.ide.lti.core.model.target.DefaultTargetCatalog
import org.ide.lti.core.model.target.TargetDevice

/**
 * Production implementation of [TargetRepository] managing target device profiles
 * and reactive selection persistence.
 *
 * Follows Open/Closed Principle: supports preconfigured official hardware targets
 * with extensible profile registry and interactive region / firmware configuration.
 */
class TargetRepositoryImpl(private val preferencesDataSource: TargetPreferencesDataSource) : TargetRepository {

    private val _targets: MutableStateFlow<List<TargetDevice>>
    private val _selectedTargetId: MutableStateFlow<String>

    init {
        // Seed in-memory only - never block the constructing thread on I/O. If this is a
        // fresh install (cache empty), the deterministic default is simply re-seeded the
        // same way on every launch until the first real mutation persists it for real.
        val cachedTargets = preferencesDataSource.currentTargets
        val initialTargets = cachedTargets.ifEmpty { listOf(DefaultTargetCatalog.PQ84P01_DEFAULT) }
        _targets = MutableStateFlow(initialTargets)

        val cachedSelectedId = preferencesDataSource.currentSelectedTargetId
        val initialSelectedId = if (cachedSelectedId != null && initialTargets.any { it.id == cachedSelectedId }) {
            cachedSelectedId
        } else {
            initialTargets.first().id
        }
        _selectedTargetId = MutableStateFlow(initialSelectedId)
    }

    /** One change at a time, list and selection together, written before memory shows it. */
    private val writes = Mutex()

    override fun getAvailableTargets(): Flow<List<TargetDevice>> = _targets.asStateFlow()

    // An edit of the selected target is a new value too, not only another selection.
    override fun getSelectedTarget(): Flow<TargetDevice> = combine(_targets, _selectedTargetId) { targets, id ->
        targets.find { it.id == id } ?: DefaultTargetCatalog.PQ84P01_DEFAULT
    }

    override suspend fun selectTarget(targetId: String): Result<TargetDevice> = writes.withLock {
        runCatching {
            val target = requireNotNull(_targets.value.find { it.id == targetId }) { "Unknown target ID: $targetId" }
            preferencesDataSource.saveSelectedTargetId(target.id)
            _selectedTargetId.value = target.id
            target
        }
    }

    override suspend fun addTarget(target: TargetDevice): Result<TargetDevice> = writes.withLock {
        runCatching {
            val current = _targets.value
            val adding = current.none { it.id == target.id }
            val updatedList = if (adding) current + target else current.map { if (it.id == target.id) target else it }
            preferencesDataSource.saveTargets(updatedList)
            _targets.value = updatedList
            if (adding) {
                preferencesDataSource.saveSelectedTargetId(target.id)
                _selectedTargetId.value = target.id
            }
            target
        }
    }

    override suspend fun updateTarget(target: TargetDevice): Result<TargetDevice> = addTarget(target)

    override suspend fun deleteTarget(targetId: String): Result<Unit> = writes.withLock {
        runCatching {
            val currentList = _targets.value
            check(currentList.size > 1) { "Cannot delete the only remaining target profile." }
            require(currentList.any { it.id == targetId }) { "Target not found: $targetId" }
            val updatedList = currentList.filter { it.id != targetId }
            preferencesDataSource.saveTargets(updatedList)
            _targets.value = updatedList
            if (_selectedTargetId.value == targetId) {
                val newSelectedId = updatedList.first().id
                preferencesDataSource.saveSelectedTargetId(newSelectedId)
                _selectedTargetId.value = newSelectedId
            }
        }
    }
}
