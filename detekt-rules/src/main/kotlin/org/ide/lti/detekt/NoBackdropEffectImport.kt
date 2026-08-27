package org.ide.lti.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtFile

class NoBackdropEffectImport(config: Config = Config.empty) : Rule(config) {
    override val issue = Issue(
        id = "NoBackdropEffectImport",
        severity = Severity.Defect,
        description = "This module may not build glass components directly from backdrop effect " +
            "primitives - compose an existing core:designsystem/core:ui component instead.",
        debt = Debt.TWENTY_MINS,
    )

    private val forbiddenImportPrefixes = listOf(
        "import com.kashif_e.backdrop.effects.",
        "import com.kashif_e.backdrop.highlight.",
        "import com.kashif_e.backdrop.shadow.",
    )
    private val forbiddenImportExact = setOf(
        "import com.kashif_e.backdrop.drawBackdrop",
        "import com.kashif_e.backdrop.BackdropEffectScope",
    )

    // Mirrors the boundaryScanRoots check in the Gradle-script guardrail: this rule only
    // matters outside core:designsystem/core:ui/backdrop, which are allowed to use backdrop
    // effects directly to build the components everything else must go through instead.
    private val allowedPathMarkers = listOf(
        "/core/designsystem/", "/core/ui/", "/backdrop/",
        "\\core\\designsystem\\", "\\core\\ui\\", "\\backdrop\\",
    )

    override fun visitKtFile(file: KtFile) {
        super.visitKtFile(file)
        val path = file.virtualFile?.path.orEmpty()
        if (allowedPathMarkers.any { path.contains(it) }) return

        file.text.lines().forEachIndexed { idx, rawLine ->
            val line = rawLine.trim()
            val isForbidden = forbiddenImportPrefixes.any { line.startsWith(it) } ||
                forbiddenImportExact.contains(line)
            if (isForbidden) {
                report(
                    CodeSmell(
                        issue = issue,
                        entity = Entity.from(file, offset = 0),
                        message = "this module may not build glass components directly from backdrop " +
                            "effect primitives - compose an existing core:designsystem/core:ui component " +
                            "instead. [$line]",
                    ),
                )
            }
        }
    }
}
