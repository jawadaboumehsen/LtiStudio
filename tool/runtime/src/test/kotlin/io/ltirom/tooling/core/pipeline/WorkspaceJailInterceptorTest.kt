package io.ltirom.tooling.core.pipeline

import io.ltirom.tooling.core.ExecutionContext
import io.ltirom.tooling.core.ToolId
import io.ltirom.tooling.core.ToolResult
import io.ltirom.tooling.core.ToolRisk
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkspaceJailInterceptorTest {

    @Test
    fun `permissive when allowedRoots is empty`() = runTest {
        val interceptor = WorkspaceJailInterceptor(allowedRoots = emptyList())

        val mockCommand = object : io.ltirom.tooling.core.ToolCommand<String> {
            override val toolId = ToolId.ADB
            override val risk: ToolRisk = ToolRisk.READ_ONLY
            override fun getArguments() = listOf("../../outside/file")
            override fun parseResult(result: io.ltirom.tooling.core.CommandExecutionResult) = "ok"
        }

        val chain = object : ExecutionChain<String> {
            override val command = mockCommand
            override val context = ExecutionContext()
            override suspend fun proceed(
                command: io.ltirom.tooling.core.ToolCommand<String>,
                context: ExecutionContext
            ): ToolResult<String> {
                return ToolResult.Success("proceeded", 10L)
            }
        }

        val result = interceptor.intercept(chain)
        assertTrue(result is ToolResult.Success)
    }

    @Test
    fun `blocks path traversal escaping allowed root`() = runTest {
        val tempDir = createTempDirectory("jail_test").toFile()
        try {
            val interceptor = WorkspaceJailInterceptor(allowedRoots = listOf(tempDir))

            val mockCommand = object : io.ltirom.tooling.core.ToolCommand<String> {
                override val toolId = ToolId.MKE2FS
                override val risk: ToolRisk = ToolRisk.READ_ONLY
                override fun getArguments() = listOf("-o", "../../escape.img")
                override fun parseResult(result: io.ltirom.tooling.core.CommandExecutionResult) = ""
            }

            val chain = object : ExecutionChain<String> {
                override val command = mockCommand
                override val context = ExecutionContext()
                override suspend fun proceed(
                    command: io.ltirom.tooling.core.ToolCommand<String>,
                    context: ExecutionContext
                ): ToolResult<String> {
                    return ToolResult.Success("should not reach", 10L)
                }
            }

            val result = interceptor.intercept(chain)
            assertTrue(result is ToolResult.Failure.SecurityViolation)
            assertTrue((result as ToolResult.Failure.SecurityViolation).message.contains("Directory traversal escape"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `blocks absolute path outside allowed root`() = runTest {
        val jailDir = createTempDirectory("jail_root").toFile()
        val outsideDir = createTempDirectory("outside_root").toFile()
        try {
            val interceptor = WorkspaceJailInterceptor(allowedRoots = listOf(jailDir))
            val outsideFile = File(outsideDir, "target.img")

            val mockCommand = object : io.ltirom.tooling.core.ToolCommand<String> {
                override val toolId = ToolId.MKE2FS
                override val risk: ToolRisk = ToolRisk.READ_ONLY
                override fun getArguments() = listOf("--output=${outsideFile.absolutePath}")
                override fun parseResult(result: io.ltirom.tooling.core.CommandExecutionResult) = ""
            }

            val chain = object : ExecutionChain<String> {
                override val command = mockCommand
                override val context = ExecutionContext()
                override suspend fun proceed(
                    command: io.ltirom.tooling.core.ToolCommand<String>,
                    context: ExecutionContext
                ): ToolResult<String> {
                    return ToolResult.Success("should not reach", 10L)
                }
            }

            val result = interceptor.intercept(chain)
            assertTrue(result is ToolResult.Failure.SecurityViolation)
            assertTrue((result as ToolResult.Failure.SecurityViolation).message.contains("outside approved workspace roots"))
        } finally {
            jailDir.deleteRecursively()
            outsideDir.deleteRecursively()
        }
    }

    @Test
    fun `allows absolute path inside allowed root and flags`() = runTest {
        val jailDir = createTempDirectory("jail_root").toFile()
        try {
            val insideFile = File(jailDir, "valid.img")
            val interceptor = WorkspaceJailInterceptor(allowedRoots = listOf(jailDir))

            val mockCommand = object : io.ltirom.tooling.core.ToolCommand<String> {
                override val toolId = ToolId.MKE2FS
                override val risk: ToolRisk = ToolRisk.READ_ONLY
                override fun getArguments() = listOf("-v", "-t", "ext4", insideFile.absolutePath)
                override fun parseResult(result: io.ltirom.tooling.core.CommandExecutionResult) = "ok"
            }

            val chain = object : ExecutionChain<String> {
                override val command = mockCommand
                override val context = ExecutionContext()
                override suspend fun proceed(
                    command: io.ltirom.tooling.core.ToolCommand<String>,
                    context: ExecutionContext
                ): ToolResult<String> {
                    return ToolResult.Success("ok", 5L)
                }
            }

            val result = interceptor.intercept(chain)
            assertTrue(result is ToolResult.Success)
            assertEquals("ok", (result as ToolResult.Success).value)
        } finally {
            jailDir.deleteRecursively()
        }
    }
}
