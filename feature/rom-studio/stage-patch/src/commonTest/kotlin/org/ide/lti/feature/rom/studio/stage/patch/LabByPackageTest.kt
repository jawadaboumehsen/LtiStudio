/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.patch

import org.ide.lti.core.domain.plugin.LabRow
import org.ide.lti.core.domain.plugin.LabStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class LabByPackageTest {
    private val framework = "system/system/framework/framework.jar"
    private val services = "system/system/framework/services.jar"
    private val keystore = "Landroid/security/KeyStore2;->getSecurityLevel(I)Landroid/x;"

    @Test
    fun eachPackageGathersItsModsMethodsAndFilesSharedPackagesFirst() {
        val rows = listOf(
            LabRow("io:pif", "spoof", LabStatus.CHECKED, methods = listOf(keystore), files = listOf("smali/K.smali")),
            LabRow("io:hma", "hide", LabStatus.CHECKED),
            LabRow("io:keybox", "keystore", LabStatus.CHECKED, methods = listOf(keystore)),
            LabRow("io:gone", "old", LabStatus.CHECKED),
        )
        val targets = mapOf("spoof" to framework, "keystore" to framework, "hide" to services)
        val packages = labByPackage(rows) { targets[it.operationId] }

        assertEquals(listOf(framework, UNKNOWN, services), packages.map { it.target })
        val shared = packages.first()
        assertEquals(listOf("io:pif", "io:keybox"), shared.rows.map { it.mod })
        assertEquals(listOf(keystore), shared.methods)
        assertEquals(listOf("smali/K.smali"), shared.files)
    }

    @Test
    fun methodsAndFilesSurviveTheHostStepText() {
        val row = LabRow(
            "io:pif",
            "spoof",
            LabStatus.CHECKED,
            "applies",
            "=== smali/K.smali\n- a\n+ b",
            methods = listOf(keystore, "Lx/Y;->z()V"),
            files = listOf("smali/K.smali", "res/values/strings.xml"),
        )
        assertEquals(listOf(row), LabRow.decode(LabRow.encode(listOf(row))))
        // A row written before the two lists existed still reads.
        assertEquals(LabStatus.CHECKED, LabRow.decode("io:a\top\tCHECKED\tok").single().status)
    }
}
