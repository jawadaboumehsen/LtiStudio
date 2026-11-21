/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.publish

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.ide.lti.core.domain.usecase.publish.PublishPreflight
import org.ide.lti.core.domain.usecase.publish.ReleaseFiles
import org.ide.lti.core.model.workspace.PublishSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PublishSessionsTest {
    @Test
    fun pendingAndFailedRefreshesNeverKeepAnEarlierReviewReady() = runTest {
        val releaseCheck = CompletableDeferred<Unit>()
        val first = PublishSettings(localExportFolder = "/first")
        val second = PublishSettings(localExportFolder = "/second")
        val sessions = PublishSessions(null, backgroundScope) { _, settings ->
            if (settings == second) {
                releaseCheck.await()
                error("destination unavailable")
            }
            valid(settings)
        }
        val state = sessions.state("workspace")

        sessions.refresh("workspace", first)
        runCurrent()
        assertNotNull(state.value.readyReview(first))
        sessions.refresh("workspace", second)
        assertNull(state.value.review)
        runCurrent()
        assertNull(state.value.readyReview(second))

        releaseCheck.complete(Unit)
        runCurrent()
        assertNull(state.value.review)
        assertTrue(state.value.error.orEmpty().contains("destination unavailable"))
    }

    @Test
    fun anOldRefreshCannotReplaceANewerReviewEvenIfItsReadIgnoresCancellation() = runTest {
        val oldRead = CompletableDeferred<Unit>()
        val first = PublishSettings(localExportFolder = "/first")
        val second = PublishSettings(localExportFolder = "/second")
        val sessions = PublishSessions(null, backgroundScope) { _, settings ->
            if (settings == first) withContext(NonCancellable) { oldRead.await() }
            valid(settings)
        }
        val state = sessions.state("workspace")

        sessions.refresh("workspace", first)
        runCurrent()
        sessions.refresh("workspace", second)
        runCurrent()
        oldRead.complete(Unit)
        runCurrent()

        assertEquals("/second", state.value.readyReview(second)?.preflight?.destination)
        assertNull(state.value.readyReview(first))
    }

    private fun valid(settings: PublishSettings) = PublishPreflight(
        ReleaseFiles("release.zip", "hash", 3),
        emptyList(),
        settings.localExportFolder,
        null,
    )
}
