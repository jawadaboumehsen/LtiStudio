package org.ide.lti.detekt

import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider

class LtiDesignSystemRuleSetProvider : RuleSetProvider {
    override val ruleSetId: RuleSetId = RuleSetId("lti-design-system")

    override fun instance(): RuleSet = RuleSet(
        ruleSetId,
        listOf(
            ::NoRawColorLiteral,
            ::NoRawAlphaLiteral,
            ::NoRawDimensionLiteral,
            ::NoRawMotionLiteral,
            ::NoUnprotectedTextClip,
            ::NoWeightInScrollableContainer,
        ),
    )
}
