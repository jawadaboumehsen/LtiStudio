package org.ide.lti.core.data.repository.target

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import org.ide.lti.core.datastore.TargetPreferencesDataSource
import org.ide.lti.core.domain.repository.target.TargetRepository
import org.ide.lti.core.model.target.DefaultTargetCatalog
import org.ide.lti.core.model.target.FirmwareAcquisitionMode
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.target.TargetFirmware
import org.ide.lti.core.model.target.TargetRegion
import org.ide.lti.core.model.target.TargetStatus

/**
 * Production implementation of [TargetRepository] managing target device profiles
 * and reactive selection persistence.
 *
 * Follows Open/Closed Principle: supports preconfigured official hardware targets
 * with extensible profile registry and interactive region / firmware configuration.
 */
class TargetRepositoryImpl(
    private val preferencesDataSource: TargetPreferencesDataSource,
) : TargetRepository {

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

    override fun getAvailableTargets(): Flow<List<TargetDevice>> = _targets.asStateFlow()

    override fun getSelectedTarget(): Flow<TargetDevice> {
        return _selectedTargetId.map { selectedId ->
            _targets.value.find { it.id == selectedId } ?: DefaultTargetCatalog.PQ84P01_DEFAULT
        }
    }

    override suspend fun selectTarget(targetId: String): Result<TargetDevice> {
        val target = _targets.value.find { it.id == targetId }
            ?: return Result.failure(IllegalArgumentException("Unknown target ID: $targetId"))
        _selectedTargetId.value = target.id
        preferencesDataSource.saveSelectedTargetId(target.id)
        return Result.success(target)
    }

    override suspend fun selectRegion(region: TargetRegion): Result<TargetDevice> {
        val current = _targets.value.find { it.id == _selectedTargetId.value } ?: DefaultTargetCatalog.PQ84P01_DEFAULT
        val recommendedFirmware = current.availableFirmwares[region]?.firstOrNull { it.isRecommended }
            ?: current.availableFirmwares[region]?.firstOrNull()
            ?: current.selectedFirmware

        val updated = current.copy(
            selectedRegion = region,
            selectedFirmware = recommendedFirmware,
            status = if (recommendedFirmware.isOfficial) TargetStatus.QUALIFIED else TargetStatus.EXPERIMENTAL,
            description = when (region) {
                TargetRegion.GLOBAL -> "Official Global & European retail baseline (${current.codename}_GB / NP05J_GB)"
                TargetRegion.CHINA -> "Official China mainland retail baseline (${current.codename}_CN / NP05J_CN)"
            },
        )

        val updatedList = _targets.value.map { if (it.id == updated.id) updated else it }
        _targets.value = updatedList
        preferencesDataSource.saveTargets(updatedList)
        return Result.success(updated)
    }

    override suspend fun selectFirmware(firmware: TargetFirmware): Result<TargetDevice> {
        val current = _targets.value.find { it.id == _selectedTargetId.value } ?: DefaultTargetCatalog.PQ84P01_DEFAULT
        val updated = current.copy(
            selectedFirmware = firmware,
            status = if (firmware.isOfficial) TargetStatus.QUALIFIED else TargetStatus.EXPERIMENTAL,
        )

        val updatedList = _targets.value.map { if (it.id == updated.id) updated else it }
        _targets.value = updatedList
        preferencesDataSource.saveTargets(updatedList)
        return Result.success(updated)
    }

    override suspend fun setCustomFirmware(
        version: String,
        buildId: String?,
        otaUrl: String?,
    ): Result<TargetDevice> {
        val current = _targets.value.find { it.id == _selectedTargetId.value } ?: DefaultTargetCatalog.PQ84P01_DEFAULT
        val customFirmware = TargetFirmware(
            version = version.trim(),
            buildId = buildId?.trim() ?: "${current.codename}:15/CUSTOM/${version.trim()}",
            androidVersion = "15",
            securityPatch = "Custom",
            otaUrl = otaUrl?.trim(),
            isOfficial = false,
            isRecommended = false,
        )

        val updated = current.copy(
            selectedFirmware = customFirmware,
            status = TargetStatus.EXPERIMENTAL,
            description = "Custom firmware build (${customFirmware.version})",
        )

        val updatedList = _targets.value.map { if (it.id == updated.id) updated else it }
        _targets.value = updatedList
        preferencesDataSource.saveTargets(updatedList)
        return Result.success(updated)
    }

    override suspend fun selectAcquisitionMode(mode: FirmwareAcquisitionMode): Result<TargetDevice> {
        val current = _targets.value.find { it.id == _selectedTargetId.value } ?: DefaultTargetCatalog.PQ84P01_DEFAULT
        val updated = current.copy(acquisitionMode = mode)
        val updatedList = _targets.value.map { if (it.id == updated.id) updated else it }
        _targets.value = updatedList
        preferencesDataSource.saveTargets(updatedList)
        return Result.success(updated)
    }

    override suspend fun addTarget(target: TargetDevice): Result<TargetDevice> {
        val existing = _targets.value.find { it.id == target.id }
        if (existing != null) {
            return updateTarget(target)
        }
        val updatedList = _targets.value + target
        _targets.value = updatedList
        _selectedTargetId.value = target.id
        preferencesDataSource.saveTargets(updatedList)
        preferencesDataSource.saveSelectedTargetId(target.id)
        return Result.success(target)
    }

    override suspend fun updateTarget(target: TargetDevice): Result<TargetDevice> {
        val exists = _targets.value.any { it.id == target.id }
        if (!exists) {
            return addTarget(target)
        }
        val updatedList = _targets.value.map { if (it.id == target.id) target else it }
        _targets.value = updatedList
        preferencesDataSource.saveTargets(updatedList)
        return Result.success(target)
    }

    override suspend fun deleteTarget(targetId: String): Result<Unit> {
        val currentList = _targets.value
        if (currentList.size <= 1) {
            return Result.failure(IllegalStateException("Cannot delete the only remaining target profile."))
        }
        val targetToDelete = currentList.find { it.id == targetId }
            ?: return Result.failure(IllegalArgumentException("Target not found: $targetId"))

        val updatedList = currentList.filter { it.id != targetId }
        _targets.value = updatedList
        preferencesDataSource.saveTargets(updatedList)
        if (_selectedTargetId.value == targetId) {
            val newSelectedId = updatedList.first().id
            _selectedTargetId.value = newSelectedId
            preferencesDataSource.saveSelectedTargetId(newSelectedId)
        }
        return Result.success(Unit)
    }
}
