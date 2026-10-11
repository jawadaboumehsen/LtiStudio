package org.ide.lti.desktop.realhost

import kotlinx.coroutines.runBlocking
import org.ide.lti.core.data.tools.RemoteCommandRunner
import org.ide.lti.core.data.tools.ToolErrorException
import org.ide.lti.core.data.tools.ToolSettingsFile
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.tools.ResolvedSource
import org.ide.lti.core.domain.tools.ToolError
import org.ide.lti.core.model.setup.ToolRef
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ToolSettingsFileRealTest {
    @Test
    fun missingFileYieldsNoPicks() = runBlocking {
        if (!RealToolHost.isAvailable()) return@runBlocking
        RealToolHost.create().use { host ->
            val runner = RemoteCommandRunner(host.transport)
            val settings = ToolSettingsFile(runner, File(host.tempHome, "LtiRomTools").canonicalPath)
            val picks = settings.readSelections().getOrThrow()
            assertTrue(picks.isEmpty())
        }
    }

    @Test
    fun roundTripsPicksAndClearsAtomically() = runBlocking {
        if (!RealToolHost.isAvailable()) return@runBlocking
        RealToolHost.create().use { host ->
            val baseDir = File(host.tempHome, "LtiRomTools").canonicalPath
            val runner = RemoteCommandRunner(host.transport)
            val settings = ToolSettingsFile(runner, baseDir)

            val gitSource =
                ResolvedSource.Git(
                    repoUrl = "https://github.com/sekaiacg/erofs-tools.git",
                    ref = ToolRef.Tag("v1.8.9"),
                    commit = "7274417816e3adfe0cd6d4a8ff194ec5f41268f4",
                    submodules = mapOf("sub" to "1111111111111111111111111111111111111111"),
                )
            val group = ToolGroupId("erofs-utils")
            settings.record(group, "v1.8.9", gitSource).getOrThrow()

            val readPicks = settings.readSelections().getOrThrow()
            assertEquals(1, readPicks.size)
            val (readId, readSource) = readPicks[group]!!
            assertEquals("v1.8.9", readId)
            assertTrue(readSource is ResolvedSource.Git)
            assertEquals(gitSource.repoUrl, readSource.repoUrl)
            assertEquals(gitSource.commit, readSource.commit)

            settings.clear(group).getOrThrow()
            val afterClear = settings.readSelections().getOrThrow()
            assertTrue(afterClear.isEmpty())
        }
    }

    @Test
    fun unreadableFileFailsAndDoesNotOverwrite() = runBlocking {
        if (!RealToolHost.isAvailable()) return@runBlocking
        RealToolHost.create().use { host ->
            val baseDir = File(host.tempHome, "LtiRomTools").canonicalPath
            val runner = RemoteCommandRunner(host.transport)
            val settings = ToolSettingsFile(runner, baseDir)

            // Write corrupt JSON
            val settingsFile = File(baseDir, "settings.json")
            settingsFile.parentFile.mkdirs()
            val corruptContent = "{ INVALID JSON 123"
            settingsFile.writeText(corruptContent)

            val readRes = settings.readSelections()
            assertTrue(readRes.isFailure)
            val ex = readRes.exceptionOrNull()
            assertTrue(ex is ToolErrorException && ex.toolError is ToolError.Internal)

            // Attempting to record should fail and leave corrupt bytes intact
            val gitSource =
                ResolvedSource.Git(
                    repoUrl = "https://example.com/repo.git",
                    ref = ToolRef.Tag("v1"),
                    commit = "1111111111111111111111111111111111111111",
                )
            val recRes = settings.record(ToolGroupId("erofs-utils"), "v1", gitSource)
            assertTrue(recRes.isFailure)
            assertEquals(corruptContent, settingsFile.readText())
        }
    }
}
