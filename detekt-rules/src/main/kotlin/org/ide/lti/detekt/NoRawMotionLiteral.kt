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

    override fun visitKtFile(file: KtFile) {
        super.visitKtFile(file)
        // Fixed offset 0: a computed per-line
        // character offset crashed detekt 2.0.0-alpha.6's Entity.from() with "Wrong offset" during
        // finding reporting, even for a genuinely in-range offset. The line content in the message
        // is enough to locate the violation.
        file.text.lines().forEachIndexed { idx, rawLine ->
            if (isCommentOrImport(rawLine)) return@forEachIndexed
            val line = rawLine.trim()
            val lineNumber = idx + 1

            if (durationMillisRegex.containsMatchIn(line)) {
                report(
                    Finding(
                        entity = Entity.from(file, offset = 0),
                        message = "raw durationMillis literal bypasses theme/Motion.kt (MotionDuration). (line $lineNumber: $line)",
                    ),
                )
            }
            if (tweenRegex.containsMatchIn(line)) {
                report(
                    Finding(
                        entity = Entity.from(file, offset = 0),
                        message = "raw tween duration literal bypasses theme/Motion.kt (MotionDuration). (line $lineNumber: $line)",
                    ),
                )
            }
            if (springRegex.containsMatchIn(line)) {
                report(
                    Finding(
                        entity = Entity.from(file, offset = 0),
                        message = "raw spring numeric literal bypasses theme/Motion.kt (MotionSpring). (line $lineNumber: $line)",
                    ),
                )
            }
            if (springParamsRegex.containsMatchIn(line)) {
                report(
                    Finding(
                        entity = Entity.from(file, offset = 0),
                        message = "raw spring parameter literal bypasses theme/Motion.kt (MotionSpring). (line $lineNumber: $line)",
                    ),
                )
            }
            if (cubicBezierEasingRegex.containsMatchIn(line)) {
                report(
                    Finding(
                        entity = Entity.from(file, offset = 0),
                        message = "raw CubicBezierEasing literal bypasses theme/Motion.kt (MotionEasing). (line $lineNumber: $line)",
                    ),
                )
            }
        }
    }
}
