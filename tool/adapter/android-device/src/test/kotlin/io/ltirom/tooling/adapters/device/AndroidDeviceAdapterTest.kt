package io.ltirom.tooling.adapters.device

import io.ltirom.tooling.adapters.adb.DevicesCommand
import io.ltirom.tooling.adapters.fastboot.DevicesCommand as FastbootDevicesCommand
import io.ltirom.tooling.core.ExecutionContext
import io.ltirom.tooling.core.ToolId
import io.ltirom.tooling.testkit.FakeToolRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class AndroidDeviceAdapterTest {

    @Test
    fun `adb devices command executes via fake repository`() = runTest {
        val fakeRepo = FakeToolRepository()
        val cmd = DevicesCommand(longListing = true)
        val ctx = ExecutionContext()

        fakeRepo.execute(cmd, ctx)
        fakeRepo.assertInvoked(ToolId.ADB, times = 1)
        fakeRepo.assertArgumentPresent(ToolId.ADB, "devices")
        fakeRepo.assertArgumentPresent(ToolId.ADB, "-l")
    }

    @Test
    fun `fastboot devices command executes via fake repository`() = runTest {
        val fakeRepo = FakeToolRepository()
        val cmd = FastbootDevicesCommand()
        val ctx = ExecutionContext()

        fakeRepo.execute(cmd, ctx)
        fakeRepo.assertInvoked(ToolId.FASTBOOT, times = 1)
        fakeRepo.assertArgumentPresent(ToolId.FASTBOOT, "devices")
    }
}
