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

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtCallExpression

/**
 * Flags a `Text(...)` call that sets `softWrap = false` without also setting `overflow`.
 * Without `overflow`, Compose silently clips the text with no ellipsis, which reproduces the
 * "text vanishes/clips at narrow widths" bug class found repeatedly across Glass* components
 * (e.g. GlassConsoleDrawer's "View Output" label). Use `overflow = TextOverflow.Ellipsis` (or
 * `.Clip` if clipping is deliberate) alongside `softWrap = false`.
 *
 * Overrides `visitCallExpression` directly on this Rule (a KtTreeVisitorVoid) rather than
 * delegating to a nested `KtVisitorVoid` - the latter does NOT recurse into child PSI nodes and
 * would silently never fire on any nested call expression.
 */
class NoUnprotectedTextClip(config: Config = Config.empty) : Rule(config) {
    override val issue = Issue(
        id = "NoUnprotectedTextClip",
        severity = Severity.Defect,
        description = "Text(...) with softWrap = false must also specify overflow " +
            "(e.g. TextOverflow.Ellipsis), otherwise text silently clips with no ellipsis.",
        debt = Debt.FIVE_MINS,
    )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (expression.calleeExpression?.text != "Text") return

        val args = expression.valueArguments
        val hasSoftWrapFalse = args.any { arg ->
            arg.getArgumentName()?.asName?.asString() == "softWrap" &&
                arg.getArgumentExpression()?.text == "false"
        }
        if (!hasSoftWrapFalse) return

        val hasOverflow = args.any { arg ->
            arg.getArgumentName()?.asName?.asString() == "overflow"
        }
        if (hasOverflow) return

        report(
            CodeSmell(
                issue = issue,
                entity = Entity.from(expression),
                message = "Text(...) sets softWrap = false without overflow; " +
                    "add overflow = TextOverflow.Ellipsis (or .Clip if intentional).",
            ),
        )
    }
}
