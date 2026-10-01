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

    override fun visitKtFile(file: KtFile) {
        super.visitKtFile(file)
        // Fixed offset 0: a computed per-line
        // character offset crashed detekt 2.0.0-alpha.6's Entity.from() with "Wrong offset" during
        // finding reporting, even for a genuinely in-range offset. The line content in the message
        // is enough to locate the violation.
        file.text.lines().forEachIndexed { idx, rawLine ->
            if (isCommentOrImport(rawLine)) return@forEachIndexed
            val line = rawLine.trim()
            if (colorLiteralRegex.containsMatchIn(line)) {
                report(
                    Finding(
                        entity = Entity.from(file, offset = 0),
                        message = "raw color literal bypasses theme/Color.kt. (line ${idx + 1}: $line)",
                    ),
                )
            }
        }
    }
}
