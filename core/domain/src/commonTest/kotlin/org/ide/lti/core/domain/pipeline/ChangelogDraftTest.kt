/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline

import org.ide.lti.core.domain.pipeline.stages.ChangelogDraft
import kotlin.test.Test
import kotlin.test.assertEquals

/** fw-baseline's last build: an excerpt of its work/modules/plan.json and its work/debloat/result.json as written. */
class ChangelogDraftTest {
    private val plan = """{"lockfileEntries":[
        {"publisher":"io.ltirom","id":"adb","version":"1.4.0","contentDigest":"sha256:6b87"},
        {"publisher":"io.ltirom","id":"hma","version":"1.4.0","contentDigest":"sha256:4c53"}],
        "operationIds":["hma-framework.jar","hma-services.jar","adb-prop"],
        "operations":[
        {"id":"hma-framework.jar","kind":"ApkJarPatch","target":"system/system/framework/framework.jar"},
        {"id":"hma-services.jar","kind":"ApkJarPatch","target":"system/system/framework/services.jar"},
        {"id":"adb-prop","kind":"PropertySet","target":"system/system/build.prop"}],
        "resolvedPlanDigests":[]}"""
    private val debloat =
        """{"removals":[{"partition":"product","relativePath":"app/YouTube","sizeBytes":115306441,""" +
            """"packageId":"com.google.android.youtube","reason":"EXPLICIT_SELECTOR","sidecarEntries":[]},""" +
            """{"partition":"system","relativePath":"system/app/facebook-appmanager","sizeBytes":6426949,""" +
            """"packageId":"com.facebook.appmanager","reason":"EXPLICIT_SELECTOR","sidecarEntries":[]}],""" +
            """"kept":[],"logicalBytesFreed":121733390}"""

    @Test
    fun theDraftSaysWhatTheBuildDid() {
        assertEquals(
            """
            Mods
            - adb 1.4.0
            - hma 1.4.0

            3 change(s) to framework.jar, services.jar, build.prop

            Removed apps (116 MB freed)
            - YouTube (com.google.android.youtube)
            - facebook-appmanager (com.facebook.appmanager)
            """.trimIndent(),
            ChangelogDraft.of(plan, debloat, null),
        )
        assertEquals("", ChangelogDraft.of(planJson = null, debloatJson = null, snapshot = null))
    }
}
