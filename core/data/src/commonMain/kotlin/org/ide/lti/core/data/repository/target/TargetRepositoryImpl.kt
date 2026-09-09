package org.ide.lti.core.data.repository.target

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.ide.lti.core.datastore.TargetPreferencesDataSource
import org.ide.lti.core.domain.repository.target.TargetRepository
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

    companion object {
        val PQ84P01_GLOBAL_FIRMWARES = listOf(
            TargetFirmware(
                version = "REDMAGICOS10.5.15_NP05J_GB",
                buildId = "PQ84P01:15/AQ3A.240812.002/20260311.222527",
                androidVersion = "15",
                securityPatch = "2026-02-01",
                otaUrl = "https://rom.download.nubia.com/Europe&Asia/PQ84P01(NP05J)/REDMAGICOS10.5.15_NP05J_GB/update.zip",
                isOfficial = true,
                isRecommended = true,
            ),
            TargetFirmware(
                version = "REDMAGICOS10.5.12_NP05J_GB",
                buildId = "PQ84P01:15/AQ3A.240812.002/20260120.143210",
                androidVersion = "15",
                securityPatch = "2026-01-01",
                otaUrl = "https://rom.download.nubia.com/Europe&Asia/PQ84P01(NP05J)/REDMAGICOS10.5.12_NP05J_GB/update.zip",
                isOfficial = true,
                isRecommended = false,
            ),
            TargetFirmware(
                version = "REDMAGICOS10.5.8_NP05J_GB",
                buildId = "PQ84P01:15/AQ3A.240812.002/20251115.110543",
                androidVersion = "15",
                securityPatch = "2025-11-01",
                otaUrl = "https://rom.download.nubia.com/Europe&Asia/PQ84P01(NP05J)/REDMAGICOS10.5.8_NP05J_GB/update.zip",
                isOfficial = true,
                isRecommended = false,
            ),
        )

        val PQ84P01_CHINA_FIRMWARES = listOf(
            TargetFirmware(
                version = "REDMAGICOS10.5.16_NP05J_CN",
                buildId = "PQ84P01:15/AQ3A.240812.002/20260315.191244",
                androidVersion = "15",
                securityPatch = "2026-02-01",
                otaUrl = "https://rom.download.nubia.com/China/PQ84P01(NP05J)/REDMAGICOS10.5.16_NP05J_CN/update.zip",
                isOfficial = true,
                isRecommended = true,
            ),
            TargetFirmware(
                version = "REDMAGICOS10.5.12_NP05J_CN",
                buildId = "PQ84P01:15/AQ3A.240812.002/20260125.102230",
                androidVersion = "15",
                securityPatch = "2026-01-01",
                otaUrl = "https://rom.download.nubia.com/China/PQ84P01(NP05J)/REDMAGICOS10.5.12_NP05J_CN/update.zip",
                isOfficial = true,
                isRecommended = false,
            ),
            TargetFirmware(
                version = "REDMAGICOS10.5.8_NP05J_CN",
                buildId = "PQ84P01:15/AQ3A.240812.002/20251120.091512",
                androidVersion = "15",
                securityPatch = "2025-11-01",
                otaUrl = "https://rom.download.nubia.com/China/PQ84P01(NP05J)/REDMAGICOS10.5.8_NP05J_CN/update.zip",
                isOfficial = true,
                isRecommended = false,
            ),
            TargetFirmware(
                version = "REDMAGICOS10.5.16_MR01A",
                buildId = "PQ84P01:15/AQ3A.240812.002/20260315.191244",
                androidVersion = "15",
                securityPatch = "2026-02-01",
                otaUrl = "https://rom.download.nubia.com/China/PQ84P01/REDMAGICOS10.5.16_MR01A/update.zip",
                isOfficial = true,
                isRecommended = false,
            ),
        )

        val PQ84P01_FIRMWARES = mapOf(
            TargetRegion.GLOBAL to PQ84P01_GLOBAL_FIRMWARES,
            TargetRegion.CHINA to PQ84P01_CHINA_FIRMWARES,
        )

        // Aliases for backward compatibility
        val GLOBAL_FIRMWARES = PQ84P01_GLOBAL_FIRMWARES
        val CHINA_FIRMWARES = PQ84P01_CHINA_FIRMWARES
        val FIRMWARES_BY_REGION = PQ84P01_FIRMWARES
    }

    private val defaultTarget = TargetDevice(
        id = "PQ84P01",
        name = "REDMAGIC Astra Gaming Tablet",
        codename = "PQ84P01",
        selectedRegion = TargetRegion.GLOBAL,
        availableRegions = listOf(TargetRegion.GLOBAL, TargetRegion.CHINA),
        selectedFirmware = PQ84P01_GLOBAL_FIRMWARES.first(),
        availableFirmwares = PQ84P01_FIRMWARES,
        socPlatform = "Snapdragon 8 Elite (SM8750 / sun)",
        filesystemType = "erofs",
        superPartitionBytes = 17179869184L,
        dynamicPartitions = listOf(
            "system",
            "vendor",
            "product",
            "system_ext",
            "odm",
            "vendor_dlkm",
            "system_dlkm",
        ),
        bootPartitions = listOf("boot", "init_boot", "vendor_boot", "dtbo"),
        status = TargetStatus.QUALIFIED,
        description = "Official Global & European retail baseline (NP05J_GB)",
        isDefault = true,
    )

    private val _targets: MutableStateFlow<List<TargetDevice>>
    private val _selectedTargetId: MutableStateFlow<String>

    init {
        val cachedTargets = preferencesDataSource.currentTargets
        val initialTargets = if (cachedTargets.isEmpty()) {
            val initial = listOf(defaultTarget)
            runBlocking {
                preferencesDataSource.saveTargets(initial)
                preferencesDataSource.saveSelectedTargetId(defaultTarget.id)
            }
            initial
        } else {
            cachedTargets
        }
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
            _targets.value.find { it.id == selectedId } ?: defaultTarget
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
        val current = _targets.value.find { it.id == _selectedTargetId.value } ?: defaultTarget
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
        val current = _targets.value.find { it.id == _selectedTargetId.value } ?: defaultTarget
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
        val current = _targets.value.find { it.id == _selectedTargetId.value } ?: defaultTarget
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
        val current = _targets.value.find { it.id == _selectedTargetId.value } ?: defaultTarget
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
