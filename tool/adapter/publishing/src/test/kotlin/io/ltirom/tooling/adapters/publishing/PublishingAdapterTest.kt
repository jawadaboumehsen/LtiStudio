package io.ltirom.tooling.adapters.publishing

import io.ltirom.tooling.adapters.gh.AuthStatusCommand
import io.ltirom.tooling.core.ExecutionContext
import io.ltirom.tooling.core.ToolId
import io.ltirom.tooling.testkit.FakeToolRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class PublishingAdapterTest {

    @Test
    fun `gh auth status command executes via fake repository`() = runTest {
        val fakeRepo = FakeToolRepository()
        val cmd = AuthStatusCommand(showToken = true)
        val ctx = ExecutionContext()

        fakeRepo.execute(cmd, ctx)
        fakeRepo.assertInvoked(ToolId.GH, times = 1)
        fakeRepo.assertArgumentPresent(ToolId.GH, "auth")
        fakeRepo.assertArgumentPresent(ToolId.GH, "status")
        fakeRepo.assertArgumentPresent(ToolId.GH, "--show-token")
    }
}
