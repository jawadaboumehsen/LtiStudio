/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.configuration

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.ide.lti.core.domain.repository.target.TargetRepository
import org.ide.lti.core.domain.usecase.target.AddTargetUseCase
import org.ide.lti.core.domain.usecase.target.CreateTargetScaffoldUseCase
import org.ide.lti.core.domain.usecase.target.DeleteTargetUseCase
import org.ide.lti.core.domain.usecase.target.DuplicateTargetUseCase
import org.ide.lti.core.domain.usecase.target.GetAvailableTargetsUseCase
import org.ide.lti.core.domain.usecase.target.GetSelectedTargetUseCase
import org.ide.lti.core.domain.usecase.target.SaveTargetConfigurationUseCase
import org.ide.lti.core.domain.usecase.target.SelectTargetUseCase
import org.ide.lti.core.domain.usecase.target.UpdateTargetUseCase
import org.ide.lti.core.domain.usecase.target.ValidateTargetConfigurationUseCase
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.target.TargetFirmware
import org.ide.lti.core.model.target.TargetRegion
import org.ide.lti.core.model.target.TargetStatus
import org.ide.lti.core.model.target.TargetValidationError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ConfigurationViewModelTest {

    private class FakeTargetRepository(val targets: List<TargetDevice>, initialSelected: TargetDevice) :
        TargetRepository {
        val targetsFlow = MutableStateFlow(targets)
        val selectedTargetFlow = MutableStateFlow(initialSelected)

        override fun getAvailableTargets(): Flow<List<TargetDevice>> = targetsFlow.asStateFlow()
        override fun getSelectedTarget(): Flow<TargetDevice> = selectedTargetFlow.asStateFlow()

        override suspend fun selectTarget(targetId: String): Result<TargetDevice> {
            val found = targetsFlow.value.find { it.id == targetId }
            return if (found != null) {
                selectedTargetFlow.value = found
                Result.success(found)
            } else {
                Result.failure(IllegalArgumentException("Target not found: $targetId"))
            }
        }

        override suspend fun addTarget(target: TargetDevice): Result<TargetDevice> {
            val list = targetsFlow.value.toMutableList()
            list.removeAll { it.id == target.id }
            list.add(target)
            targetsFlow.value = list
            return Result.success(target)
        }

        override suspend fun updateTarget(target: TargetDevice): Result<TargetDevice> = addTarget(target)

        override suspend fun deleteTarget(targetId: String): Result<Unit> {
            val list = targetsFlow.value.toMutableList()
            list.removeAll { it.id == targetId }
            targetsFlow.value = list
            return Result.success(Unit)
        }
    }

    private fun createTestFixture(): Pair<ConfigurationViewModel, FakeTargetRepository> {
        val fw = TargetFirmware(
            version = "REDMAGICOS10.5.15_NP05J_GB",
            buildId = "PQ84P01:15/AQ3A.240812.002/20260311.222527",
            isOfficial = true,
        )
        val defaultDevice = TargetDevice(
            id = "PQ84P01",
            name = "REDMAGIC Astra Gaming Tablet",
            codename = "PQ84P01",
            availableRegions = listOf(TargetRegion.GLOBAL),
            availableFirmwares = mapOf(TargetRegion.GLOBAL to listOf(fw)),
            socPlatform = "Snapdragon 8 Elite",
            filesystemType = "erofs",
            superPartitionBytes = 17179869184L,
            dynamicPartitions = listOf("system", "vendor", "product"),
            bootPartitions = listOf("boot", "init_boot"),
            status = TargetStatus.QUALIFIED,
            description = "Official Astra Global",
            isDefault = true,
        )
        val secondaryDevice = TargetDevice(
            id = "NX789J",
            name = "REDMAGIC 10 Pro+",
            codename = "NX789J",
            availableRegions = listOf(TargetRegion.GLOBAL),
            availableFirmwares = mapOf(TargetRegion.GLOBAL to listOf(fw)),
            socPlatform = "Snapdragon 8 Elite",
            filesystemType = "erofs",
            superPartitionBytes = 17179869184L,
            dynamicPartitions = listOf("system", "vendor"),
            bootPartitions = listOf("boot"),
            status = TargetStatus.QUALIFIED,
            description = "10 Pro+ Global",
        )

        val repo = FakeTargetRepository(listOf(defaultDevice, secondaryDevice), defaultDevice)
        val validateTarget = ValidateTargetConfigurationUseCase()
        val addTarget = AddTargetUseCase(repo)
        val updateTarget = UpdateTargetUseCase(repo)
        val selectTarget = SelectTargetUseCase(repo)
        val createScaffold = CreateTargetScaffoldUseCase()
        val duplicateTarget = DuplicateTargetUseCase()
        val deleteTarget = DeleteTargetUseCase(repo)
        val saveTarget = SaveTargetConfigurationUseCase(
            validateTargetConfigurationUseCase = validateTarget,
            addTargetUseCase = addTarget,
            updateTargetUseCase = updateTarget,
            selectTargetUseCase = selectTarget,
        )

        val viewModel = ConfigurationViewModel(
            getAvailableTargetsUseCase = GetAvailableTargetsUseCase(repo),
            getSelectedTargetUseCase = GetSelectedTargetUseCase(repo),
            selectTargetUseCase = selectTarget,
            createTargetScaffoldUseCase = createScaffold,
            duplicateTargetUseCase = duplicateTarget,
            deleteTargetUseCase = deleteTarget,
            validateTargetConfigurationUseCase = validateTarget,
            saveTargetConfigurationUseCase = saveTarget,
        )

        return Pair(viewModel, repo)
    }

    @Test
    fun testInitialStateLoadsDefaultTarget() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val (viewModel, _) = createTestFixture()
            val job = backgroundScope.launch(testDispatcher) { viewModel.uiState.collect() }
            testScheduler.advanceUntilIdle()

            assertEquals(2, viewModel.uiState.value.availableTargets.size)
            assertEquals("PQ84P01", viewModel.uiState.value.selectedTargetId)
            assertEquals("REDMAGIC Astra Gaming Tablet", viewModel.uiState.value.editingTarget.name)
            assertFalse(viewModel.uiState.value.isDirty)
            assertTrue(viewModel.uiState.value.validationErrors.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testSelectTargetUpdatesEditingTarget() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val (viewModel, _) = createTestFixture()
            val job = backgroundScope.launch(testDispatcher) { viewModel.uiState.collect() }
            testScheduler.advanceUntilIdle()

            viewModel.onSelectTarget("NX789J")
            testScheduler.advanceUntilIdle()

            assertEquals("NX789J", viewModel.uiState.value.selectedTargetId)
            assertEquals("REDMAGIC 10 Pro+", viewModel.uiState.value.editingTarget.name)
            assertFalse(viewModel.uiState.value.isDirty)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testUpdateEditingTargetSetsDirtyFlag() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val (viewModel, _) = createTestFixture()
            val job = backgroundScope.launch(testDispatcher) { viewModel.uiState.collect() }
            testScheduler.advanceUntilIdle()

            val modified = viewModel.uiState.value.editingTarget.copy(name = "REDMAGIC Astra Modified")
            viewModel.onUpdateEditingTarget(modified)
            testScheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isDirty)
            assertEquals("REDMAGIC Astra Modified", viewModel.uiState.value.editingTarget.name)

            // Revert changes
            viewModel.onRevertChanges()
            testScheduler.advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isDirty)
            assertEquals("REDMAGIC Astra Gaming Tablet", viewModel.uiState.value.editingTarget.name)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testValidationCatchesEmptyFields() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val (viewModel, _) = createTestFixture()
            val job = backgroundScope.launch(testDispatcher) { viewModel.uiState.collect() }
            testScheduler.advanceUntilIdle()

            val invalid = viewModel.uiState.value.editingTarget.copy(
                name = "",
                codename = "",
                superPartitionBytes = 0L,
                dynamicPartitions = emptyList(),
            )
            viewModel.onUpdateEditingTarget(invalid)
            testScheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value.validationErrors.isNotEmpty())
            assertTrue(viewModel.uiState.value.validationErrors.any { it is TargetValidationError.EmptyName })
            assertTrue(viewModel.uiState.value.validationErrors.any { it is TargetValidationError.EmptyCodename })
            assertTrue(
                viewModel.uiState.value.validationErrors.any {
                    it is TargetValidationError.InvalidSuperPartitionSize
                },
            )
            assertTrue(
                viewModel.uiState.value.validationErrors.any {
                    it is TargetValidationError.EmptyDynamicPartitions
                },
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testAddNewTargetScaffold() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val (viewModel, _) = createTestFixture()
            val job = backgroundScope.launch(testDispatcher) { viewModel.uiState.collect() }
            testScheduler.advanceUntilIdle()

            viewModel.onAddNewTarget()
            testScheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isDirty)
            assertTrue(viewModel.uiState.value.editingTarget.id.startsWith("TARGET_NEW_"))
            assertTrue(viewModel.uiState.value.validationErrors.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testDuplicateTarget() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val (viewModel, _) = createTestFixture()
            val job = backgroundScope.launch(testDispatcher) { viewModel.uiState.collect() }
            testScheduler.advanceUntilIdle()

            val current = viewModel.uiState.value.editingTarget
            // First duplication
            viewModel.onDuplicateTarget(current)
            testScheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isDirty)
            assertEquals("PQ84P01_COPY", viewModel.uiState.value.editingTarget.id)
            assertEquals("REDMAGIC Astra Gaming Tablet (Copy)", viewModel.uiState.value.editingTarget.name)

            // Save first duplicate
            viewModel.onApplyChanges()
            testScheduler.advanceUntilIdle()
            assertEquals(3, viewModel.uiState.value.availableTargets.size)
            assertTrue(viewModel.uiState.value.availableTargets.any { it.id == "PQ84P01_COPY" })

            // Second duplication of the same source target
            viewModel.onDuplicateTarget(current)
            testScheduler.advanceUntilIdle()
            val secondDuplicate = viewModel.uiState.value.editingTarget
            assertEquals("PQ84P01_COPY_2", secondDuplicate.id)

            // Save second duplicate
            viewModel.onApplyChanges()
            testScheduler.advanceUntilIdle()

            // Both duplicates exist in availableTargets with different IDs
            val available = viewModel.uiState.value.availableTargets
            assertEquals(4, available.size)
            assertTrue(available.any { it.id == "PQ84P01_COPY" })
            assertTrue(available.any { it.id == "PQ84P01_COPY_2" })
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testApplyChangesPersists() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val (viewModel, repo) = createTestFixture()
            val job = backgroundScope.launch(testDispatcher) { viewModel.uiState.collect() }
            testScheduler.advanceUntilIdle()

            val updated = viewModel.uiState.value.editingTarget.copy(socPlatform = "SM8750 Extreme Edition")
            viewModel.onUpdateEditingTarget(updated)
            testScheduler.advanceUntilIdle()

            var successCalled = false
            viewModel.onApplyChanges(onSuccess = { successCalled = true })
            testScheduler.advanceUntilIdle()

            assertTrue(successCalled)
            assertFalse(viewModel.uiState.value.isDirty)

            val saved = repo.targetsFlow.value.find { it.id == "PQ84P01" }
            assertEquals("SM8750 Extreme Edition", saved?.socPlatform)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testApplyNavigationIntentEdit() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val (viewModel, _) = createTestFixture()
            val job = backgroundScope.launch(testDispatcher) { viewModel.uiState.collect() }
            testScheduler.advanceUntilIdle()

            // Navigate to edit secondary target NX789J on hardware tab
            viewModel.applyNavigationIntent(
                targetId = "NX789J",
                mode = ConfigurationNavMode.EDIT,
                tab = ConfigurationTab.HARDWARE,
            )
            testScheduler.advanceUntilIdle()

            assertEquals("NX789J", viewModel.uiState.value.editingTarget.id)
            assertEquals(ConfigurationTab.HARDWARE, viewModel.uiState.value.activeTab)
            assertFalse(viewModel.uiState.value.isDirty)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testApplyNavigationIntentCreate() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val (viewModel, _) = createTestFixture()
            val job = backgroundScope.launch(testDispatcher) { viewModel.uiState.collect() }
            testScheduler.advanceUntilIdle()

            // Navigate to create mode
            viewModel.applyNavigationIntent(
                targetId = null,
                mode = ConfigurationNavMode.CREATE,
                tab = ConfigurationTab.GENERAL,
            )
            testScheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value.editingTarget.id.startsWith("TARGET_NEW_"))
            assertTrue(viewModel.uiState.value.isDirty)
            assertEquals(ConfigurationTab.GENERAL, viewModel.uiState.value.activeTab)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testApplyNavigationIntentClone() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val (viewModel, _) = createTestFixture()
            val job = backgroundScope.launch(testDispatcher) { viewModel.uiState.collect() }
            testScheduler.advanceUntilIdle()

            // Navigate to clone PQ84P01 on partitions tab
            viewModel.applyNavigationIntent(
                targetId = "PQ84P01",
                mode = ConfigurationNavMode.CLONE,
                tab = ConfigurationTab.PARTITIONS,
            )
            testScheduler.advanceUntilIdle()

            assertEquals("PQ84P01_COPY", viewModel.uiState.value.editingTarget.id)
            assertTrue(viewModel.uiState.value.editingTarget.name.contains("(Copy)"))
            assertTrue(viewModel.uiState.value.isDirty)
            assertEquals(ConfigurationTab.PARTITIONS, viewModel.uiState.value.activeTab)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testPendingNavigationIntentResolvedWhenTargetsLoaded() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val fw = TargetFirmware(
                version = "REDMAGICOS10.5.15_NP05J_GB",
                buildId = "PQ84P01:15/AQ3A.240812.002/20260311.222527",
                isOfficial = true,
            )
            val defaultDevice = TargetDevice(
                id = "PQ84P01",
                name = "Astra",
                codename = "PQ84P01",
                availableRegions = listOf(TargetRegion.GLOBAL),
                availableFirmwares = mapOf(TargetRegion.GLOBAL to listOf(fw)),
                socPlatform = "Snapdragon 8 Elite",
                filesystemType = "erofs",
                superPartitionBytes = 17179869184L,
                dynamicPartitions = listOf("system"),
                bootPartitions = listOf("boot"),
                status = TargetStatus.QUALIFIED,
                description = "Astra",
                isDefault = true,
            )
            val delayedTarget = TargetDevice(
                id = "NX789J",
                name = "REDMAGIC 10 Pro+",
                codename = "NX789J",
                availableRegions = listOf(TargetRegion.GLOBAL),
                availableFirmwares = mapOf(TargetRegion.GLOBAL to listOf(fw)),
                socPlatform = "Snapdragon 8 Elite",
                filesystemType = "erofs",
                superPartitionBytes = 17179869184L,
                dynamicPartitions = listOf("system"),
                bootPartitions = listOf("boot"),
                status = TargetStatus.QUALIFIED,
                description = "10 Pro+",
            )

            val repo = FakeTargetRepository(listOf(defaultDevice), defaultDevice)
            val validateTarget = ValidateTargetConfigurationUseCase()
            val addTarget = AddTargetUseCase(repo)
            val updateTarget = UpdateTargetUseCase(repo)
            val selectTarget = SelectTargetUseCase(repo)
            val createScaffold = CreateTargetScaffoldUseCase()
            val duplicateTarget = DuplicateTargetUseCase()
            val deleteTarget = DeleteTargetUseCase(repo)
            val saveTarget = SaveTargetConfigurationUseCase(
                validateTargetConfigurationUseCase = validateTarget,
                addTargetUseCase = addTarget,
                updateTargetUseCase = updateTarget,
                selectTargetUseCase = selectTarget,
            )

            val viewModel = ConfigurationViewModel(
                getAvailableTargetsUseCase = GetAvailableTargetsUseCase(repo),
                getSelectedTargetUseCase = GetSelectedTargetUseCase(repo),
                selectTargetUseCase = selectTarget,
                createTargetScaffoldUseCase = createScaffold,
                duplicateTargetUseCase = duplicateTarget,
                deleteTargetUseCase = deleteTarget,
                validateTargetConfigurationUseCase = validateTarget,
                saveTargetConfigurationUseCase = saveTarget,
            )

            // Intentionally set navigation intent before the delayed target exists in repo
            viewModel.applyNavigationIntent(
                targetId = "NX789J",
                mode = ConfigurationNavMode.EDIT,
                tab = ConfigurationTab.OS_STREAMS,
            )

            val job = backgroundScope.launch(testDispatcher) { viewModel.uiState.collect() }
            testScheduler.advanceUntilIdle()

            // Now emit the delayed target
            repo.targetsFlow.value = listOf(defaultDevice, delayedTarget)
            testScheduler.advanceUntilIdle()

            // It should have resolved the pending intent now
            assertEquals("NX789J", viewModel.uiState.value.editingTarget.id)
            assertEquals(ConfigurationTab.OS_STREAMS, viewModel.uiState.value.activeTab)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testDeleteTargetRemovesFromRepository() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val (viewModel, repo) = createTestFixture()
            val job = backgroundScope.launch(testDispatcher) { viewModel.uiState.collect() }
            testScheduler.advanceUntilIdle()

            assertEquals(2, repo.targetsFlow.value.size)
            viewModel.onDeleteTarget("NX789J")
            testScheduler.advanceUntilIdle()

            assertEquals(1, repo.targetsFlow.value.size)
            assertEquals("PQ84P01", repo.targetsFlow.value.first().id)
            assertEquals("Target deleted successfully", viewModel.uiState.value.statusMessage)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
