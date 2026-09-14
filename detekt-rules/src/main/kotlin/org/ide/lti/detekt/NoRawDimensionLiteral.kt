package org.ide.lti.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.RuleName
import org.jetbrains.kotlin.psi.KtFile

class NoRawDimensionLiteral(config: Config = Config.empty) : Rule(
    config,
    description = "Raw dp literals (e.g. 16.dp, 8f.dp) bypass theme/Dimens.kt. Use tokens from theme/Dimens.kt instead.",
) {
    override val ruleName: RuleName get() = RuleName("NoRawDimensionLiteral")

    private val dimensionLiteralRegex = Regex("""\d+f?\.dp\b""")

    private fun isCommentOrImport(line: String): Boolean {
        val t = line.trim()
        return t.isEmpty() || t.startsWith("//") || t.startsWith("*") || t.startsWith("/*") || t.startsWith("import ")
    }

    private fun computeLineOffsets(text: String): List<Int> {
        val offsets = mutableListOf<Int>()
        offsets.add(0)
        for (i in 0 until text.length) {
            if (text[i] == '\n') {
                offsets.add(i + 1)
            }
        }
        return offsets
    }

    override fun visitKtFile(file: KtFile) {
        super.visitKtFile(file)
        val text = file.text
        val lines = text.lines()
        val offsets = computeLineOffsets(text)

        lines.forEachIndexed { idx, rawLine ->
            if (isCommentOrImport(rawLine)) return@forEachIndexed
            val line = rawLine.trim()
            if (dimensionLiteralRegex.containsMatchIn(line)) {
                val offset = if (idx < offsets.size) offsets[idx] else 0
                report(
                    Finding(
                        entity = Entity.from(file, offset),
                        message = "raw dp literal bypasses theme/Dimens.kt. [$line]",
                    ),
                )
            }
        }
    }
}
