package org.ide.lti.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.RuleName
import org.jetbrains.kotlin.psi.KtFile

class NoBooleanChromaticAberration(config: Config = Config.empty) : Rule(
    config,
    description = "lensChromaticAberration is a binary on/off in this engine, but Haze's own " +
        "guidance (docs/effects/glass.md) is to keep chromatic aberration modest (0.1-0.25 " +
        "strength) to avoid rainbow-fringe artifacts - a hardcoded `= true` can never satisfy " +
        "that. Leave it at its default `false`.",
) {
    override val ruleName: RuleName get() = RuleName("NoBooleanChromaticAberration")

    private val literalRegex = Regex("""lensChromaticAberration\s*=\s*true""")

    override fun visitKtFile(file: KtFile) {
        super.visitKtFile(file)
        // Fixed offset 0, matching NoBackdropEffectImport's proven-safe pattern - a computed
        // per-line character offset here crashed detekt 2.0.0-alpha.6's Entity.from() with a
        // "Wrong offset" IndexOutOfBounds during finding reporting, even for a correct in-range
        // offset. The line content in the message is enough to locate the violation.
        file.text.lines().forEachIndexed { idx, rawLine ->
            val line = rawLine.trim()
            if (literalRegex.containsMatchIn(line)) {
                report(
                    Finding(
                        entity = Entity.from(file, offset = 0),
                        message = "lensChromaticAberration = true hardcodes full-strength dispersion; " +
                            "Haze's guidance is to keep it modest, which this binary flag can't express. " +
                            "(line ${idx + 1}: $line)",
                    ),
                )
            }
        }
    }
}
