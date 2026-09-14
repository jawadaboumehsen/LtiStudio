package org.ide.lti.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

class LtiDesignSystemRuleSetProvider : RuleSetProvider {
    override val ruleSetId: String = "lti-design-system"

    override fun instance(config: Config): RuleSet = RuleSet(
        ruleSetId,
        listOf(
            NoRawColorLiteral(config),
            NoRawAlphaLiteral(config),
            NoRawDimensionLiteral(config),
            NoRawMotionLiteral(config),
            NoBackdropEffectImport(config),
            NoUnprotectedTextClip(config),
        ),
    )
}
