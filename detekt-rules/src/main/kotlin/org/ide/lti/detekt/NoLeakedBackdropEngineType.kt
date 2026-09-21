package org.ide.lti.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.RuleName
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTypeReference

/**
 * The design-system module boundary plan's Phase 1 goal ("no public design-system signature
 * contains a backdrop-engine type") verified true by inspection when the module boundary was
 * closed - [org.ide.lti.core.designsystem.theme.glass.GlassSceneState] became the only backdrop-
 * related type any public signature may expose. This rule locks that in going forward: it is PSI-
 * based (not the simpler line-scanning [NoBackdropEffectImport] uses for import statements) because
 * a text-only scan can't reliably tell a public declaration's own modifier from an unrelated
 * `private`/`internal` on a different line of a multi-line signature - exactly the shape most
 * real signatures in this codebase take.
 */
class NoLeakedBackdropEngineType(config: Config = Config.empty) : Rule(
    config,
    description = "Public design-system signatures may only expose GlassSceneState, never the " +
        "raw backdrop engine's Backdrop/LayerBackdrop types.",
) {
    override val ruleName: RuleName get() = RuleName("NoLeakedBackdropEngineType")

    private val forbiddenTypeNames = setOf("Backdrop", "LayerBackdrop")

    override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)
        if (!isEffectivelyPublic(function)) return
        checkTypeReference(function.typeReference, function)
        function.valueParameters.forEach { checkParameter(it) }
    }

    override fun visitProperty(property: KtProperty) {
        super.visitProperty(property)
        if (!isEffectivelyPublic(property)) return
        checkTypeReference(property.typeReference, property)
    }

    private fun checkParameter(parameter: KtParameter) {
        checkTypeReference(parameter.typeReference, parameter)
    }

    private fun checkTypeReference(typeReference: KtTypeReference?, declaration: KtCallableDeclaration) {
        val reference = typeReference ?: return
        val text = reference.text
        val leaked = forbiddenTypeNames.firstOrNull { name ->
            Regex("(^|[^\\w.])$name([^\\w]|$)").containsMatchIn(text)
        } ?: return
        report(
            Finding(
                entity = Entity.from(declaration),
                message = "public signature exposes the backdrop engine's `$leaked` type - use " +
                    "GlassSceneState instead. [${reference.text}]",
            ),
        )
    }

    private fun isEffectivelyPublic(declaration: KtCallableDeclaration): Boolean {
        if (declaration.hasModifier(KtTokens.PRIVATE_KEYWORD)) return false
        if (declaration.hasModifier(KtTokens.INTERNAL_KEYWORD)) return false
        if (declaration.hasModifier(KtTokens.PROTECTED_KEYWORD)) return false
        return true
    }
}
