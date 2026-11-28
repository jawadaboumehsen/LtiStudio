/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.desktop

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.ide.lti.core.common.notification.NotificationLevel
import org.ide.lti.core.common.notification.SystemNotification
import org.ide.lti.core.common.notification.SystemNotifier
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.RunState
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.run.StageOutcome
import org.ide.lti.core.model.run.StageState
import org.ide.lti.core.model.target.AdbDeviceState
import org.ide.lti.core.model.target.ConnectedTargetDevice
import org.ide.lti.core.model.target.DeviceConnectionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BackgroundSessionAndDropTest {

    private class FakeNotifier : SystemNotifier {
        override val isSupported: Boolean = true
        val sent = mutableListOf<SystemNotification>()
        override fun notify(notification: SystemNotification) {
            sent += notification
        }
    }

    private class FakeClock(private var epochSec: Long = 10_000L) : Clock {
        override fun now(): Instant = Instant.fromEpochSeconds(epochSec++)
    }

    @Test
    fun sessionLifecycleDispatchesAlertsOsNotificationsAndCancellation() {
        val notifier = FakeNotifier()
        val manager = BackgroundSessionManager(notifier = notifier, clock = FakeClock())
        var cancelInvoked = false

        val jobId = manager.startSession(
            id = "ext_1",
            kind = BackgroundSessionKind.EXTRACTION,
            title = "Extract payload.bin",
            detail = "Running payload_dumper",
            progress = 0.25f,
            canCancel = true,
            onCancel = { cancelInvoked = true },
        )

        assertEquals(1, manager.state.value.activeCount)
        manager.updateProgress(jobId, progress = 1.5f, detail = "Extracting vendor")
        assertEquals(1.0f, manager.state.value.sessions.first().progress)

        manager.completeSession(jobId, detail = "Extracted 6 partitions")
        assertEquals(0, manager.state.value.activeCount)
        assertEquals(1, manager.state.value.unreadAlertCount)
        assertEquals(1, notifier.sent.size)
        assertEquals(NotificationLevel.Info, notifier.sent.single().level)

        val failId = manager.startSession(
            kind = BackgroundSessionKind.REPACK,
            title = "Repack super.img",
            onCancel = { cancelInvoked = true },
        )
        manager.cancelSession(failId)
        assertTrue(cancelInvoked)
        assertEquals(BackgroundSessionState.CANCELLED, manager.state.value.sessions.first { it.id == failId }.state)
    }

    @Test
    fun syncBuildRunAndConnectedDevicesEmitAlertsOnStateTransitions() {
        val notifier = FakeNotifier()
        val manager = BackgroundSessionManager(notifier = notifier, clock = FakeClock())
        val baseRun = BuildRun(
            id = "r10",
            workspaceId = "ws1",
            snapshotId = "s1",
            state = RunState.RUNNING,
            startedAt = Instant.fromEpochSeconds(1_000),
            stages = listOf(StageOutcome(StageId.FIRMWARE_EXTRACTION, StageState.RUNNING)),
        )
        manager.syncBuildRun(baseRun)
        assertEquals(1, manager.state.value.activeCount)
        assertEquals(BackgroundSessionKind.EXTRACTION, manager.state.value.sessions.single().kind)

        manager.syncBuildRun(
            baseRun.copy(
                state = RunState.FAILED,
                stages = listOf(
                    StageOutcome(
                        stageId = StageId.BUILD_FLASHABLE_ZIP,
                        state = StageState.FAILED,
                        message = "mkfs.erofs out of space",
                    ),
                ),
            ),
        )
        assertEquals(0, manager.state.value.activeCount)
        assertTrue(notifier.sent.any { it.level == NotificationLevel.Error && it.message.contains("out of space") })

        val devOnline = ConnectedTargetDevice("SER01", "Pixel 8", AdbDeviceState.ONLINE, DeviceConnectionType.USB)
        manager.syncConnectedDevices(emptyList())
        manager.syncConnectedDevices(listOf(devOnline))
        assertTrue(notifier.sent.any { it.title.contains("Pixel 8") })

        manager.syncConnectedDevices(listOf(devOnline.copy(state = AdbDeviceState.FASTBOOT)))
        assertTrue(manager.state.value.alerts.any { it.message.contains("fastboot") })
    }

    @Test
    fun dropClassifierAndCoordinatorRouteImagesApksModsAndRejectMissingWorkspace() = runTest {
        assertEquals(DropArtifactCategory.OTA_ZIP, DropFileClassifier.classify("C:\\fw\\rom-ota.zip").category)
        assertEquals(DropArtifactCategory.PAYLOAD_BIN, DropFileClassifier.classify("/tmp/payload.bin").category)
        assertEquals(DropArtifactCategory.ODIN_TAR, DropFileClassifier.classify("/fw/AP_G998B.tar.md5").category)
        assertEquals(DropArtifactCategory.PARTITION_IMAGE, DropFileClassifier.classify("/img/system.img").category)
        assertEquals(DropArtifactCategory.APK_PACKAGE, DropFileClassifier.classify("/app/Base.apk").category)
        assertEquals(
            DropArtifactCategory.MOD_BUNDLE,
            DropFileClassifier.classify("/mods/tweaks.zip", preferredStage = StageId.MODULE_APPLICATION).category,
        )

        val sessions = BackgroundSessionManager(notifier = FakeNotifier(), clock = FakeClock())
        val coordinator = WorkspaceDropCoordinator(sessions = sessions)
        coordinator.onDragEntered(listOf("C:\\tmp\\vendor.img", "C:\\tmp\\foo.tar.md5"))
        assertTrue(coordinator.state.value.isDraggingOver)
        assertEquals(2, coordinator.state.value.hoverFiles.size)

        val outcomes = coordinator.handleDroppedPaths(
            paths = listOf("/tmp/vendor.img", "/tmp/SystemUI.apk", "/tmp/patch.ltimod", "/tmp/ota.zip"),
            activeWorkspaceId = null,
        )
        assertIs<DropRouteOutcome.OpenedPartitionImage>(outcomes[0])
        assertIs<DropRouteOutcome.QueuedApkInspection>(outcomes[1])
        assertIs<DropRouteOutcome.PreparedModBundle>(outcomes[2])
        assertIs<DropRouteOutcome.Failed>(outcomes[3])
        assertEquals(3, sessions.state.value.unreadAlertCount)
    }
}
