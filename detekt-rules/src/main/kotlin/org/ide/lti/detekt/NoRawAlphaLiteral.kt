package org.ide.lti.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtFile

class NoRawAlphaLiteral(config: Config = Config.empty) : Rule(config) {
    override val issue = Issue(
        id = "NoRawAlphaLiteral",
        severity = Severity.Defect,
        description = "Raw alpha literals (alpha = 0.x) bypass theme/Alpha.kt (AlphaTokens). Use tokens from theme/Alpha.kt instead.",
        debt = Debt.FIVE_MINS,
    )

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
                    CodeSmell(
                        issue = issue,
                        entity = Entity.from(file, offset),
                        message = "raw alpha literal bypasses theme/Alpha.kt (AlphaTokens). [$line]",
                    ),
                )
            }
        }
    }
}
