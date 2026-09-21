package org.ide.lti.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.RuleName
import org.jetbrains.kotlin.psi.KtFile

class NoBackdropEffectImport(config: Config = Config.empty) : Rule(
    config,
    description = "This module may not build glass components directly from backdrop effect " +
        "primitives - compose an existing core:designsystem/core:ui component instead.",
) {
    override val ruleName: RuleName get() = RuleName("NoBackdropEffectImport")

    private val forbiddenImportPrefixes = listOf(
        "import com.kashif_e.backdrop.effects.",
        "import com.kashif_e.backdrop.highlight.",
        "import com.kashif_e.backdrop.shadow.",
        // backdrops.* covers LayerBackdrop, rememberLayerBackdrop, layerBackdrop,
        // rememberCombinedBackdrop, rememberBackdrop - the engine's scene-capture API. Outside
        // core:designsystem/core:ui/backdrop, a GlassSceneState (core:designsystem's opaque
        // wrapper, see theme/glass/GlassSceneState.kt) is the only backdrop-related type to hold.
        "import com.kashif_e.backdrop.backdrops.",
    )
    private val forbiddenImportExact = setOf(
        "import com.kashif_e.backdrop.drawBackdrop",
        "import com.kashif_e.backdrop.BackdropEffectScope",
        "import com.kashif_e.backdrop.Backdrop",
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
                    Finding(
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
