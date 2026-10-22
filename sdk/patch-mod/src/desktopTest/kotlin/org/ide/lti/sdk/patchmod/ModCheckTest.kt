package org.ide.lti.sdk.patchmod

import org.ide.lti.core.model.plugin.Operation
import org.ide.lti.core.model.plugin.PluginPlanTemplate
import org.ide.lti.core.model.plugin.ValueRef
import org.ide.lti.core.model.plugin.XmlChangeKind
import org.ide.lti.core.model.plugin.XmlMatch
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModCheckTest {
    private val services = "system/framework/services.jar"

    private fun decodedServices(): File = Files.createTempDirectory("decoded-services").toFile().apply {
        resolve("smali_classes3/com/x").mkdirs()
        resolve("smali_classes3/com/x/Y.smali").writeText(".class LY;\n.method run()V\n    return-void\n.end method\n")
        resolve("res/values").mkdirs()
        resolve("res/values/strings.xml").writeText("<resources>\n    <string name=\"a\">A</string>\n</resources>\n")
    }

    @Test
    fun aModsEditsAreAppliedInMemoryAndALaterModSeesAnEarlierOnesChanges() {
        val dir = decodedServices()
        val first = romMod("p", "first", "1.0.0") {
            operations {
                apkJarPatch("hook", Partition.SYSTEM, services) {
                    method("Lcom/x/Y;", "run") { insertBefore("    return-void", "    nop") }
                    xml("res/values/strings.xml") { append("<string name=\"b\">B</string>") }
                }
            }
        }
        val second = romMod("p", "second", "1.0.0") {
            operations {
                apkJarPatch("after-hook", Partition.SYSTEM, services) {
                    method("Lcom/x/Y;", "run") { insertAfter("    nop", "    nop") }
                    method("Lcom/x/Y;", "gone") { insertAfter("    nop", "    nop") }
                }
            }
        }

        val checks = ModCheck.check(listOf(first, second)) { if (it.relativePath == services) dir else null }

        assertEquals(
            listOf("Lcom/x/Y;->run()V", "res/values/strings.xml", "Lcom/x/Y;->run()V"),
            checks.take(3).map { it.changed },
        )
        checks.take(3).forEach { assertNull(it.failure, it.toString()) }
        assertTrue(checks[3].failure!!.contains("No method matches name gone"), checks[3].failure)
        // Nothing is written to the decoded folder.
        assertTrue("nop" !in dir.resolve("smali_classes3/com/x/Y.smali").readText())
    }

    @Test
    fun anXmlBlockBecomesStructuredChanges() {
        val plan = romMod("p", "m", "1.0.0") {
            operations {
                apkJarPatch("res", Partition.SYSTEM_EXT, "priv-app/S/S.apk") {
                    xml("res/xml/top.xml") {
                        insertAfter(element(null, "android:key" to "featured"), "<Preference android:key=\"lti\"/>")
                        remove(element("intent"))
                    }
                }
            }
        }
        val edit = (plan.operations.single() as Operation.ApkJarPatch).edits.single()
        assertEquals("res/xml/top.xml", edit.memberDescriptor)
        assertEquals(
            listOf(
                XmlChangeKind.INSERT_AFTER to XmlMatch(null, mapOf("android:key" to "featured")),
                XmlChangeKind.REMOVE to XmlMatch("intent"),
            ),
            edit.xml.map { it.kind to it.match },
        )
    }

    private val policy = "(allow a b)\n(allow c d)\n"

    private fun workTree(): File = Files.createTempDirectory("work-tree").toFile().apply {
        resolve("system/system").mkdirs()
        resolve("system/system/build.prop").writeText("ro.a=1\nro.b=2\n")
        resolve("system/etc").mkdirs()
        resolve("system/etc/policy.cil").writeText(policy)
        resolve("system/app").mkdirs()
        resolve("system/app/Old.apk").writeText("apk")
    }

    private fun checked(tree: File?, vararg mods: PluginPlanTemplate) =
        ModCheck.check(mods.toList(), tree, { _, ref -> ref.path != "assets/missing.bin" }) { null }

    @Test
    fun fileOperationsAreAppliedInMemoryInOrderAndNothingIsWritten() {
        val tree = workTree()
        val first = romMod("p", "first", "1.0.0") {
            operations {
                propertyPatch("prop", Partition.SYSTEM, "system/build.prop", "ro.a", literal("9"))
                textPatch("text", Partition.SYSTEM, "etc/policy.cil", "(allow a b)\n", literal("(allow a z)\n"), "")
                delete("del", Partition.SYSTEM, "app/Old.apk")
                copy("new", "assets/tool.bin", Partition.SYSTEM, "bin/tool")
            }
        }
        // A later mod sees the earlier one's changes: its anchor is gone, and so is the deleted file.
        val second = romMod("p", "second", "1.0.0") {
            operations {
                textPatch("again", Partition.SYSTEM, "etc/policy.cil", "(allow a b)\n", literal("x"), "")
                propertyPatch("prop2", Partition.SYSTEM, "system/build.prop", "ro.a", literal("10"))
                delete("del2", Partition.SYSTEM, "app/Old.apk")
            }
        }

        val checks = checked(tree, first, second)

        assertEquals(listOf(false, false, false, false, true, false, true), checks.map { it.failed })
        assertTrue(checks[4].failure!!.startsWith("TEXT_CONTEXT_NOT_FOUND"), checks[4].failure)
        assertTrue(checks[6].failure!!.startsWith("TARGET_MISSING"), checks[6].failure)
        assertEquals("system:etc/policy.cil", checks[1].changed)
        assertEquals("ro.a=1\nro.b=2\n", tree.resolve("system/system/build.prop").readText())
        assertEquals(policy, tree.resolve("system/etc/policy.cil").readText())
        assertTrue(tree.resolve("system/app/Old.apk").isFile && !tree.resolve("system/bin").exists())
    }

    @Test
    fun aMissingAnchorFileAssetOrPreimageIsNamed() {
        val tree = workTree()
        val mod = romMod("p", "m", "1.0.0") {
            operations {
                textPatch("ambiguous", Partition.SYSTEM, "etc/policy.cil", "(allow", literal("x"), "")
                textPatch("pinned", Partition.SYSTEM, "etc/policy.cil", "(allow a b)", literal("x"), "ab".repeat(32))
                propertyPatch("nofile", Partition.SYSTEM, "system/missing.prop", "k", literal("v"))
                propertyPatch("badkey", Partition.SYSTEM, "system/build.prop", "a=b", literal("v"))
                copy("asset", "assets/missing.bin", Partition.SYSTEM, "bin/x")
                replace("swap", "assets/tool.bin", Partition.SYSTEM, "app/Old.apk", "cd".repeat(32))
                copy("nowhere", "assets/tool.bin", Partition.VENDOR, "bin/x")
            }
        }

        val codes = checked(tree, mod).map { it.failure?.substringBefore(':') }

        assertEquals(
            listOf(
                "TEXT_CONTEXT_AMBIGUOUS",
                "PREIMAGE_MISMATCH",
                "TARGET_MISSING",
                "PROPERTY_KEY_INVALID",
                "SOURCE_ASSET_MISSING",
                "PREIMAGE_MISMATCH",
                "PARTITION_NOT_IN_TREE",
            ),
            codes,
        )
    }

    @Test
    fun twoOperationsOnOnePathOfAModConflictUnlessBothAreInPlaceEdits() {
        val tree = workTree()
        val mod = romMod("p", "m", "1.0.0") {
            operations {
                propertyPatch("p1", Partition.SYSTEM, "system/build.prop", "ro.a", literal("1"))
                propertyPatch("p2", Partition.SYSTEM, "system/build.prop", "ro.c", literal("2"))
                copy("c1", "assets/a.bin", Partition.SYSTEM, "bin/x")
                copy("c2", "assets/b.bin", Partition.SYSTEM, "bin/x")
            }
        }

        val checks = checked(tree, mod)

        assertEquals(listOf(null, null, null), checks.take(3).map { it.failure })
        assertTrue(checks[3].failure!!.startsWith("OVERLAPPING_MUTATION"), checks[3].failure)
    }

    @Test
    fun withoutAWorkTreeFileOperationsAreSkippedAndASettingStandsAsItsDefault() {
        val withSetting = romMod("p", "m", "1.0.0") {
            settings.boolean("on", "On", default = true)
            operations {
                propertyPatch("prop", Partition.SYSTEM, "system/build.prop", "ro.x", ValueRef.Setting("on"))
            }
        }
        val skipped = checked(null, withSetting).single()
        assertTrue(skipped.notChecked && !skipped.failed, skipped.toString())

        assertNull(checked(workTree(), withSetting).single().failure)
    }

    @Test
    fun aTreeWithoutThePartitionFolderIsReportedNotThrown() {
        val empty = Files.createTempDirectory("empty-tree").toFile()
        val mod = romMod("p", "m", "1.0.0") {
            operations { delete("d", Partition.SYSTEM, "app/Old.apk") }
        }
        assertTrue(checked(empty, mod).single().failure!!.startsWith("PARTITION_NOT_IN_TREE"))
    }
}
