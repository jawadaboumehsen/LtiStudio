package io.ltirom.tooling.adapters.security

import io.ltirom.tooling.adapters.zipalign.AlignCommand
import io.ltirom.tooling.core.ExecutionContext
import io.ltirom.tooling.core.ToolId
import io.ltirom.tooling.testkit.FakeToolRepository
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class SecurityAdapterTest {

    @Test
    fun `zipalign align command executes via fake repository`() = runTest {
        val fakeRepo = FakeToolRepository()
        val cmd = AlignCommand(
            alignment = 4,
            infile = File("in.apk"),
            outfile = File("out.apk"),
            overwrite = true
        )
        val ctx = ExecutionContext()

        fakeRepo.execute(cmd, ctx)
        fakeRepo.assertInvoked(ToolId.ZIPALIGN, times = 1)
        fakeRepo.assertArgumentPresent(ToolId.ZIPALIGN, "-f")
    }
}
