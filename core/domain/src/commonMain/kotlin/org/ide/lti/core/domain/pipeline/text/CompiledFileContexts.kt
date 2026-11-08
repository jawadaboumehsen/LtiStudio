/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.text

/**
 * Reads a compiled `file_contexts.bin` (what `sefcontext_compile` writes, shipped by Android 7 and older and in some
 * OTA packages' `META/`) back into its text rules. Every rule keeps its regex text and context next to the compiled
 * regex, so nothing is lost; the compiled part is skipped. Formats 1 to 5, for PCRE and PCRE2 builds.
 */
public object CompiledFileContexts {
    public const val MAGIC: Long = 0xF97CFF8AL
    private const val PCRE_VERSION = 2
    private const val MODE_VERSION = 3
    private const val PREFIX_VERSION = 4
    private const val ARCH_VERSION = 5
    private const val PCRE2_MAJOR = "10."
    private const val NAME_LIMIT = 1 shl 20

    /** File type bits of `st_mode`, as the rules store them (0: any). */
    private const val S_IFMT = 0xF000
    private const val S_IFREG = 0x8000
    private const val S_IFDIR = 0x4000
    private const val S_IFLNK = 0xA000
    private const val S_IFCHR = 0x2000
    private const val S_IFBLK = 0x6000
    private const val S_IFSOCK = 0xC000
    private const val S_IFIFO = 0x1000

    public fun isCompiled(bytes: ByteArray): Boolean = bytes.size >= 4 && Reader(bytes).u32() == MAGIC

    /**
     * [bytes] as text rules, one `<regex>\t[<type>\t]<context>` per line in file order.
     * @throws IllegalArgumentException when it is not a compiled file_contexts of a known format.
     */
    public fun decode(bytes: ByteArray): String {
        val r = Reader(bytes)
        require(r.u32() == MAGIC) { "Not a compiled file_contexts (no 0xf97cff8a magic)." }
        val version = r.u32().toInt()
        require(version in 1..ARCH_VERSION) { "Compiled file_contexts format $version is not known." }
        val pcre2 = if (version >= PCRE_VERSION) r.string(r.u32().toInt()).startsWith(PCRE2_MAJOR) else false
        if (version >= ARCH_VERSION) r.skip(r.u32().toInt())
        repeat(r.u32().toInt()) { r.skip(r.u32().toInt() + 1) } // stems: each with its NUL
        val count = r.u32().toInt()
        val lines = (0 until count).map {
            val context = r.cString(r.u32().toInt())
            val regex = r.cString(r.u32().toInt())
            val mode = if (version >= MODE_VERSION) r.u32().toInt() else 0
            r.u32() // stem id
            r.u32() // has meta characters
            if (version >= PREFIX_VERSION) r.u32() // prefix length
            if (version >= PCRE_VERSION) {
                r.skip(r.u32().toInt()) // the compiled regex
                if (!pcre2) r.skip(r.u32().toInt()) // PCRE's study data
            }
            listOfNotNull(regex, typeOf(mode), context).joinToString("\t")
        }
        return lines.joinToString("\n", postfix = "\n")
    }

    private fun typeOf(mode: Int): String? = when (mode and S_IFMT) {
        0 -> null
        S_IFREG -> "--"
        S_IFDIR -> "-d"
        S_IFLNK -> "-l"
        S_IFCHR -> "-c"
        S_IFBLK -> "-b"
        S_IFSOCK -> "-s"
        S_IFIFO -> "-p"
        else -> null
    }

    private class Reader(private val b: ByteArray) {
        private var at = 0

        fun u32(): Long {
            need(4)
            val v = (0 until 4).fold(0L) { acc, i -> acc or ((b[at + i].toLong() and 0xFF) shl (8 * i)) }
            at += 4
            return v
        }

        fun skip(n: Int) {
            require(n in 0..NAME_LIMIT) { "A length of $n at byte $at is not plausible." }
            need(n)
            at += n
        }

        fun string(n: Int): String {
            skip(n)
            return b.decodeToString(at - n, at)
        }

        /** [n] bytes ending in a NUL, without it. */
        fun cString(n: Int): String = string(n).substringBefore('\u0000')

        private fun need(n: Int) = require(
            at + n <= b.size,
        ) { "The file ends at byte ${b.size}, before its last rule." }
    }
}
