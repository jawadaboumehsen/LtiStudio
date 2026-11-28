package org.ide.lti.core.domain.pipeline

import org.ide.lti.core.domain.pipeline.stages.hasAppliedModules
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppliedModulesReceiptTest {
    @Test
    fun appliedReceiptRemainsPatchedRegardlessOfAnOlderAssemblyStamp() {
        assertTrue(hasAppliedModules("""{"enabled":["io.ltirom:hma"],"operations":42}"""))
    }

    @Test
    fun freshAssemblyAndNoReceiptAreClean() {
        assertFalse(hasAppliedModules(null))
        assertFalse(hasAppliedModules("""{"enabled":[],"operations":0}"""))
        assertFalse(hasAppliedModules("""{ "operations" : 0, "enabled": [] }"""))
    }

    @Test
    fun unknownOrDamagedReceiptsCannotAuthorizeASecondPatch() {
        listOf("", "{", "{}", "[]", """{"operations":null}""", """{"operations":-1}""")
            .forEach { assertTrue(hasAppliedModules(it), it) }
    }

    @Test
    fun zeroTextInsideAnotherFieldDoesNotHideAppliedOperations() {
        assertTrue(hasAppliedModules("""{"operations":42,"note":{"operations":0}}"""))
    }
}
