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

import org.ide.lti.core.domain.plugin.ComponentPatchCodes
import org.ide.lti.core.model.plugin.SmaliEdit
import java.security.MessageDigest

/** What applying one edit to a member's text gave: its new text, or why it does not apply. */
sealed interface EditResult {
    /** [changed] names what changed: the member, or `Lclass;->method(…)` for a method edit. */
    data class Changed(val content: String, val changed: String) : EditResult
    data class Failed(val code: String, val message: String) : EditResult
}

/**
 * The one engine for a decoded APK/JAR member's edits, used by the build and by `patchmod checkMod` alike: a text
 * edit ([SmaliTextEditor]), a method edit ([SmaliMethodEditor]) or an XML edit ([XmlResourceEditor]). Finding and
 * reading the member is the caller's: see [classMember] for a method edit.
 */
object MemberEdits {

    /** The member a method edit's class is in, among a decoded component's [members] (any `smali*` folder). */
    fun classMember(classDescriptor: String, members: Collection<String>): EditResult {
        val rel = classDescriptor.removePrefix("L").removeSuffix(";") + ".smali"
        val paths = members.filter { it.substringBefore('/').startsWith("smali") && it.substringAfter('/') == rel }
            .distinct()
        return when (paths.size) {
            1 -> EditResult.Changed(paths.single(), classDescriptor)
            0 -> EditResult.Failed(
                ComponentPatchCodes.SMALI_MEMBER_MISSING,
                "Class $classDescriptor is not in the component",
            )
            else -> EditResult.Failed(
                ComponentPatchCodes.PATCH_CONTEXT_AMBIGUOUS,
                "Class $classDescriptor is in $paths",
            )
        }
    }

    /**
     * Applies [edit] to [existing] (the member's current text, null when it does not exist). A non-empty
     * [SmaliEdit.expectedSha256] pins that text first (an empty one for a member the edit creates).
     */
    fun apply(existing: String?, edit: SmaliEdit): EditResult {
        val name = edit.memberDescriptor
        val actual = sha256(existing.orEmpty())
        val method = edit.method
        // A missing member, or one an edit would create that exists, is reported by the editor (not as a hash).
        val pinned = existing != null && edit.expectedSha256.isNotEmpty() && edit.expectedSha256 != EMPTY_SHA256
        return when {
            pinned && !actual.equals(edit.expectedSha256, ignoreCase = true) ->
                EditResult.Failed(
                    ComponentPatchCodes.SMALI_PREIMAGE_MISMATCH,
                    "Preimage mismatch for '$name': expected ${edit.expectedSha256}, actual $actual",
                )
            method != null -> existing?.let { SmaliMethodEditor.apply(it, method, edit.unifiedDiff, name) }
                ?: missing(name)
            edit.xml.isNotEmpty() -> existing?.let { XmlResourceEditor.apply(it, edit.xml, name) } ?: missing(name)
            else -> SmaliTextEditor.apply(existing, edit.unifiedDiff, name)
        }
    }

    private fun missing(name: String) =
        EditResult.Failed(ComponentPatchCodes.SMALI_MEMBER_MISSING, "'$name' is not in the component")

    /** sha256 of nothing: the preimage of a member an edit creates. */
    private const val EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
