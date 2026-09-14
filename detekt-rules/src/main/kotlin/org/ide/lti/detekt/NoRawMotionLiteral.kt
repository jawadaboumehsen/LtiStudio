package org.ide.lti.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.RuleName
import org.jetbrains.kotlin.psi.KtFile

class NoRawMotionLiteral(config: Config = Config.empty) : Rule(
    config,
    description = "Raw motion literals bypass theme/Motion.kt (MotionDuration, MotionSpring, MotionEasing). Use tokens from theme/Motion.kt instead.",
) {
    override val ruleName: RuleName get() = RuleName("NoRawMotionLiteral")

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
                    Finding(
                        entity = Entity.from(file, offset),
                        message = "raw durationMillis literal bypasses theme/Motion.kt (MotionDuration). [$line]",
                    ),
                )
            }
            if (tweenRegex.containsMatchIn(line)) {
                report(
                    Finding(
                        entity = Entity.from(file, offset),
                        message = "raw tween duration literal bypasses theme/Motion.kt (MotionDuration). [$line]",
                    ),
                )
            }
            if (springRegex.containsMatchIn(line)) {
                report(
                    Finding(
                        entity = Entity.from(file, offset),
                        message = "raw spring numeric literal bypasses theme/Motion.kt (MotionSpring). [$line]",
                    ),
                )
            }
            if (springParamsRegex.containsMatchIn(line)) {
                report(
                    Finding(
                        entity = Entity.from(file, offset),
                        message = "raw spring parameter literal bypasses theme/Motion.kt (MotionSpring). [$line]",
                    ),
                )
            }
            if (cubicBezierEasingRegex.containsMatchIn(line)) {
                report(
                    Finding(
                        entity = Entity.from(file, offset),
                        message = "raw CubicBezierEasing literal bypasses theme/Motion.kt (MotionEasing). [$line]",
                    ),
                )
            }
        }
    }
}
