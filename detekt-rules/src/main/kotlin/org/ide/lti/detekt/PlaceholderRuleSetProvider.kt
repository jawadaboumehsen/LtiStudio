package org.ide.lti.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

class PlaceholderRuleSetProvider : RuleSetProvider {
    override val ruleSetId: String = "lti-design-system"

    override fun instance(config: Config): RuleSet = RuleSet(ruleSetId, emptyList())
}
