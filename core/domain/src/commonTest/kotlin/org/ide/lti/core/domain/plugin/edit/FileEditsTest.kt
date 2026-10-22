package org.ide.lti.core.domain.plugin.edit

import org.ide.lti.core.domain.plugin.FileMutationCodes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FileEditsTest {
    private val props = "# a comment\nro.a=1\n#ro.b=old\nro.b = 2\n"

    private fun content(result: EditResult) = assertIs<EditResult.Changed>(result).content

    private fun failed(result: EditResult) = assertIs<EditResult.Failed>(result)

    @Test
    fun aPropertyIsReplacedInPlaceOrAppendedAndCommentsAreIgnored() {
        assertEquals("# a comment\nro.a=9\n#ro.b=old\nro.b = 2\n", content(FileEdits.propertyPatch(props, "ro.a", "9")))
        // The key matches with spaces around it; the commented-out copy is left alone.
        assertEquals("# a comment\nro.a=1\n#ro.b=old\nro.b=3\n", content(FileEdits.propertyPatch(props, "ro.b", "3")))
        assertEquals(props + "ro.c=4\n", content(FileEdits.propertyPatch(props, "ro.c", "4")))
        assertEquals("ro.c=4\n", content(FileEdits.propertyPatch("", "ro.c", "4")))
        assertEquals("a=1\nb=2\n", content(FileEdits.propertyPatch("a=1\r\n", "b", "2")))
    }

    @Test
    fun anInvalidPropertyKeyIsRefused() {
        listOf("", "a=b", "a\nb", "a\u0000").forEach { key ->
            assertEquals(FileMutationCodes.PROPERTY_KEY_INVALID, failed(FileEdits.propertyPatch(props, key, "v")).code)
        }
    }

    @Test
    fun aTextPatchReplacesItsOneAnchorAndNamesWhyItCannot() {
        val text = "alpha\nbeta\ngamma\nbeta2\n"
        assertEquals("alpha\nB\ngamma\nbeta2\n", content(FileEdits.textPatch(text, "beta\n", "B\n", "", "", "f")))

        val none = failed(FileEdits.textPatch(text, "delta", "x", "", "", "f"))
        assertEquals(FileMutationCodes.TEXT_CONTEXT_NOT_FOUND, none.code)
        val many = failed(FileEdits.textPatch(text, "beta", "x", "", "", "f"))
        assertEquals(FileMutationCodes.TEXT_CONTEXT_AMBIGUOUS, many.code)
        assertTrue("found 2 times" in many.message, many.message)
        // An empty anchor never matches.
        val empty = failed(FileEdits.textPatch(text, "", "x", "", "", "f"))
        assertEquals(FileMutationCodes.TEXT_CONTEXT_NOT_FOUND, empty.code)
    }

    @Test
    fun aNonEmptyPreimageHashPinsTheFileFirst() {
        fun patch(actual: String) = FileEdits.textPatch("a", "a", "b", "AA", actual, "f")
        assertEquals(FileMutationCodes.PREIMAGE_MISMATCH, failed(patch("bb")).code)
        // Case does not matter; a match goes on to the anchor.
        assertEquals("b", content(patch("aa")))
    }
}
