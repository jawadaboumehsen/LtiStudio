package org.ide.lti.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.RuleName
import org.jetbrains.kotlin.psi.KtFile

class NoRawAlphaLiteral(config: Config = Config.empty) : Rule(
    config,
    description = "Raw alpha literals (alpha = 0.x) bypass theme/Alpha.kt (AlphaTokens). Use tokens from theme/Alpha.kt instead.",
) {
    override val ruleName: RuleName get() = RuleName("NoRawAlphaLiteral")

    private val alphaLiteralRegex = Regex("""alpha\s*=\s*0\.\d""")

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
            if (alphaLiteralRegex.containsMatchIn(line)) {
                val offset = if (idx < offsets.size) offsets[idx] else 0
                report(
                    Finding(
                        entity = Entity.from(file, offset),
                        message = "raw alpha literal bypasses theme/Alpha.kt (AlphaTokens). [$line]",
                    ),
                )
            }
        }
    }
}
