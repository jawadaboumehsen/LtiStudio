/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.plugin

import org.ide.lti.core.model.plugin.Compatibility
import org.ide.lti.core.model.plugin.DependencyRef
import org.ide.lti.core.model.plugin.Operation
import org.ide.lti.core.model.plugin.PartitionPath
import org.ide.lti.core.model.plugin.PayloadEntry
import org.ide.lti.core.model.plugin.PluginPlanTemplate
import org.ide.lti.core.model.plugin.SettingDefinition
import org.ide.lti.core.model.plugin.SmaliEdit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModUpdateReviewTest {
    @Test
    fun changedPatchContentsAreVisibleWhenOperationCountsStayTheSame() {
        val edit = SmaliEdit("smali/io/example/Hook.smali", "", "- old\n+ before\n")
        val old = template(
            "1.0",
            emptyList(),
            listOf(Operation.ApkJarPatch("framework", framework, listOf(edit))),
            emptyList(),
        )
        val new = old.copy(
            version = "1.1",
            operations = listOf(
                Operation.ApkJarPatch("framework", framework, listOf(edit.copy(unifiedDiff = "- old\n+ after\n"))),
            ),
        )
        val review = ModUpdateReview.of("io:pif", "1.0", "1.1", old, new, null, null)
        assertFalse(review.unchanged)
        assertTrue(review.sections.getValue(ModUpdateReview.FILES).single().contains("patch contents"))
    }

    @Test
    fun changedAssetDigestsAreVisibleEvenWhenPathsAndSizesStayTheSame() {
        val same = template("1.0", emptyList(), emptyList(), emptyList())
        val old = pkg("1.0", emptyList()).copy(
            payloads = listOf(PayloadEntry("assets/tool", 10, "old"), PayloadEntry("assets/gone", 1, "gone")),
        )
        val new = pkg("1.1", emptyList()).copy(
            payloads = listOf(PayloadEntry("assets/tool", 10, "new"), PayloadEntry("assets/added", 2, "added")),
        )
        val review = ModUpdateReview.of("io:pif", "1.0", "1.1", same, same, old, new)
        assertFalse(review.unchanged)
        assertEquals(
            listOf(
                "new packaged file assets/added (2 bytes)",
                "removed packaged file assets/gone",
                "changed assets/tool (10 → 10 bytes)",
            ),
            review.sections.getValue(ModUpdateReview.PAYLOADS),
        )
    }

    private val services = PartitionPath("system", "system/framework/services.jar")
    private val framework = PartitionPath("system", "system/framework/framework.jar")
    private val prop = PartitionPath("system", "system/build.prop")

    private fun template(
        version: String,
        settings: List<SettingDefinition>,
        ops: List<Operation>,
        compat: List<Compatibility>,
    ) = PluginPlanTemplate(
        publisher = "io",
        id = "pif",
        version = version,
        settings = settings,
        operations = ops,
        compatibility = compat,
    )

    private fun pkg(version: String, deps: List<DependencyRef>) = InstalledPackage(
        "io",
        "pif",
        version,
        "d",
        deps,
        emptyList(),
    )

    @Test
    fun eachSectionNamesWhatTheNewVersionChanges() {
        val old = template(
            "1.0",
            listOf(
                SettingDefinition.BooleanSetting("spoof", "Spoof", default = true),
                SettingDefinition.EnumSetting("model", "Model", default = "a", options = listOf("a", "b")),
                SettingDefinition.TextSetting("note", "Note", default = ""),
            ),
            listOf(Operation.ApkJarPatch("k", services, emptyList()), Operation.Delete("d", prop)),
            listOf(Compatibility("PQ84P01", listOf("A")), Compatibility("PQ82A01")),
        )
        val new = template(
            "1.1",
            listOf(
                SettingDefinition.BooleanSetting("spoof", "Spoof", default = true),
                SettingDefinition.EnumSetting("model", "Model", default = "b", options = listOf("a", "b", "c")),
                SettingDefinition.IntSetting("level", "Level", default = 2, min = 0, max = 5),
            ),
            listOf(
                Operation.ApkJarPatch("k", services, emptyList()),
                Operation.ApkJarPatch("k2", services, emptyList()),
                Operation.ApkJarPatch("f", framework, emptyList()),
            ),
            listOf(Compatibility("PQ84P01", listOf("A", "B"))),
        )
        val review = ModUpdateReview.of(
            "io:pif",
            "1.0",
            "1.1",
            old,
            new,
            pkg("1.0", emptyList()),
            pkg("1.1", listOf(DependencyRef("io", "base", ">=2.0"))),
        )

        assertEquals(
            listOf(
                "new: Level (number 0-5, default 2)",
                "Model: one of a/b, default a → one of a/b/c, default b",
                "gone: Note (its value is no longer used)",
            ),
            review.sections.getValue(ModUpdateReview.OPTIONS),
        )
        assertEquals(
            listOf(
                "no longer edits system/system/build.prop (was 1 delete)",
                "now edits system/system/framework/framework.jar (1 APK/JAR edit)",
                "system/system/framework/services.jar: 1 APK/JAR edit → 2 APK/JAR edit",
            ),
            review.sections.getValue(ModUpdateReview.FILES),
        )
        assertEquals(
            listOf("no longer for PQ82A01", "PQ84P01 firmwares: A → A, B"),
            review.sections.getValue(ModUpdateReview.DEVICES),
        )
        assertEquals(listOf("now needs io:base >=2.0"), review.sections.getValue(ModUpdateReview.DEPENDENCIES))
    }

    @Test
    fun aRebuildOfTheSameModChangesNothingAndAnUnreadablePlanIsSaid() {
        val same = template("1.0", emptyList(), listOf(Operation.Delete("d", prop)), emptyList())
        assertTrue(ModUpdateReview.of("io:pif", "1.0", "1.0.1", same, same, null, null).unchanged)
        val unread = ModUpdateReview.of("io:pif", "1.0", "1.1", null, same, null, null)
        assertEquals(listOf("The plan of 1.0 could not be read"), unread.sections.getValue(ModUpdateReview.OPTIONS))
    }
}
