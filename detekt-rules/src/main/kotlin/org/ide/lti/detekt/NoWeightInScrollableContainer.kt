/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.RuleName
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtCallExpression

/**
 * Flags a direct child of a `Row`/`Column` that applies `Modifier.weight(...)` when that same
 * `Row`/`Column` applies `Modifier.horizontalScroll(...)`/`verticalScroll(...)` on itself.
 *
 * `weight()` requires its parent to hand down a bounded constraint along that axis; a scrollable
 * container deliberately measures its direct children with an UNBOUNDED constraint along the
 * scroll axis, so the combination throws at runtime (`IllegalStateException`: vertically/
 * horizontally scrollable ... was measured with an unbound ... "). This is not hypothetical: it
 * was hit and self-corrected once already in `GlassStepper.kt` this session
 * (`Modifier.weight(1f, fill = false).widthIn(...)` combined with a parent
 * `.horizontalScroll(rememberScrollState())`).
 *
 * A nested `Row`/`Column`/`Box` re-bounds its own children, so `weight()` used inside one of
 * those (rather than directly inside the scrollable container) is safe and not flagged.
 *
 * Overrides `visitCallExpression` directly on this Rule (a KtTreeVisitorVoid) rather than
 * delegating to a nested `KtVisitorVoid` - the latter does NOT recurse into child PSI nodes and
 * would silently never fire on any nested call expression.
 *
 * IMPORTANT: modifier/scroll detection must only look at each call's own PARENTHESIZED argument
 * list (`valueArgumentList`), never `valueArguments` - the latter also includes the trailing
 * content lambda as a `ValueArgument`, whose `.text` is the ENTIRE nested subtree's source. Using
 * `valueArguments` directly makes every ancestor of a real match falsely "contain" that match too.
 */
class NoWeightInScrollableContainer(config: Config = Config.empty) : Rule(
    config,
    description = "Modifier.weight(...) on a direct child of a Row/Column that itself applies " +
        "horizontalScroll/verticalScroll throws at runtime - weight() needs a bounded parent " +
        "constraint, which a scrollable container deliberately does not provide along its " +
        "scroll axis. Remove weight() (use widthIn/heightIn instead) or move it inside a " +
        "nested Row/Column/Box that re-bounds its own children.",
) {
    override val ruleName: RuleName get() = RuleName("NoWeightInScrollableContainer")

    private val scrollModifierNeedles = listOf(".horizontalScroll(", ".verticalScroll(")
    private val layoutBoundaryCallees = setOf(
        "Row", "Column", "Box",
        "LazyRow", "LazyColumn", "LazyVerticalGrid", "LazyHorizontalGrid",
        "FlowRow", "FlowColumn",
    )

    /** Only this call's own parenthesized arguments - excludes the trailing content lambda. */
    private fun KtCallExpression.ownArgumentContains(needle: String): Boolean =
        (valueArgumentList?.arguments ?: emptyList())
            .any { it.getArgumentExpression()?.text?.contains(needle) == true }

    private fun KtCallExpression.isScrollableContainer(): Boolean {
        val calleeName = calleeExpression?.text
        if (calleeName != "Row" && calleeName != "Column") return false
        return scrollModifierNeedles.any { ownArgumentContains(it) }
    }

    private fun collectWeightedDirectChildren(root: PsiElement, sink: MutableList<KtCallExpression>) {
        for (child in root.children) {
            if (child is KtCallExpression) {
                if (child.ownArgumentContains(".weight(")) {
                    sink.add(child)
                }
                val calleeName = child.calleeExpression?.text
                if (calleeName != null && calleeName in layoutBoundaryCallees) {
                    // Nested layout container re-bounds constraints for its own children - a
                    // weight() further inside it is safe, so don't descend into its own body.
                    continue
                }
            }
            collectWeightedDirectChildren(child, sink)
        }
    }

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (!expression.isScrollableContainer()) return

        val bodies = expression.lambdaArguments.mapNotNull { it.getLambdaExpression()?.bodyExpression }
        val violations = mutableListOf<KtCallExpression>()
        bodies.forEach { collectWeightedDirectChildren(it, violations) }

        violations.forEach { violatingCall ->
            report(
                Finding(
                    entity = Entity.from(violatingCall),
                    message = "${violatingCall.calleeExpression?.text ?: "this child"} uses " +
                        "Modifier.weight(...) directly inside a scrollable Row/Column - " +
                        "this throws at runtime. Use widthIn/heightIn instead, or move it " +
                        "inside a nested Row/Column/Box.",
                ),
            )
        }
    }
}
