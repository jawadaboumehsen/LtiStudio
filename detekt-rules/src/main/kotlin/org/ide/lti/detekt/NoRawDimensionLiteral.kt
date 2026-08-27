package org.ide.lti.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtFile

class NoRawDimensionLiteral(config: Config = Config.empty) : Rule(config) {
    override val issue = Issue(
        id = "NoRawDimensionLiteral",
        severity = Severity.Defect,
        description = "Raw dp literals (e.g. 16.dp, 8f.dp) bypass theme/Dimens.kt. Use tokens from theme/Dimens.kt instead.",
        debt = Debt.FIVE_MINS,
    )

    private val dimensionLiteralRegex = Regex("""\d+f?\.dp\b""")

    private val effectFnMarkers = listOf(
        "blur(", "lens(", "vibrancy(", "colorControls(", "exposureAdjustment(",
        "gammaAdjustment(", "opacity(", "colorFilter(",
    )

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

        var effectCallDepth = 0
        lines.forEachIndexed { idx, rawLine ->
            if (isCommentOrImport(rawLine)) return@forEachIndexed
            val line = rawLine.trim()
            val netParens = line.count { it == '(' } - line.count { it == ')' }
            val hasEffectMarker = effectFnMarkers.any { line.contains(it) }
            val inEffectContext = hasEffectMarker || effectCallDepth > 0

            if (hasEffectMarker && effectCallDepth == 0) {
                effectCallDepth = maxOf(netParens, 0)
            } else if (effectCallDepth > 0) {
                effectCallDepth = maxOf(effectCallDepth + netParens, 0)
            }

            if (dimensionLiteralRegex.containsMatchIn(line) && !inEffectContext) {
                val offset = if (idx < offsets.size) offsets[idx] else 0
                report(
                    CodeSmell(
                        issue = issue,
                        entity = Entity.from(file, offset),
                        message = "raw dp literal bypasses theme/Dimens.kt. [$line]",
                    ),
                )
            }
        }
    }
}
