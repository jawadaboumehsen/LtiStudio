/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.plugin.edit

import org.ide.lti.core.model.plugin.MethodMatch
import org.ide.lti.core.model.plugin.XmlChange
import org.ide.lti.core.model.plugin.XmlChangeKind
import org.ide.lti.core.model.plugin.XmlMatch
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertIs

/** Edits that cannot do what they say are refused, never reported as changed. */
class EditRefusalsTest {
    @Test
    fun overlappingTextContextsAreAmbiguous() {
        assertIs<EditResult.Failed>(FileEdits.textPatch("aaa", "aa", "b", "", "", "file"))
    }

    @Test
    fun aPropertyKeyCannotBeAComment() {
        assertIs<EditResult.Failed>(FileEdits.propertyPatch("x=1\n", "#ignored", "2"))
        assertIs<EditResult.Failed>(FileEdits.propertyPatch("x=1\n", "two words", "2"))
    }

    @Test
    fun aPropertyValueCannotBeAnotherProperty() {
        assertIs<EditResult.Failed>(FileEdits.propertyPatch("x=1\n", "x", "2\nother=3"))
    }

    @Test
    fun oneAppendCannotDeclareAResourceTwice() {
        val twice = "<string name=\"same\">a</string><string name=\"same\">b</string>"
        val changes = listOf(XmlChange(XmlChangeKind.APPEND, fragment = twice))
        assertIs<EditResult.Failed>(XmlResourceEditor.apply("<resources/>", changes, "strings.xml"))
    }

    @Test
    fun aResourceInsertedOrReplacedIsCheckedLikeAnAppendedOne() {
        val xml = "<resources><string name=\"a\">1</string><string name=\"b\">2</string></resources>"
        val onB = XmlMatch("string", mapOf("name" to "b"))

        val clash = XmlChange(XmlChangeKind.INSERT_BEFORE, match = onB, fragment = "<string name=\"a\">3</string>")
        assertIs<EditResult.Failed>(XmlResourceEditor.apply(xml, listOf(clash), "strings.xml"))

        // Replacing a resource by one of its own name is not a clash.
        val same = XmlChange(XmlChangeKind.REPLACE, match = onB, fragment = "<string name=\"b\">3</string>")
        val replaced = assertIs<EditResult.Changed>(XmlResourceEditor.apply(xml, listOf(same), "strings.xml"))
        assertContains(replaced.content, ">3<")
    }

    @Test
    fun aDiffLineMustBeAWholeLine() {
        val diff = "@@ -1 +1 @@\n-foo\n+bar\n"
        assertIs<EditResult.Failed>(SmaliTextEditor.apply(".field public foobar:I\n", diff, "X.smali"))
        assertIs<EditResult.Changed>(SmaliTextEditor.apply("foo\n", diff, "X.smali"))
    }

    @Test
    fun anEmptyMethodDiffIsNotAChange() {
        val text = ".class public LX;\n.super Ljava/lang/Object;\n.method public f()V\n .locals 0\n return-void\n" +
            ".end method\n"
        assertIs<EditResult.Failed>(SmaliMethodEditor.apply(text, MethodMatch(name = "f"), "", "LX;"))
    }
}
