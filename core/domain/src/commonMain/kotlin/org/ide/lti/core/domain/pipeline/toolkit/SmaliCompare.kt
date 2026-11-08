/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.toolkit

/**
 * Two decoded packages compared class by class and method by method (smali from `apktool d -r --no-debug-info`).
 *
 * A class is keyed by its name, not by its `smali_classesN` folder: a recompile may place it in another dex file,
 * which is no change to the class. A method whose body differs only in the compiler's numbering (labels, registers,
 * the index of a synthetic lambda or anonymous class: a consistent renaming, nothing else) is reported apart as
 * [MethodDifference.renumberedOnly], so a recompiled framework does not bury the real changes.
 */
public object SmaliCompare {
    /** The lines of a class outside its methods (header, super, interfaces, annotations, fields), as one entry. */
    public const val CLASS_LEVEL: String = "(class header and fields)"

    // Only baksmali's own label names: a bare `:x` is also a field type (`->f:I`).
    private val LABEL = Regex(
        ":(?:cond|goto|try_start|try_end|catchall|catch|pswitch_data|pswitch|sswitch_data|sswitch|array)" +
            """_[0-9a-f]+\b""",
    )

    // A register is an operand: after a space, `{` or `,`, never inside a name (`Lcom/v1/A;`) or a string.
    private val V_REGISTER = Regex("""(?<![^\s{,])v\d+(?![\w/;$"])""")
    private val P_REGISTER = Regex("""(?<![^\s{,])p\d+(?![\w/;$"])""")
    private val SYNTHETIC = Regex("""[$][$]ExternalSynthetic[A-Za-z]+\d+|[$][$]Lambda[$]\d+|[$]\d+(?=[;$])""")

    /** `smali_classes2/com/a/B.smali` -> `com/a/B`; the class name a smali file holds. */
    public fun className(file: String): String = file.substringAfter('/').removeSuffix(".smali")

    /** The dex a smali folder came from: `smali` -> `classes.dex`, `smali_classes3` -> `classes3.dex`. */
    public fun dexOf(file: String): String = "classes${file.substringBefore('/').removePrefix("smali_classes")
        .removePrefix("smali")}.dex"

    /**
     * Method signature -> its `.method` line and body; [CLASS_LEVEL] holds every line outside the methods. The
     * header is part of the body: an access flag changed on it (private -> public) is a modified method.
     */
    public fun methodsOf(smali: String): Map<String, String> {
        val methods = mutableMapOf<String, String>()
        val classLevel = StringBuilder()
        var signature: String? = null
        val body = StringBuilder()
        for (line in smali.lines()) {
            when {
                line.startsWith(".method") -> {
                    signature = line.substringAfterLast(' ')
                    body.clear().append(line).append('\n')
                }

                line.startsWith(".end method") -> signature?.let { methods[it] = body.toString() }
                    .also { signature = null }

                signature != null -> body.append(line).append('\n')

                // Blank lines between methods are layout: a method added is no change to the class header.
                line.isNotBlank() -> classLevel.append(line).append('\n')
            }
        }
        methods[CLASS_LEVEL] = classLevel.toString()
        return methods
    }

    /**
     * The instructions of a method body: lines that are not blank, a directive (`.`), a label (`:`) or a comment, and
     * not inside an annotation or a switch/array data block (their values are data, not code).
     */
    public fun instructionCount(body: String): Int {
        var inBlock = false
        return body.lineSequence().map(String::trim).count { line ->
            when {
                DATA_BLOCKS.any { line.startsWith(".$it") } -> inBlock = true
                DATA_BLOCKS.any { line.startsWith(".end $it") } -> inBlock = false
            }
            !inBlock && line.isNotEmpty() && line[0] != '.' && line[0] != ':' && line[0] != '#'
        }
    }

    private val DATA_BLOCKS = listOf("annotation", "subannotation", "array-data", "packed-switch", "sparse-switch")

    /**
     * [body] with the compiler's numbering replaced by the order of first use: labels, v and p registers, synthetic
     * class indices; `.locals`/`.registers` dropped (they follow the registers). Two bodies with the same canonical
     * form differ by a consistent renaming only.
     */
    public fun canonical(body: String): String {
        val maps = List(NAMESPACES.size) { mutableMapOf<String, String>() }
        return body.lineSequence().map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith(".locals") && !it.startsWith(".registers") }
            .joinToString("\n") { line ->
                // A string literal is content, never numbering: only the part before its quote is renamed.
                val quote = line.indexOf('"').takeIf { it >= 0 } ?: line.length
                val code = NAMESPACES.foldIndexed(line.substring(0, quote)) { i, text, (pattern, prefix) ->
                    pattern.replace(text) { m -> maps[i].getOrPut(m.value) { "$prefix${maps[i].size}" } }
                }
                code + line.substring(quote)
            }
    }

    private val NAMESPACES = listOf(LABEL to ":L", V_REGISTER to "v", P_REGISTER to "p", SYNTHETIC to "#")

    /**
     * [left] and [right] (smali file -> text, files under their `smali*` folder) compared: classes only on one side
     * (`removed com/a/B` / `added com/a/B`) and each method added, removed or modified, with both instruction counts.
     */
    public fun compare(left: Map<String, String>, right: Map<String, String>): CodePackageDiff =
        CodePackageDiff(classChanges(left.keys, right.keys), methodChanges(left, right))

    /** Classes only on one side, by name (`removed com/a/B`, `added com/a/B`), from the smali files of each. */
    public fun classChanges(left: Set<String>, right: Set<String>): List<String> {
        val l = left.map(::className).toSet()
        val r = right.map(::className).toSet()
        return ((l - r).map { "removed $it" } + (r - l).map { "added $it" }).sorted()
    }

    /** The methods added, removed or modified in the classes both sides have and whose text differs. */
    public fun methodChanges(left: Map<String, String>, right: Map<String, String>): List<MethodDifference> {
        val l = left.mapKeys { className(it.key) }
        val r = right.mapKeys { className(it.key) }
        return l.keys.intersect(r.keys).filter { l[it] != r[it] }.sorted().flatMap { cls ->
            methodChanges(cls, methodsOf(l.getValue(cls)), methodsOf(r.getValue(cls)))
        }
    }

    private fun methodChanges(cls: String, l: Map<String, String>, r: Map<String, String>): List<MethodDifference> =
        (l.keys - r.keys).map { MethodDifference(cls, it, MethodChangeKind.Removed, instructionsA = count(l, it)) } +
            (r.keys - l.keys).map { MethodDifference(cls, it, MethodChangeKind.Added, instructionsB = count(r, it)) } +
            l.keys.intersect(r.keys).filter { l[it] != r[it] }.map { m ->
                val a = l.getValue(m)
                val b = r.getValue(m)
                MethodDifference(
                    className = cls,
                    methodName = m,
                    changeKind = MethodChangeKind.Modified,
                    lines = LineDiff.diff(a.lines(), b.lines()).orEmpty(),
                    instructionsA = count(l, m),
                    instructionsB = count(r, m),
                    renumberedOnly = m != CLASS_LEVEL && canonical(a) == canonical(b),
                )
            }

    private fun count(methods: Map<String, String>, m: String): Int? =
        if (m == CLASS_LEVEL) null else instructionCount(methods.getValue(m))
}
