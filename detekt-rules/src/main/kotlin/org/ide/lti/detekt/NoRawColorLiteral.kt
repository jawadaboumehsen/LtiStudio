package org.ide.lti.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.RuleName
import org.jetbrains.kotlin.psi.KtFile

class NoRawColorLiteral(config: Config = Config.empty) : Rule(
    config,
    description = "Raw color literals (Color(0x...)) bypass theme/Color.kt. Use tokens from theme/Color.kt instead.",
) {
    override val ruleName: RuleName get() = RuleName("NoRawColorLiteral")

    private val colorLiteralRegex = Regex("""Color\(0x""")

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
            if (colorLiteralRegex.containsMatchIn(line)) {
                val offset = if (idx < offsets.size) offsets[idx] else 0
                report(
                    Finding(
                        entity = Entity.from(file, offset),
                        message = "raw color literal bypasses theme/Color.kt. [$line]",
                    ),
                )
            }
        }
    }
}
