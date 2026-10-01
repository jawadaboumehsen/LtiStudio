package io.ltirom.tooling.adapters.pkg

import io.ltirom.tooling.adapters.aapt2.VersionCommand as Aapt2VersionCommand
import io.ltirom.tooling.adapters.apktool.VersionCommand as ApktoolVersionCommand
import io.ltirom.tooling.core.ExecutionContext
import io.ltirom.tooling.core.ToolId
import io.ltirom.tooling.testkit.FakeToolRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class AndroidPackageAdapterTest {

    @Test
    fun `aapt2 version command executes via fake repository`() = runTest {
        val fakeRepo = FakeToolRepository()
        val cmd = Aapt2VersionCommand()
        val ctx = ExecutionContext()

        fakeRepo.execute(cmd, ctx)
        fakeRepo.assertInvoked(ToolId.AAPT2, times = 1)
        fakeRepo.assertArgumentPresent(ToolId.AAPT2, "version")
    }

    @Test
    fun `apktool version command executes via fake repository`() = runTest {
        val fakeRepo = FakeToolRepository()
        val cmd = ApktoolVersionCommand()
        val ctx = ExecutionContext()

        fakeRepo.execute(cmd, ctx)
        fakeRepo.assertInvoked(ToolId.APKTOOL, times = 1)
        fakeRepo.assertArgumentPresent(ToolId.APKTOOL, "v")
    }
}
