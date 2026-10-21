package org.ide.lti.core.domain.plugin.edit

import org.ide.lti.core.domain.plugin.ComponentPatchCodes
import org.ide.lti.core.model.plugin.XmlChange
import org.ide.lti.core.model.plugin.XmlChangeKind
import org.ide.lti.core.model.plugin.XmlMatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class XmlResourceEditorTest {
    private val values = """
        <?xml version="1.0" encoding="utf-8" standalone="no"?>
        <resources>
            <string name="a">A</string>
            <public type="string" name="a" id="0x7f150001" />
        </resources>
    """.trimIndent() + "\n"

    private val screen = """
        <?xml version="1.0" encoding="utf-8" standalone="no"?>
        <PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android">
            <Preference android:key="featured" android:order="1" />
            <Preference android:key="system">
                <intent android:action="android.settings.SYSTEM_UPDATE_SETTINGS" />
            </Preference>
        </PreferenceScreen>
    """.trimIndent() + "\n"

    private fun change(kind: XmlChangeKind, match: XmlMatch?, fragment: String = "") = XmlChange(kind, match, fragment)

    private fun edit(xml: String, vararg changes: XmlChange) = XmlResourceEditor.apply(xml, changes.toList(), "res.xml")

    private fun changed(xml: String, vararg changes: XmlChange) =
        assertIs<EditResult.Changed>(edit(xml, *changes)).content

    private fun failed(xml: String, vararg changes: XmlChange) = assertIs<EditResult.Failed>(edit(xml, *changes))

    @Test
    fun resourcesAppendedToTheRootLineUpWithTheOthers() {
        val added = """<string name="b">B</string><color name="c">#fff</color>"""
        val out = changed(values, change(XmlChangeKind.APPEND, null, added))
        val tail = "/>\n    <string name=\"b\">B</string>\n    <color name=\"c\">#fff</color>\n</resources>"
        assertTrue(tail in out, out)
        assertTrue(out.startsWith("<?xml") && "<string name=\"a\">A</string>" in out, out)
    }

    @Test
    fun aResourceThatIsAlreadyDeclaredOrAPublicIdThatIsTakenIsRefused() {
        val sameName = failed(values, change(XmlChangeKind.APPEND, null, "<string name=\"a\">X</string>"))
        assertEquals(ComponentPatchCodes.PATCH_CONTEXT_AMBIGUOUS, sameName.code)

        val takenId = failed(
            values,
            change(XmlChangeKind.APPEND, null, """<public type="string" name="b" id="0x7F150001" />"""),
        )
        assertTrue("0x7f150001" in takenId.message, takenId.message)
    }

    @Test
    fun elementsArePickedByTagAndAttributesWhereverTheyAre() {
        val featured = XmlMatch(attributes = mapOf("android:key" to "featured"))
        val update = XmlMatch("intent", mapOf("android:action" to "android.settings.SYSTEM_UPDATE_SETTINGS"))
        val out = changed(
            screen,
            change(XmlChangeKind.INSERT_AFTER, featured, "<Preference android:key=\"lti\" />"),
            change(XmlChangeKind.REPLACE, update, "<intent android:targetPackage=\"com.zte.zdm\" />"),
        )
        val inserted = "<Preference android:key=\"featured\" android:order=\"1\"/>\n" +
            "    <Preference android:key=\"lti\"/>\n"
        assertTrue(inserted in out, out)
        assertTrue("<intent android:targetPackage=\"com.zte.zdm\"/>" in out && "SYSTEM_UPDATE_SETTINGS" !in out, out)
    }

    @Test
    fun aMissingOrRepeatedElementIsRefused() {
        val none = failed(screen, change(XmlChangeKind.REMOVE, XmlMatch("intent", mapOf("x" to "y"))))
        assertEquals(ComponentPatchCodes.PATCH_CONTEXT_NOT_FOUND, none.code)

        val two = failed(screen, change(XmlChangeKind.REMOVE, XmlMatch("Preference")))
        assertEquals(ComponentPatchCodes.PATCH_CONTEXT_AMBIGUOUS, two.code)
    }

    @Test
    fun aDoctypeIsRefusedSoNoEntityIsEverResolved() {
        val evil = "<?xml version=\"1.0\"?><!DOCTYPE r [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><r>&x;</r>"
        failed(evil, change(XmlChangeKind.APPEND, null, "<a/>"))
    }
}
