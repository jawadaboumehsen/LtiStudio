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

    override fun visitKtFile(file: KtFile) {
        super.visitKtFile(file)
        // Fixed offset 0: a computed per-line
        // character offset crashed detekt 2.0.0-alpha.6's Entity.from() with "Wrong offset" during
        // finding reporting, even for a genuinely in-range offset. The line content in the message
        // is enough to locate the violation.
        file.text.lines().forEachIndexed { idx, rawLine ->
            if (isCommentOrImport(rawLine)) return@forEachIndexed
            val line = rawLine.trim()
            if (dimensionLiteralRegex.containsMatchIn(line)) {
                report(
                    Finding(
                        entity = Entity.from(file, offset = 0),
                        message = "raw dp literal bypasses theme/Dimens.kt. (line ${idx + 1}: $line)",
                    ),
                )
            }
        }
    }
}
