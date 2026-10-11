package org.ide.lti.desktop.realhost

import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import org.ide.lti.core.domain.setup.RecipeConfig
import org.ide.lti.core.domain.setup.ToolGroup
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.setup.ToolOutput
import org.ide.lti.core.domain.setup.ToolOutputKind
import org.ide.lti.core.domain.setup.ToolProbe
import org.ide.lti.core.domain.setup.ToolSource
import org.ide.lti.core.model.setup.ToolRef

/**
 * Fixture builder creating real git and cmake fixtures for realhost tests (T004).
 */
object Fixtures {
    private val translator = io.ltirom.tooling.client.WslPathTranslator()

    private fun toWsl(path: String): String = translator.toWslPath(path)

    suspend fun setupGitRepo(
        transport: RemoteTransportPort,
        repoPath: String,
        branch: String = "main",
        tag: String = "v1.0.0",
    ): Result<Unit> {
        val wslRepo = toWsl(repoPath)
        val script =
            """
            mkdir -p '$wslRepo'
            cd '$wslRepo'
            git init -b '$branch'
            git config user.name 'Test Runner'
            git config user.email 'test@example.com'
            echo 'initial' > README.md
            git add README.md
            git commit -m 'Initial commit'
            git tag '$tag'
            """.trimIndent()
        val req = ToolExecutionRequest(toolId = "sh", arguments = listOf("-c", script), workingDirectory = "/tmp")
        val res = transport.execute(req)
        return if (res.exitCode == 0) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("Failed to setup git repo: ${res.stderr}"))
        }
    }

    suspend fun setupAndroidToolsFixture(transport: RemoteTransportPort, repoPath: String): Result<Unit> {
        val wslRepo = toWsl(repoPath)
        val script =
            """
                        mkdir -p '$wslRepo/vendor' '$wslRepo/gki' '$wslRepo/patches'
                        cd '$wslRepo'
                        git init -b sixteen
                        git config user.name 'Test Runner'
                        git config user.email 'test@example.com'
                        cat << 'EOF' > CMakeLists.txt
                        cmake_minimum_required(VERSION 3.16)
                        project(android_tools_fixture C CXX)
                        file(WRITE "${'$'}{CMAKE_BINARY_DIR}/options.txt" "CMAKE_BUILD_TYPE=${'$'}{CMAKE_BUILD_TYPE}\nC_COMPILER=${'$'}{CMAKE_C_COMPILER}\nCXX_COMPILER=${'$'}{CMAKE_CXX_COMPILER}\nUSE_BUNDLED_FMT=${'$'}{ANDROID_TOOLS_USE_BUNDLED_FMT}\nUSE_BUNDLED_LIBUSB=${'$'}{ANDROID_TOOLS_USE_BUNDLED_LIBUSB}\nPATCH_VENDOR=${'$'}{ANDROID_TOOLS_PATCH_VENDOR}\nGIT_COMMITTER_NAME=${'$'}ENV{GIT_COMMITTER_NAME}\nGIT_COMMITTER_EMAIL=${'$'}ENV{GIT_COMMITTER_EMAIL}\nGIT_AUTHOR_NAME=${'$'}ENV{GIT_AUTHOR_NAME}\nGIT_AUTHOR_EMAIL=${'$'}ENV{GIT_AUTHOR_EMAIL}\n")
                        add_custom_target(build_all ALL
                            COMMAND ${'$'}{CMAKE_COMMAND} -E make_directory "${'$'}{CMAKE_BINARY_DIR}/vendor"
                            COMMAND ${'$'}{CMAKE_COMMAND} -E touch "${'$'}{CMAKE_BINARY_DIR}/vendor/adb"
                            COMMAND ${'$'}{CMAKE_COMMAND} -E touch "${'$'}{CMAKE_BINARY_DIR}/vendor/fastboot"
                            COMMAND ${'$'}{CMAKE_COMMAND} -E touch "${'$'}{CMAKE_BINARY_DIR}/vendor/mke2fs.android"
                        )
            EOF
                        echo '#!/usr/bin/env python3' > avbtool
                        echo 'print("avbtool")' >> avbtool
                        chmod +x avbtool

                        echo '#!/usr/bin/env python3' > mkbootimg
                        echo 'print("mkbootimg")' >> mkbootimg
                        chmod +x mkbootimg

                        echo '#!/usr/bin/env python3' > unpack_bootimg
                        echo 'print("unpack_bootimg")' >> unpack_bootimg
                        chmod +x unpack_bootimg

                        echo '#!/usr/bin/env python3' > repack_bootimg
                        echo 'print("repack_bootimg")' >> repack_bootimg
                        chmod +x repack_bootimg

                        echo '#!/bin/sh' > mkuserimg_mke2fs
                        echo 'echo mkuserimg_mke2fs' >> mkuserimg_mke2fs
                        chmod +x mkuserimg_mke2fs

                        touch gki/gki_kernel.img
                        echo 'BROKEN PATCH' > patches/break.patch

                        git add .
                        git commit -m 'Android tools fixture commit'
            """.trimIndent()
        val req = ToolExecutionRequest(toolId = "sh", arguments = listOf("-c", script), workingDirectory = "/tmp")
        val res = transport.execute(req)
        return if (res.exitCode == 0) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("Failed to setup android-tools fixture: ${res.stderr}"))
        }
    }

    suspend fun setupCmakeSyntaxErrorFixture(
        transport: RemoteTransportPort,
        repoPath: String,
        tag: String = "v1.0.0",
    ): Result<String> {
        val wslRepo = toWsl(repoPath)
        val script =
            """
            mkdir -p '$wslRepo'
            cd '$wslRepo'
            git init -b main
            git config user.name 'Test Runner'
            git config user.email 'test@example.com'
            echo 'THIS IS INVALID CMAKE SYNTAX' > CMakeLists.txt
            git add CMakeLists.txt
            git commit -m 'Broken CMake'
            git tag -f '$tag'
            git rev-parse HEAD
            """.trimIndent()
        val req = ToolExecutionRequest(toolId = "sh", arguments = listOf("-c", script), workingDirectory = "/tmp")
        val res = transport.execute(req)
        return if (res.exitCode == 0) {
            val commit =
                res.stdout.lines().map { it.trim() }.lastOrNull { it.matches(Regex("^[a-fA-F0-9]{40}$")) }
                    ?: return Result.failure(Exception("Failed to find commit SHA in output: ${res.stdout}"))
            Result.success(commit)
        } else {
            Result.failure(Exception("Failed to setup broken cmake fixture: ${res.stderr}"))
        }
    }

    fun createTestToolGroup(
        id: String,
        repoUrl: String,
        ref: ToolRef = ToolRef.Tag("v1.0.0"),
        commit: String = "abcdef1234567890abcdef1234567890abcdef12",
    ): ToolGroup {
        val wslRepo = toWsl(repoUrl)
        return ToolGroup(
            id = ToolGroupId(id),
            source =
            ToolSource.Git(
                recommendedUrl = wslRepo,
                pinnedRef = ref,
                pinnedCommit = commit,
            ),
            recipe =
            RecipeConfig.CMake(
                sourceSubdir = null,
                buildSubdir = "build",
                cmakeArgs = emptyList(),
            ),
            layout = emptyList(),
            outputs =
            listOf(
                ToolOutput(
                    toolId = id,
                    file = id,
                    kind = ToolOutputKind.NATIVE,
                    probe = ToolProbe(listOf("--version")),
                ),
            ),
            buildPackages = emptyList(),
        )
    }

    suspend fun setupWorkingCMakeToolFixture(
        transport: RemoteTransportPort,
        repoPath: String,
        toolName: String = "mytool",
        probeExitCode: Int = 0,
        tag: String = "v1.0.0",
    ): Result<String> {
        val wslRepo = toWsl(repoPath)
        val script =
            """
                        mkdir -p '$wslRepo'
                        cd '$wslRepo'
                        if [ ! -d .git ]; then
                            git init -b main
                            git config user.name 'Test Runner'
                            git config user.email 'test@example.com'
                        fi
                        cat << 'EOF' > CMakeLists.txt
            cmake_minimum_required(VERSION 3.16)
            project($toolName C)
            file(WRITE "${'$'}{CMAKE_BINARY_DIR}/$toolName" "#!/bin/sh\nexit $probeExitCode\n")
            add_custom_target(build_all ALL
                COMMAND chmod 755 "${'$'}{CMAKE_BINARY_DIR}/$toolName"
            )
            EOF
                        git add .
                        git commit --allow-empty -m 'Tool fixture $tag'
                        git tag -f '$tag'
                        git rev-parse HEAD
            """.trimIndent()
        val req = ToolExecutionRequest(toolId = "sh", arguments = listOf("-c", script), workingDirectory = "/tmp")
        val res = transport.execute(req)
        return if (res.exitCode == 0) {
            val commit =
                res.stdout.lines().map { it.trim() }.lastOrNull { it.matches(Regex("^[a-fA-F0-9]{40}$")) }
                    ?: return Result.failure(Exception("Failed to find commit SHA in output: ${res.stdout}"))
            Result.success(commit)
        } else {
            Result.failure(Exception("Failed to setup tool fixture: ${res.stderr}"))
        }
    }

    suspend fun setupSlowCMakeToolFixture(
        transport: RemoteTransportPort,
        repoPath: String,
        toolName: String = "mytool",
        delaySeconds: Int = 4,
        tag: String = "v1.0.0",
    ): Result<String> {
        val wslRepo = toWsl(repoPath)
        val script =
            """
                        mkdir -p '$wslRepo'
                        cd '$wslRepo'
                        if [ ! -d .git ]; then
                            git init -b main
                            git config user.name 'Test Runner'
                            git config user.email 'test@example.com'
                        fi
                        cat << 'EOF' > CMakeLists.txt
            cmake_minimum_required(VERSION 3.16)
            project($toolName C)
            file(WRITE "${'$'}{CMAKE_BINARY_DIR}/$toolName" "#!/bin/sh\nexit 0\n")
            add_custom_target(build_all ALL
                COMMAND sleep $delaySeconds
                COMMAND chmod 755 "${'$'}{CMAKE_BINARY_DIR}/$toolName"
            )
            EOF
                        git add .
                        git commit --allow-empty -m 'Slow tool fixture $tag'
                        git tag -f '$tag'
                        git rev-parse HEAD
            """.trimIndent()
        val req = ToolExecutionRequest(toolId = "sh", arguments = listOf("-c", script), workingDirectory = "/tmp")
        val res = transport.execute(req)
        return if (res.exitCode == 0) {
            val commit =
                res.stdout.lines().map { it.trim() }.lastOrNull { it.matches(Regex("^[a-fA-F0-9]{40}$")) }
                    ?: return Result.failure(Exception("Failed to find commit SHA in output: ${res.stdout}"))
            Result.success(commit)
        } else {
            Result.failure(Exception("Failed to setup slow tool fixture: ${res.stderr}"))
        }
    }
}
