package org.ide.lti.sdk.patchmod

import org.ide.lti.core.model.plugin.Operation
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
}
