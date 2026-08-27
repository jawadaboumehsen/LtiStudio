package org.ide.lti.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtFile

class NoRawMotionLiteral(config: Config = Config.empty) : Rule(config) {
    override val issue = Issue(
        id = "NoRawMotionLiteral",
        severity = Severity.Defect,
        description = "Raw motion literals bypass theme/Motion.kt (MotionDuration, MotionSpring, MotionEasing). Use tokens from theme/Motion.kt instead.",
        debt = Debt.FIVE_MINS,
    )

    private val durationMillisRegex = Regex("""durationMillis\s*=\s*[0-9]""")
    private val tweenRegex = Regex("""\btween\s*\(\s*[0-9]""")
    private val springRegex = Regex("""\bspring\s*\(\s*[0-9]""")
    private val springParamsRegex = Regex("""\b(?:dampingRatio|stiffness)\s*=\s*[0-9]""")
    private val cubicBezierEasingRegex = Regex("""\bCubicBezierEasing\s*\(\s*[0-9]""")

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
            val offset = if (idx < offsets.size) offsets[idx] else 0

            if (durationMillisRegex.containsMatchIn(line)) {
                report(
                    CodeSmell(
                        issue = issue,
                        entity = Entity.from(file, offset),
                        message = "raw durationMillis literal bypasses theme/Motion.kt (MotionDuration). [$line]",
                    ),
                )
            }
            if (tweenRegex.containsMatchIn(line)) {
                report(
                    CodeSmell(
                        issue = issue,
                        entity = Entity.from(file, offset),
                        message = "raw tween duration literal bypasses theme/Motion.kt (MotionDuration). [$line]",
                    ),
                )
            }
            if (springRegex.containsMatchIn(line)) {
                report(
                    CodeSmell(
                        issue = issue,
                        entity = Entity.from(file, offset),
                        message = "raw spring numeric literal bypasses theme/Motion.kt (MotionSpring). [$line]",
                    ),
                )
            }
            if (springParamsRegex.containsMatchIn(line)) {
                report(
                    CodeSmell(
                        issue = issue,
                        entity = Entity.from(file, offset),
                        message = "raw spring parameter literal bypasses theme/Motion.kt (MotionSpring). [$line]",
                    ),
                )
            }
            if (cubicBezierEasingRegex.containsMatchIn(line)) {
                report(
                    CodeSmell(
                        issue = issue,
                        entity = Entity.from(file, offset),
                        message = "raw CubicBezierEasing literal bypasses theme/Motion.kt (MotionEasing). [$line]",
                    ),
                )
            }
        }
    }
}
