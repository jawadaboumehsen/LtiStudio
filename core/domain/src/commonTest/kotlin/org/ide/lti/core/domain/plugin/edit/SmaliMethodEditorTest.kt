package org.ide.lti.core.domain.plugin.edit

import org.ide.lti.core.domain.plugin.ComponentPatchCodes
import org.ide.lti.core.model.plugin.MethodMatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SmaliMethodEditorTest {
    private val cls = """
        .class public LFoo;
        .super Ljava/lang/Object;

        .method public run(I)V
            .locals 2

            .line 10
            invoke-virtual {p0, v1}, LFoo;->work(I)V

            return-void
        .end method

        .method public run(Ljava/lang/String;)V
            .locals 1

            const-string v0, "other"

            invoke-virtual {p0, v1}, LFoo;->work(I)V

            return-void
        .end method
    """.trimIndent() + "\n"

    private fun apply(match: MethodMatch, diff: String) = SmaliMethodEditor.apply(cls, match, diff, "LFoo;")

    @Test
    fun anOverloadedNameIsRefusedUntilTheParametersPickOne() {
        val diff = "@@ @@\n     invoke-virtual {p0, v1}, LFoo;->work(I)V\n+    nop"
        val ambiguous = assertIs<EditResult.Failed>(apply(MethodMatch(name = "run"), diff))
        assertEquals(ComponentPatchCodes.ANCHOR_AMBIGUOUS, ambiguous.code)

        val changed = assertIs<EditResult.Changed>(apply(MethodMatch("run", parameters = "I"), diff))
        assertEquals("LFoo;->run(I)V", changed.changed)
        // Only the picked method changes, though the other one holds the same line.
        assertEquals(1, changed.content.lines().count { it.trim() == "nop" })
        val first = changed.content.substringBefore("(Ljava/lang/String;)V")
        assertTrue("work(I)V\n    nop" in first, changed.content)
    }

    @Test
    fun theStringsAMethodLoadsPickIt() {
        val diff = "@@ @@\n+    nop\n     return-void"
        val changed = assertIs<EditResult.Changed>(apply(MethodMatch(strings = listOf("other")), diff))
        assertEquals("LFoo;->run(Ljava/lang/String;)V", changed.changed)
    }

    @Test
    fun blankAndLineDirectivesDoNotBreakAnAnchorAndReplaceDropsTheFoundLines() {
        // The anchor is written without the blank line and .line the firmware has between these two lines.
        val diff = "@@ @@\n-    .locals 2\n-    invoke-virtual {p0, v1}, LFoo;->work(I)V\n+    .locals 3"
        val changed = assertIs<EditResult.Changed>(apply(MethodMatch("run", parameters = "I"), diff))
        val method = changed.content.substringAfter(".method public run(I)V").substringBefore(".end method")
        assertEquals(listOf(".locals 3", "return-void"), method.lines().map(String::trim).filter(String::isNotEmpty))
    }

    @Test
    fun aCapturedRegisterIsWrittenAsTheOneTheFirmwareUses() {
        val diff = "@@ @@\n     invoke-virtual {@{self}, @{arg}}, LFoo;->work(I)V\n" +
            "+    invoke-static {@{arg}}, LHook;->x(I)V"
        val changed = assertIs<EditResult.Changed>(apply(MethodMatch("run", "I"), diff))
        assertTrue("invoke-static {v1}, LHook;->x(I)V" in changed.content, changed.content)
    }

    @Test
    fun aMissingAnchorOrMethodSaysWhichAndWhere() {
        val noLine = assertIs<EditResult.Failed>(apply(MethodMatch("run", "I"), "@@ @@\n nope\n+x"))
        assertEquals(ComponentPatchCodes.PATCH_CONTEXT_NOT_FOUND, noLine.code)
        assertTrue("run(I)V" in noLine.message, noLine.message)

        val noMethod = assertIs<EditResult.Failed>(apply(MethodMatch("gone"), "@@ @@\n x\n+y"))
        assertEquals(ComponentPatchCodes.ANCHOR_NOT_FOUND, noMethod.code)

        val unknown = "@@ @@\n     return-void\n+    invoke-static {@{who}}, LHook;->x(I)V"
        assertIs<EditResult.Failed>(apply(MethodMatch("run", "I"), unknown))
    }
}
