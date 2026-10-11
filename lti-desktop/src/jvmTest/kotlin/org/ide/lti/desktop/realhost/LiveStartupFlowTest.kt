package org.ide.lti.desktop.realhost

import io.ltirom.tooling.client.WslDaemonConfig
import io.ltirom.tooling.client.WslDaemonManager
import io.ltirom.tooling.client.resilience.DaemonHeartbeatMonitor
import io.ltirom.tooling.client.transport.KtorRemoteTransportAdapter
import io.ltirom.tooling.client.wsl.ServerArtifactInstaller
import io.ltirom.tooling.client.wsl.WslCliExecutor
import io.ltirom.tooling.client.wsl.WslEnvironmentDetector
import io.ltirom.tooling.core.remote.ToolListResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import org.ide.lti.core.data.setup.ServerArtifactAdapter
import org.ide.lti.core.data.setup.WslHostPrerequisiteProbe
import org.ide.lti.core.data.setup.check.CheckContext
import org.ide.lti.core.data.setup.check.EnvironmentCheck
import org.ide.lti.core.data.setup.check.ReadOnlyCommands
import org.ide.lti.core.data.setup.check.ServicePreparation
import org.ide.lti.core.data.setup.check.StageOutcome
import org.ide.lti.core.data.setup.check.stages.DistroStage
import org.ide.lti.core.data.setup.check.stages.PackagesStage
import org.ide.lti.core.data.setup.check.stages.ServiceStage
import org.ide.lti.core.data.setup.check.stages.ToolsStage
import org.ide.lti.core.data.setup.state.CheckScope
import org.ide.lti.core.data.tools.KotlinToolStoreAdapter
import org.ide.lti.core.data.tools.RemoteCommandRunner
import org.ide.lti.core.data.tools.ToolSettingsFile
import org.ide.lti.core.domain.repository.setup.ToolchainSelectionRepository
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.setup.ToolSource
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.domain.tools.ChoiceUpdate
import org.ide.lti.core.domain.tools.GroupSetupResult
import org.ide.lti.core.domain.tools.GroupStatus
import org.ide.lti.core.domain.tools.OperationResult
import org.ide.lti.core.domain.tools.ResolvedSource
import org.ide.lti.core.domain.tools.ToolError
import org.ide.lti.core.domain.tools.ToolManager
import org.ide.lti.core.domain.tools.ToolUsageCoordinator
import org.ide.lti.core.model.setup.SetupEnvironment
import org.ide.lti.core.model.setup.ToolRef
import org.ide.lti.feature.setup.tools.ToolsPageState
import org.junit.FixMethodOrder
import org.junit.runners.MethodSorters
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Live startup flow test covering quickstart scenarios A through G (T008).
 * Runs when LTI_REAL_HOST=wsl and LTI_TEST_DISTRO is specified; otherwise cleanly skips.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class LiveStartupFlowTest {
    private fun assumeWslRealHost(): Boolean {
        val realHost = System.getenv("LTI_REAL_HOST") ?: System.getProperty("lti.real.host")
        val testDistro = System.getenv("LTI_TEST_DISTRO") ?: System.getProperty("lti.test.distro")
        if (realHost != "wsl" || testDistro.isNullOrBlank()) {
            println("Skipping LiveStartupFlowTest: requires LTI_REAL_HOST=wsl and LTI_TEST_DISTRO set.")
            return false
        }
        return true
    }

    @Test
    fun scenarioA_cleanMachineSetup() = runBlocking {
        if (!assumeWslRealHost()) return@runBlocking
        val distro = System.getenv("LTI_TEST_DISTRO") ?: System.getProperty("lti.test.distro") ?: "LtiRomTest"
        val cli = WslCliExecutor()
        val detector = WslEnvironmentDetector(cli)
        val home = detector.resolveWslUserHome(distro)

        // Step 0: create dummy ~/LtiRomTools/bin/adb and ~/LtiRomTools/bin.old-1/
        cli.execute(distro, listOf("rm", "-f", "$home/LtiRomTools/settings.json"))
        cli.execute(distro, listOf("rm", "-rf", "$home/LtiRomTools/work"))
        val oldFlatAdb = "$home/LtiRomTools/bin/adb"
        val oldFlatDir = "$home/LtiRomTools/bin.old-1"
        cli.execute(distro, listOf("mkdir", "-p", "$home/LtiRomTools/bin", oldFlatDir))
        cli.execute(distro, listOf("touch", oldFlatAdb))
        assertEquals(0, cli.execute(distro, listOf("test", "-f", oldFlatAdb)).exitCode, "Old flat adb must exist")
        assertEquals(0, cli.execute(distro, listOf("test", "-d", oldFlatDir)).exitCode, "Old bin.old-1 must exist")

        // Ensure at least one required package is missing so PackagesStage tests missing package flow
        cli.execute(distro, listOf("dpkg", "--purge", "--force-all", "libfuse3-dev"), user = "root")

        val originalDefault = detector.listDistros().firstOrNull() ?: "Ubuntu"
        var heartbeatMonitor: DaemonHeartbeatMonitor? = null
        try {
            // Ensure target distro is default for DistroStage primary check
            cli.executeHost(listOf("wsl.exe", "--set-default", distro))

            val env =
                SetupEnvironment(
                    distro = distro,
                    wslVersion = 2,
                    osId = "ubuntu",
                    osVersionId = "24.04",
                    user = "lti",
                    home = home,
                )

            val supervisor = WslDaemonManager(WslDaemonConfig(distroName = distro), cli = cli, detector = detector)
            val transport = KtorRemoteTransportAdapter(supervisor, supervisor.httpClient)
            val monitor =
                DaemonHeartbeatMonitor(
                    supervisor = supervisor,
                    transport = transport,
                    intervalMs = 5_000L,
                )
            heartbeatMonitor = monitor
            val installer = ServerArtifactInstaller(cli = cli)
            val serverArtifactAdapter = ServerArtifactAdapter(installer = installer, daemonManager = supervisor)
            val servicePrep =
                ServicePreparation(
                    hostPrerequisitePort = WslHostPrerequisiteProbe(detector, cli),
                    supervisor = supervisor,
                    transport = transport,
                    serverArtifactPort = serverArtifactAdapter,
                )

            val distroStage = DistroStage(detector)
            val serviceStage =
                ServiceStage(
                    installer = installer,
                    supervisor = supervisor,
                    transport = transport,
                    servicePreparation = servicePrep,
                )
            val packagesStage = PackagesStage(servicePreparation = servicePrep)

            val store =
                KotlinToolStoreAdapter(
                    transport = transport,
                    heartbeatMonitor = monitor,
                    serverDirProvider = { "$home/.ltirom/server/current" },
                )
            val toolManager =
                ToolManager(
                    port = store,
                    catalog = ToolGroupCatalog,
                )
            val toolsStage = ToolsStage(toolManager = toolManager)

            val check = EnvironmentCheck(listOf(distroStage, serviceStage, packagesStage, toolsStage))

            val readOnlyCommands = ReadOnlyCommands(cli)
            val stateFlow = MutableStateFlow(ToolchainSetupState())
            val scope = CheckScope("Ubuntu", "scenarioA-check", stateFlow) { "scenarioA-check" }

            val ctx =
                CheckContext(
                    environment = env,
                    readOnlyCommands = readOnlyCommands,
                    userHome = home,
                    workDir = "$home/LtiRomWorkDir",
                    binDir = "$home/LtiRomTools/bin",
                    extDir = "$home/LtiRomTools/external",
                    transport = transport,
                )

            // Step 1: Run the check
            val outcome1 = check.run(ctx, scope)

            // Step 2: Verify service stage installs the bundle & java runtime
            val distroResult1 = outcome1[SetupStepStage.WSL_DETECTION]
            assertIs<StageOutcome.Success>(distroResult1, "Distro stage must succeed")

            val serviceResult1 = outcome1[SetupStepStage.SERVER_CONNECTIVITY]
            assertIs<StageOutcome.Success>(serviceResult1, "Service stage must succeed and install bundle")

            monitor.start()

            // Verify server process in WSL runs bundled Java
            val pid = supervisor.getConnectionInfo()?.pid
            assertNotNull(pid, "Daemon PID must be available")
            val exeLink = cli.execute(distro, listOf("readlink", "-f", "/proc/$pid/exe")).output.trim()
            assertTrue(
                exeLink.contains("/runtime/bin/java") || exeLink.contains("java"),
                "Process /proc/$pid/exe must resolve to bundled java runtime, got: $exeLink",
            )

            // Verify there is still no system JDK 21
            val java21Check =
                cli.execute(
                    distro,
                    listOf(
                        "sh",
                        "-c",
                        "command -v /usr/lib/jvm/java-21-openjdk-amd64/bin/java || dpkg -l openjdk-21-jdk 2>/dev/null | grep -E '^ii' || echo none",
                    ),
                ).output.trim()
            assertEquals("none", java21Check, "Clean distro must not have system JDK 21")

            // Step 3: Packages stage prints one apt command
            val packagesResult1 = outcome1[SetupStepStage.SYSTEM_PACKAGES]
            assertIs<StageOutcome.Failed>(packagesResult1, "Packages stage must fail on clean machine")
            val aptCommand = packagesResult1.error.orEmpty()
            assertTrue(aptCommand.contains("apt-get install -y"), "Must produce an apt-get command: $aptCommand")

            // Tools stage is pending
            val toolsResult1 = outcome1[SetupStepStage.TOOLCHAIN_COMPILATION]
            assertIs<StageOutcome.Pending>(toolsResult1, "Tools stage must be pending waiting for packages")

            // Step 4: Run that exact command as root in LtiRomTest, standing in for the user
            val aptToRun = aptCommand.replace("sudo ", "")
            val aptExecResult =
                cli.execute(
                    distro,
                    listOf("sh", "-c", aptToRun),
                    timeoutSeconds = 900,
                    user = "root",
                )
            assertEquals(0, aptExecResult.exitCode, "Apt package installation must exit 0: ${aptExecResult.error}")

            // Step 5: Check again
            val outcome2 = check.run(ctx, scope)
            assertIs<StageOutcome.Success>(outcome2[SetupStepStage.WSL_DETECTION])
            assertIs<StageOutcome.Success>(outcome2[SetupStepStage.SERVER_CONNECTIVITY])
            assertIs<StageOutcome.Success>(
                outcome2[SetupStepStage.SYSTEM_PACKAGES],
                "Packages must succeed after install",
            )

            // Step 6: Automatic tools run
            val summary = toolManager.prepareMissing()
            assertEquals(7, summary.groupResults.size, "All 7 groups must be attempted")
            for ((gid, res) in summary.groupResults) {
                assertTrue(
                    res is GroupSetupResult.Installed || res is GroupSetupResult.Reused,
                    "Group ${gid.value} must be Installed or Reused, got $res",
                )
            }

            // Step 0's old bin/adb and bin.old-1/ are deleted
            val adbStillExists = cli.execute(distro, listOf("test", "-e", oldFlatAdb)).exitCode == 0
            val dirStillExists = cli.execute(distro, listOf("test", "-e", oldFlatDir)).exitCode == 0
            assertFalse(adbStillExists, "Old flat adb must be deleted")
            assertFalse(dirStillExists, "Old bin.old-1 must be deleted")

            // There is no settings.json (no picks)
            val settingsExist =
                cli.execute(distro, listOf("test", "-f", "$home/LtiRomTools/settings.json")).exitCode == 0
            assertFalse(settingsExist, "settings.json must not exist (no user picks)")

            // Verify each group has version.json and current link
            for (group in ToolGroupCatalog.DEFAULT_GROUPS) {
                val currentLink = "$home/LtiRomTools/bin/${group.id.value}/current"
                val linkCheck = cli.execute(distro, listOf("test", "-L", currentLink)).exitCode
                assertEquals(0, linkCheck, "Group ${group.id.value} must have 'current' symlink")
                val versionJson = "$home/LtiRomTools/bin/${group.id.value}/current/version.json"
                val verCheck = cli.execute(distro, listOf("test", "-f", versionJson)).exitCode
                assertEquals(0, verCheck, "Group ${group.id.value} must have version.json")
            }

            // /tools list resolves every catalog tool
            val toolsResult = transport.listTools()
            assertIs<ToolListResult.Tools>(toolsResult)
            val resolvedToolIds = toolsResult.list.map { it.tool }.toSet()
            for (group in ToolGroupCatalog.DEFAULT_GROUPS) {
                for (output in group.outputs) {
                    assertTrue(
                        resolvedToolIds.contains(output.toolId),
                        "Remote server /tools must list tool '${output.toolId}'",
                    )
                }
            }

            // Step 7: Run representative extraction (small input)
            val mkfs = "$home/LtiRomTools/bin/erofs-utils/current/mkfs.erofs"
            val fsck = "$home/LtiRomTools/bin/erofs-utils/current/fsck.erofs"
            val testSrc = "$home/extract_test_src"
            val testImg = "$home/extract_test.img"
            val testOut = "$home/extract_test_out"

            cli.execute(distro, listOf("rm", "-rf", testSrc, testImg, testOut))
            cli.execute(distro, listOf("mkdir", "-p", testSrc))
            val testContent = "SC-001 representative extraction verified"
            cli.execute(distro, listOf("sh", "-c", "echo '$testContent' > $testSrc/sample.txt"))
            val mkfsRes = cli.execute(distro, listOf(mkfs, testImg, testSrc))
            assertEquals(0, mkfsRes.exitCode, "mkfs.erofs must succeed: ${mkfsRes.error}")

            cli.execute(distro, listOf("mkdir", "-p", testOut))
            val fsckRes = cli.execute(distro, listOf(fsck, "--extract=$testOut", testImg))
            assertEquals(0, fsckRes.exitCode, "fsck.erofs --extract must succeed: ${fsckRes.error}")

            val extracted = cli.execute(distro, listOf("cat", "$testOut/sample.txt")).output.trim()
            assertEquals(testContent, extracted, "Extracted content must match original")

            // Clean up extraction test files
            cli.execute(distro, listOf("rm", "-rf", testSrc, testImg, testOut))
        } finally {
            heartbeatMonitor?.close()
            cli.executeHost(listOf("wsl.exe", "--set-default", originalDefault))
        }
    }

    @Test
    fun scenarioB_secondLaunchReusesToolsWithoutBuild() = runBlocking {
        if (!assumeWslRealHost()) return@runBlocking
        val distro = System.getenv("LTI_TEST_DISTRO") ?: System.getProperty("lti.test.distro") ?: "LtiRomTest"
        val cli = WslCliExecutor()
        val detector = WslEnvironmentDetector(cli)
        val home = detector.resolveWslUserHome(distro)

        val originalDefault = detector.listDistros().firstOrNull() ?: "Ubuntu"
        var heartbeatMonitor: DaemonHeartbeatMonitor? = null
        try {
            cli.executeHost(listOf("wsl.exe", "--set-default", distro))

            val env =
                SetupEnvironment(
                    distro = distro,
                    wslVersion = 2,
                    osId = "ubuntu",
                    osVersionId = "24.04",
                    user = "lti",
                    home = home,
                )

            val supervisor = WslDaemonManager(WslDaemonConfig(distroName = distro), cli = cli, detector = detector)
            val transport = KtorRemoteTransportAdapter(supervisor, supervisor.httpClient)
            val monitor =
                DaemonHeartbeatMonitor(
                    supervisor = supervisor,
                    transport = transport,
                    intervalMs = 5_000L,
                )
            heartbeatMonitor = monitor
            val installer = ServerArtifactInstaller(cli = cli)
            val serverArtifactAdapter = ServerArtifactAdapter(installer = installer, daemonManager = supervisor)
            val servicePrep =
                ServicePreparation(
                    hostPrerequisitePort = WslHostPrerequisiteProbe(detector, cli),
                    supervisor = supervisor,
                    transport = transport,
                    serverArtifactPort = serverArtifactAdapter,
                )

            val distroStage = DistroStage(detector)
            val serviceStage =
                ServiceStage(
                    installer = installer,
                    supervisor = supervisor,
                    transport = transport,
                    heartbeatMonitor = monitor,
                    servicePreparation = servicePrep,
                )
            val packagesStage = PackagesStage(servicePreparation = servicePrep)

            val store =
                KotlinToolStoreAdapter(
                    transport = transport,
                    heartbeatMonitor = monitor,
                    serverDirProvider = { "$home/.ltirom/server/current" },
                )
            val toolManager =
                ToolManager(
                    port = store,
                    catalog = ToolGroupCatalog,
                )
            val toolsStage = ToolsStage(toolManager = toolManager)

            val check = EnvironmentCheck(listOf(distroStage, serviceStage, packagesStage, toolsStage))

            val readOnlyCommands = ReadOnlyCommands(cli)
            val stateFlow = MutableStateFlow(ToolchainSetupState())
            val scope = CheckScope("Ubuntu", "scenarioB-check", stateFlow) { "scenarioB-check" }

            val ctx =
                CheckContext(
                    environment = env,
                    readOnlyCommands = readOnlyCommands,
                    userHome = home,
                    workDir = "$home/LtiRomWorkDir",
                    binDir = "$home/LtiRomTools/bin",
                    extDir = "$home/LtiRomTools/external",
                    transport = transport,
                )

            // Step 1: Run the check again (fast second launch)
            val outcome1 = check.run(ctx, scope)
            assertIs<StageOutcome.Success>(
                outcome1[SetupStepStage.TOOLCHAIN_COMPILATION],
                "ToolsStage must be SUCCESS on second launch",
            )
            monitor.start()

            // Count results files before
            val resultsBefore =
                cli.execute(
                    distro,
                    listOf("sh", "-c", "ls -1 $home/LtiRomTools/results/*.json 2>/dev/null || true"),
                ).output.trim().lines().filter {
                    it.isNotBlank()
                }

            // Automatic tools run: prepareMissing must reuse all groups without building or fetching
            val summary1 = toolManager.prepareMissing()
            for ((gid, res) in summary1.groupResults) {
                assertIs<GroupSetupResult.Reused>(res, "Group ${gid.value} must be Reused, got $res")
            }

            // Zero new results/*.json
            val resultsAfter =
                cli.execute(
                    distro,
                    listOf("sh", "-c", "ls -1 $home/LtiRomTools/results/*.json 2>/dev/null || true"),
                ).output.trim().lines().filter {
                    it.isNotBlank()
                }
            assertEquals(resultsBefore.size, resultsAfter.size, "Second launch must create zero new results/*.json")

            // Step 2: Delete one output file (e.g. mkfs.erofs in erofs-utils/current)
            val erofsTarget =
                cli.execute(
                    distro,
                    listOf("readlink", "-f", "$home/LtiRomTools/bin/erofs-utils/current"),
                ).output.trim()
            val erofsVerId =
                cli.execute(
                    distro,
                    listOf("readlink", "$home/LtiRomTools/bin/erofs-utils/current"),
                ).output.trim()
            val mkfsFile = "$erofsTarget/mkfs.erofs"
            val inodeBefore = cli.execute(distro, listOf("stat", "-c", "%i", erofsTarget)).output.trim()

            // Inodes of other groups before repair
            val otherInodesBefore =
                ToolGroupCatalog.DEFAULT_GROUPS.filter { it.id.value != "erofs-utils" }.associate { g ->
                    val gPath =
                        cli.execute(
                            distro,
                            listOf("readlink", "-f", "$home/LtiRomTools/bin/${g.id.value}/current"),
                        ).output.trim()
                    g.id to cli.execute(distro, listOf("stat", "-c", "%i", gPath)).output.trim()
                }

            cli.execute(distro, listOf("rm", "-f", mkfsFile))

            // Step 3: Check again -> erofs-utils reports Needs repair, others untouched
            val inventoryBeforeRepair = toolManager.refresh().getOrThrow()
            val erofsStatus = inventoryBeforeRepair.groups[ToolGroupId("erofs-utils")]
            assertNotNull(erofsStatus)
            assertEquals(
                GroupStatus.NeedsRepair,
                erofsStatus.status,
                "erofs-utils must be NeedsRepair after deleting output",
            )

            for ((gid, inode) in otherInodesBefore) {
                val gStatus = inventoryBeforeRepair.groups[gid]
                assertNotNull(gStatus)
                assertEquals(GroupStatus.Ready, gStatus.status, "Group ${gid.value} must remain Ready")
            }

            // Step 4: Repair erofs-utils
            val repairResult = toolManager.repair(ToolGroupId("erofs-utils"))
            assertIs<OperationResult.Installed>(repairResult, "Repair must succeed as Installed")
            assertEquals(erofsVerId, repairResult.versionId, "Repaired version ID must match original")

            // The folder inode must change (swapped out) while directory name remains identical
            val erofsTargetAfter =
                cli.execute(
                    distro,
                    listOf("readlink", "-f", "$home/LtiRomTools/bin/erofs-utils/current"),
                ).output.trim()
            val erofsVerIdAfter =
                cli.execute(
                    distro,
                    listOf("readlink", "$home/LtiRomTools/bin/erofs-utils/current"),
                ).output.trim()
            val inodeAfter = cli.execute(distro, listOf("stat", "-c", "%i", erofsTargetAfter)).output.trim()

            assertEquals(erofsVerId, erofsVerIdAfter, "Version directory name must remain unchanged")
            assertTrue(inodeBefore != inodeAfter, "Directory inode must change ($inodeBefore vs $inodeAfter)")

            // Output file is restored and executable
            assertEquals(
                0,
                cli.execute(distro, listOf("test", "-x", "$erofsTargetAfter/mkfs.erofs")).exitCode,
                "mkfs.erofs must be restored and executable",
            )

            // Other groups' folders were untouched (same inodes)
            for ((gid, expectedInode) in otherInodesBefore) {
                val gPath =
                    cli.execute(
                        distro,
                        listOf("readlink", "-f", "$home/LtiRomTools/bin/${gid.value}/current"),
                    ).output.trim()
                val actualInode = cli.execute(distro, listOf("stat", "-c", "%i", gPath)).output.trim()
                assertEquals(expectedInode, actualInode, "Untouched group ${gid.value} inode must not change")
            }

            // Final status: erofs-utils is Ready
            val finalInv = toolManager.refresh().getOrThrow()
            assertEquals(
                GroupStatus.Ready,
                finalInv.groups[ToolGroupId("erofs-utils")]?.status,
                "erofs-utils must be Ready after repair",
            )
        } finally {
            heartbeatMonitor?.close()
            cli.executeHost(listOf("wsl.exe", "--set-default", originalDefault))
        }
    }

    @Test
    fun scenarioC_changeVersionAndUseInstant() = runBlocking {
        if (!assumeWslRealHost()) return@runBlocking
        val distro = System.getenv("LTI_TEST_DISTRO") ?: System.getProperty("lti.test.distro") ?: "LtiRomTest"
        val cli = WslCliExecutor()
        val detector = WslEnvironmentDetector(cli)
        val home = detector.resolveWslUserHome(distro)

        val originalDefault = detector.listDistros().firstOrNull() ?: "Ubuntu"
        var heartbeatMonitor: DaemonHeartbeatMonitor? = null
        try {
            cli.executeHost(listOf("wsl.exe", "--set-default", distro))

            val env =
                SetupEnvironment(
                    distro = distro,
                    wslVersion = 2,
                    osId = "ubuntu",
                    osVersionId = "24.04",
                    user = "lti",
                    home = home,
                )

            val supervisor = WslDaemonManager(WslDaemonConfig(distroName = distro), cli = cli, detector = detector)
            val transport = KtorRemoteTransportAdapter(supervisor, supervisor.httpClient)
            val monitor =
                DaemonHeartbeatMonitor(
                    supervisor = supervisor,
                    transport = transport,
                    intervalMs = 5_000L,
                )
            heartbeatMonitor = monitor
            val installer = ServerArtifactInstaller(cli = cli)
            val serverArtifactAdapter = ServerArtifactAdapter(installer = installer, daemonManager = supervisor)
            val servicePrep =
                ServicePreparation(
                    hostPrerequisitePort = WslHostPrerequisiteProbe(detector, cli),
                    supervisor = supervisor,
                    transport = transport,
                    serverArtifactPort = serverArtifactAdapter,
                )

            val distroStage = DistroStage(detector)
            val serviceStage =
                ServiceStage(
                    installer = installer,
                    supervisor = supervisor,
                    transport = transport,
                    heartbeatMonitor = monitor,
                    servicePreparation = servicePrep,
                )
            val packagesStage = PackagesStage(servicePreparation = servicePrep)

            val store =
                KotlinToolStoreAdapter(
                    transport = transport,
                    heartbeatMonitor = monitor,
                    serverDirProvider = { "$home/.ltirom/server/current" },
                )
            val trustedUrls = mutableSetOf<String>()
            val selectionRepo =
                object : ToolchainSelectionRepository {
                    override suspend fun recordTrust(repoUrl: String): Result<Unit> {
                        trustedUrls += repoUrl
                        return Result.success(Unit)
                    }

                    override suspend fun isTrusted(repoUrl: String): Boolean = repoUrl in trustedUrls
                }
            val toolManager =
                ToolManager(
                    port = store,
                    catalog = ToolGroupCatalog,
                    selectionRepository = selectionRepo,
                )
            val toolsStage = ToolsStage(toolManager = toolManager)
            val check = EnvironmentCheck(listOf(distroStage, serviceStage, packagesStage, toolsStage))

            val readOnlyCommands = ReadOnlyCommands(cli)
            val stateFlow = MutableStateFlow(ToolchainSetupState())
            val scope = CheckScope("Ubuntu", "scenarioC-check", stateFlow) { "scenarioC-check" }

            val ctx =
                CheckContext(
                    environment = env,
                    readOnlyCommands = readOnlyCommands,
                    userHome = home,
                    workDir = "$home/LtiRomWorkDir",
                    binDir = "$home/LtiRomTools/bin",
                    extDir = "$home/LtiRomTools/external",
                    transport = transport,
                )

            monitor.start()

            // 1. Resolve another erofs tag (v1.8.4-250126)
            val erofsGroup = ToolGroupCatalog.EROFS_UTILS
            val recUrl = (erofsGroup.source as ToolSource.Git).recommendedUrl
            val otherTag = "v1.8.4-250126"
            val resolveRes = toolManager.resolve(ToolGroupId("erofs-utils"), recUrl, ToolRef.Tag(otherTag))
            assertTrue(
                resolveRes.isSuccess,
                "Resolving tag $otherTag must succeed: ${resolveRes.exceptionOrNull()?.message}",
            )
            val resolvedCheck = resolveRes.getOrThrow()
            val otherCommit = resolvedCheck.commit
            assertTrue(
                otherCommit.matches(Regex("^[a-fA-F0-9]{40}$")),
                "Commit SHA must be 40 hex chars: $otherCommit",
            )

            // 2. Install & use
            val otherSource = ResolvedSource.Git(recUrl, ToolRef.Tag(otherTag), otherCommit)
            val installRes = toolManager.installAndUse(ToolGroupId("erofs-utils"), otherSource)
            assertTrue(
                installRes is OperationResult.Installed || installRes is OperationResult.Reused,
                "installAndUse must be Installed or Reused: $installRes",
            )
            val installedVerId = if (installRes is OperationResult.Installed) installRes.versionId else (installRes as OperationResult.Reused).versionId
            assertEquals(otherTag, installedVerId)

            // Verify: new directory named after the tag
            val tagDir = "$home/LtiRomTools/bin/erofs-utils/$otherTag"
            assertEquals(
                0,
                cli.execute(distro, listOf("test", "-d", tagDir)).exitCode,
                "Folder named after tag must exist: $tagDir",
            )

            // Verify: current symlink moves to the new tag
            val currentTarget =
                cli.execute(
                    distro,
                    listOf("readlink", "$home/LtiRomTools/bin/erofs-utils/current"),
                ).output.trim()
            assertEquals(otherTag, currentTarget, "current symlink must point to $otherTag")

            // Verify: ~/LtiRomTools/settings.json holds erofs-utils -> that tag with its 40-hex commit
            val sFile = ToolSettingsFile(RemoteCommandRunner(transport), "$home/LtiRomTools")
            val selections1 = sFile.readSelections().getOrThrow()
            val erofsPick1 = selections1[ToolGroupId("erofs-utils")]
            assertNotNull(erofsPick1, "settings.json must have erofs-utils pick")
            assertEquals(otherTag, erofsPick1.first)
            val pickGit1 = erofsPick1.second as ResolvedSource.Git
            assertEquals(otherCommit, pickGit1.commit)

            // 3. Use the first version (v1.8.10-251217): < 2 s duration, no build (result is Used), settings.json updated
            val firstVersionId = "v1.8.10-251217"
            val useElapsed1 =
                measureTimeMillis {
                    val useRes =
                        toolManager.use(
                            ToolGroupId("erofs-utils"),
                            firstVersionId,
                            choice = ChoiceUpdate.Record,
                        )
                    assertIs<OperationResult.Used>(useRes, "Use first version must return OperationResult.Used")
                }
            assertTrue(useElapsed1 < 2000L, "Switch to first version took $useElapsed1 ms, must be < 2000 ms")
            val currentAfterUse1 =
                cli.execute(
                    distro,
                    listOf("readlink", "$home/LtiRomTools/bin/erofs-utils/current"),
                ).output.trim()
            assertEquals(firstVersionId, currentAfterUse1, "current symlink must point to $firstVersionId")
            val selections2 = sFile.readSelections().getOrThrow()
            assertEquals(firstVersionId, selections2[ToolGroupId("erofs-utils")]?.first)

            // 4. Use the tag version again: < 2 s duration, no build (result is Used), settings.json updated
            val useElapsed2 =
                measureTimeMillis {
                    val useRes = toolManager.use(ToolGroupId("erofs-utils"), otherTag, choice = ChoiceUpdate.Record)
                    assertIs<OperationResult.Used>(useRes, "Use tag version must return OperationResult.Used")
                }
            assertTrue(useElapsed2 < 2000L, "Switch to tag version took $useElapsed2 ms, must be < 2000 ms")
            val currentAfterUse2 =
                cli.execute(
                    distro,
                    listOf("readlink", "$home/LtiRomTools/bin/erofs-utils/current"),
                ).output.trim()
            assertEquals(otherTag, currentAfterUse2, "current symlink must point to $otherTag")
            val selections3 = sFile.readSelections().getOrThrow()
            assertEquals(otherTag, selections3[ToolGroupId("erofs-utils")]?.first)

            // 5. Untrusted URL: install from custom repo URL without trust returns ToolError.TrustRequired
            val untrustedUrl = "https://github.com/custom-untrusted/erofs-tools.git"
            val untrustedRes =
                toolManager.installAndUse(
                    group = ToolGroupId("erofs-utils"),
                    source = ResolvedSource.Git(untrustedUrl, ToolRef.Tag("v1.0.0"), otherCommit),
                )
            assertIs<OperationResult.TrustRequired>(untrustedRes, "Untrusted repository must return TrustRequired")
            assertEquals(untrustedUrl, untrustedRes.url)

            // 6. Wrong layout: install from repo with missing required layout outputs returns ToolError.UnsupportedLayout
            val wrongRepo = "$home/wrong_layout_repo"
            cli.execute(distro, listOf("rm", "-rf", wrongRepo))
            cli.execute(distro, listOf("mkdir", "-p", wrongRepo))
            cli.execute(
                distro,
                listOf(
                    "sh",
                    "-c",
                    "cd $wrongRepo && git init -b main && git config user.name 'Test Runner' && git config user.email 'test@example.com' && echo 'no cmake' > README.md && git add README.md && git commit -m 'Initial commit'",
                ),
            )
            val wrongCommit = cli.execute(distro, listOf("git", "-C", wrongRepo, "rev-parse", "HEAD")).output.trim()
            selectionRepo.recordTrust(wrongRepo)

            val wrongLayoutRes =
                toolManager.installAndUse(
                    group = ToolGroupId("erofs-utils"),
                    source = ResolvedSource.Git(wrongRepo, ToolRef.Branch("main"), wrongCommit),
                )
            assertIs<OperationResult.Failed>(wrongLayoutRes, "Install with wrong layout must fail")
            val layoutErr = wrongLayoutRes.error
            assertIs<ToolError.UnsupportedLayout>(layoutErr, "Error must be UnsupportedLayout")
            assertTrue(
                layoutErr.missingPaths.contains("CMakeLists.txt"),
                "Missing paths must contain CMakeLists.txt: ${layoutErr.missingPaths}",
            )

            cli.execute(distro, listOf("rm", "-rf", wrongRepo))
        } finally {
            heartbeatMonitor?.close()
            cli.executeHost(listOf("wsl.exe", "--set-default", originalDefault))
        }
    }

    @Test
    fun scenarioD_failureIsolationAndRetryFailed(): Unit = runBlocking {
        if (!assumeWslRealHost()) return@runBlocking
        val distro = System.getenv("LTI_TEST_DISTRO") ?: System.getProperty("lti.test.distro") ?: "LtiRomTest"
        val cli = WslCliExecutor()
        val detector = WslEnvironmentDetector(cli)
        val home = detector.resolveWslUserHome(distro)

        val originalDefault = detector.listDistros().firstOrNull() ?: "Ubuntu"
        var heartbeatMonitor: DaemonHeartbeatMonitor? = null
        try {
            cli.executeHost(listOf("wsl.exe", "--set-default", distro))

            val env =
                SetupEnvironment(
                    distro = distro,
                    wslVersion = 2,
                    osId = "ubuntu",
                    osVersionId = "24.04",
                    user = "lti",
                    home = home,
                )

            val supervisor = WslDaemonManager(WslDaemonConfig(distroName = distro), cli = cli, detector = detector)
            val transport = KtorRemoteTransportAdapter(supervisor, supervisor.httpClient)
            val monitor =
                DaemonHeartbeatMonitor(
                    supervisor = supervisor,
                    transport = transport,
                    intervalMs = 5_000L,
                )
            heartbeatMonitor = monitor
            monitor.start()

            val store =
                KotlinToolStoreAdapter(
                    transport = transport,
                    heartbeatMonitor = monitor,
                    serverDirProvider = { "$home/.ltirom/server/current" },
                )
            val toolManager =
                ToolManager(
                    port = store,
                    catalog = ToolGroupCatalog,
                )

            // Setup fixtures in WSL
            val repo1 = "$home/broken-cmake-repo"
            val repo2 = "$home/good-cmake-repo"
            cli.execute(distro, listOf("rm", "-rf", repo1, repo2))

            val commit1 = Fixtures.setupCmakeSyntaxErrorFixture(transport, repo1).getOrThrow()
            val commit2 =
                Fixtures.setupWorkingCMakeToolFixture(
                    transport = transport,
                    repoPath = repo2,
                    toolName = "good-group",
                    probeExitCode = 0,
                    tag = "v1.0.0",
                ).getOrThrow()

            val brokenGroup =
                Fixtures.createTestToolGroup(
                    id = "broken-group",
                    repoUrl = repo1,
                    ref = ToolRef.Tag("v1.0.0"),
                    commit = commit1,
                )
            val goodGroup =
                Fixtures.createTestToolGroup(
                    id = "good-group",
                    repoUrl = repo2,
                    ref = ToolRef.Tag("v1.0.0"),
                    commit = commit2,
                )

            ToolGroupCatalog.withIsolatedTestGroups(listOf(brokenGroup, goodGroup)) {
                // Run setup / prepareMissing
                val summary1 = toolManager.prepareMissing()

                assertFalse(summary1.isComplete, "Pipeline must not be complete when a group fails")
                assertEquals(1, summary1.readyCount, "Good group must be ready")
                assertEquals(1, summary1.failedCount, "Broken group must be failed")
                assertEquals(0, summary1.notAttemptedCount, "No group should be not attempted")

                val res1 = summary1.groupResults[brokenGroup.id]
                assertIs<GroupSetupResult.Failed>(res1)
                assertIs<ToolError.BuildFailed>(res1.error)

                val res2 = summary1.groupResults[goodGroup.id]
                assertIs<GroupSetupResult.Installed>(res2)

                // Verify the good group's current symlink exists and is executable
                val goodCurrent =
                    cli.execute(
                        distro,
                        listOf("readlink", "-f", "$home/LtiRomTools/bin/good-group/current"),
                    ).output.trim()
                assertEquals(0, cli.execute(distro, listOf("test", "-x", "$goodCurrent/good-group")).exitCode)

                // Inode of the good group
                val goodInodeBefore = cli.execute(distro, listOf("stat", "-c", "%i", goodCurrent)).output.trim()

                // Fix broken group in repo1
                val fixedCommit =
                    Fixtures.setupWorkingCMakeToolFixture(
                        transport = transport,
                        repoPath = repo1,
                        toolName = "broken-group",
                        probeExitCode = 0,
                        tag = "v1.0.0",
                    ).getOrThrow()

                val fixedGroup =
                    Fixtures.createTestToolGroup(
                        id = "broken-group",
                        repoUrl = repo1,
                        ref = ToolRef.Tag("v1.0.0"),
                        commit = fixedCommit,
                    )

                ToolGroupCatalog.withIsolatedTestGroups(listOf(fixedGroup, goodGroup)) {
                    // Retry failed: rebuilds only the failed group, intact group is reused
                    val retrySummary = toolManager.retryFailed()

                    assertTrue(retrySummary.isComplete, "Pipeline must be complete after retrying fixed group")
                    assertEquals(2, retrySummary.readyCount)
                    assertEquals(0, retrySummary.failedCount)
                    assertEquals(0, retrySummary.notAttemptedCount)

                    val retryRes1 = retrySummary.groupResults[fixedGroup.id]
                    assertIs<GroupSetupResult.Installed>(retryRes1)

                    val retryRes2 = retrySummary.groupResults[goodGroup.id]
                    assertIs<GroupSetupResult.Reused>(retryRes2, "Intact good group must be reused, not rebuilt")

                    val goodInodeAfter = cli.execute(distro, listOf("stat", "-c", "%i", goodCurrent)).output.trim()
                    assertEquals(
                        goodInodeBefore,
                        goodInodeAfter,
                        "Intact good group directory inode must not change",
                    )
                }
            }

            cli.execute(distro, listOf("rm", "-rf", repo1, repo2))
        } finally {
            heartbeatMonitor?.close()
            cli.executeHost(listOf("wsl.exe", "--set-default", originalDefault))
        }
    }

    @Test
    fun scenarioE_jobsKeepTheirTools(): Unit = runBlocking {
        if (!assumeWslRealHost()) return@runBlocking
        val distro = System.getenv("LTI_TEST_DISTRO") ?: System.getProperty("lti.test.distro") ?: "LtiRomTest"
        val cli = WslCliExecutor()
        val detector = WslEnvironmentDetector(cli)
        val home = detector.resolveWslUserHome(distro)

        val originalDefault = detector.listDistros().firstOrNull() ?: "Ubuntu"
        var heartbeatMonitor: DaemonHeartbeatMonitor? = null
        val pageJob = SupervisorJob()
        val pageScope = CoroutineScope(Dispatchers.Default + pageJob)
        try {
            cli.executeHost(listOf("wsl.exe", "--set-default", distro))

            val supervisor = WslDaemonManager(WslDaemonConfig(distroName = distro), cli = cli, detector = detector)
            val transport = KtorRemoteTransportAdapter(supervisor, supervisor.httpClient)
            val monitor =
                DaemonHeartbeatMonitor(
                    supervisor = supervisor,
                    transport = transport,
                    intervalMs = 5_000L,
                )
            heartbeatMonitor = monitor
            monitor.start()

            val store =
                KotlinToolStoreAdapter(
                    transport = transport,
                    heartbeatMonitor = monitor,
                    serverDirProvider = { "$home/.ltirom/server/current" },
                )
            val coordinator = ToolUsageCoordinator()
            val toolManager =
                ToolManager(
                    port = store,
                    catalog = ToolGroupCatalog,
                    usageCoordinator = coordinator,
                )

            ToolGroupCatalog.withIsolatedTestGroup(ToolGroupCatalog.EROFS_UTILS) {
                // Ensure both v1.8.10-251217 and v1.8.4-250126 are installed for erofs-utils
                val inv = toolManager.refresh().getOrThrow()
                val erofsGroup = inv.groups[ToolGroupId("erofs-utils")]
                val recUrl = (ToolGroupCatalog.EROFS_UTILS.source as ToolSource.Git).recommendedUrl
                val v1Id = "v1.8.10-251217"
                val v2Id = "v1.8.4-250126"

                if (erofsGroup == null || erofsGroup.installed.none { it.id == v1Id }) {
                    val res1 =
                        toolManager.resolve(
                            ToolGroupId("erofs-utils"),
                            recUrl,
                            ToolRef.Tag(v1Id),
                        ).getOrThrow()
                    toolManager.installAndUse(
                        ToolGroupId("erofs-utils"),
                        ResolvedSource.Git(recUrl, ToolRef.Tag(v1Id), res1.commit),
                    )
                }
                if (erofsGroup == null || erofsGroup.installed.none { it.id == v2Id }) {
                    val res2 =
                        toolManager.resolve(
                            ToolGroupId("erofs-utils"),
                            recUrl,
                            ToolRef.Tag(v2Id),
                        ).getOrThrow()
                    toolManager.installAndUse(
                        ToolGroupId("erofs-utils"),
                        ResolvedSource.Git(recUrl, ToolRef.Tag(v2Id), res2.commit),
                    )
                }

                // Start from v1
                val useStart = toolManager.use(ToolGroupId("erofs-utils"), v1Id, ChoiceUpdate.Record)
                assertTrue(useStart !is OperationResult.Failed, "Initial switch to $v1Id must succeed")

                // Initialize ToolsPageState with isolated scope
                val pageState = ToolsPageState(manager = toolManager, scope = pageScope)
                toolManager.refresh()

                // Prepare extraction test directories and sample data
                val testSrc1 = "$home/extract_e1_src"
                val testImg1 = "$home/extract_e1.img"
                val testOut1 = "$home/extract_e1_out"
                val testOut2 = "$home/extract_e2_out"

                cli.execute(distro, listOf("rm", "-rf", testSrc1, testImg1, testOut1, testOut2))
                cli.execute(distro, listOf("mkdir", "-p", testSrc1))
                val sampleContent = "User Story 5: Running jobs keep their tools across version switch"
                cli.execute(distro, listOf("sh", "-c", "echo '$sampleContent' > $testSrc1/test.txt"))

                println("Scenario E: Step 1 - Starting Job 1 and pinning tools")
                // --- STEP 1: Start Job 1 and capture pinned tools (FR-023) ---
                val job1Pins =
                    coordinator.admitJob("job-extract-1") {
                        store.paths(ToolGroupCatalog).getOrThrow()
                    }
                assertTrue(coordinator.isJobActive, "Job 1 must be active")
                assertEquals(v1Id, job1Pins.versions["fsck.erofs"], "Job 1 must pin $v1Id")
                val job1Mkfs = job1Pins.paths.getValue("mkfs.erofs")
                val job1Fsck = job1Pins.paths.getValue("fsck.erofs")
                assertTrue(
                    job1Mkfs.contains(v1Id),
                    "Job 1 mkfs path must point to immutable $v1Id folder, got $job1Mkfs",
                )
                assertTrue(
                    job1Fsck.contains(v1Id),
                    "Job 1 fsck path must point to immutable $v1Id folder, got $job1Fsck",
                )

                // Stage 1 of Job 1: Build erofs image using pinned mkfs.erofs
                val mkfsRes1 = cli.execute(distro, listOf(job1Mkfs, testImg1, testSrc1))
                assertEquals(0, mkfsRes1.exitCode, "Job 1 mkfs.erofs must succeed: ${mkfsRes1.error}")

                println("Scenario E: Step 2 - Mid-job UI and Manager checks")
                // --- STEP 2: Mid-job UI and Manager checks (FR-025, FR-016) ---
                pageState.selectGroup(ToolGroupId("erofs-utils"))

                // 2a. Removal of installed version while job is running is refused in UI and Domain
                assertFalse(
                    pageState.canRemoveVersion(v2Id),
                    "Removal must be disabled in ToolsPageState while job is running",
                )
                assertEquals("A ROM job is running", pageState.removeDisabledReason(v2Id))
                val rmAttempt = toolManager.remove(ToolGroupId("erofs-utils"), v2Id)
                assertIs<OperationResult.Failed>(rmAttempt, "Direct remove call during job must fail")
                assertIs<ToolError.InUse>(
                    rmAttempt.error,
                    "Remove error must be ToolError.InUse: ${rmAttempt.error}",
                )

                // 2b. Version switching IS allowed during job (FR-025)
                assertTrue(
                    pageState.canUseVersion(v2Id),
                    "Version switch must remain enabled in ToolsPageState during job",
                )
                assertNull(pageState.useDisabledReason(v2Id))

                // Switch erofs-utils to v2 mid-job
                val useMidJob = toolManager.use(ToolGroupId("erofs-utils"), v2Id, ChoiceUpdate.Record)
                assertIs<OperationResult.Used>(useMidJob, "Mid-job version switch must succeed without building")

                // Verify current symlink in WSL has changed to v2
                val currentTarget =
                    cli.execute(
                        distro,
                        listOf("readlink", "$home/LtiRomTools/bin/erofs-utils/current"),
                    ).output.trim()
                assertEquals(v2Id, currentTarget, "Symlink 'current' must now point to $v2Id")

                println("Scenario E: Step 3 - Job 1 completes remaining stages using original pinned tools")
                // --- STEP 3: Job 1 completes remaining stages using its original pinned tools ---
                cli.execute(distro, listOf("mkdir", "-p", testOut1))
                val fsckRes1 = cli.execute(distro, listOf(job1Fsck, "--extract=$testOut1", testImg1))
                assertEquals(
                    0,
                    fsckRes1.exitCode,
                    "Job 1 fsck.erofs (pinned $v1Id) must succeed: ${fsckRes1.error}",
                )
                val extracted1 = cli.execute(distro, listOf("cat", "$testOut1/test.txt")).output.trim()
                assertEquals(sampleContent, extracted1, "Job 1 extracted content must match")

                // Finish Job 1
                coordinator.finishJob("job-extract-1")
                assertFalse(coordinator.isJobActive, "No jobs active after Job 1 finishes")

                println("Scenario E: Step 4 - Starting Job 2 and capturing new version")
                // --- STEP 4: Start Job 2: captures new version (v2Id) ---
                val job2Pins =
                    coordinator.admitJob("job-extract-2") {
                        store.paths(ToolGroupCatalog).getOrThrow()
                    }
                assertEquals(v2Id, job2Pins.versions["fsck.erofs"], "Job 2 must pin $v2Id")
                val job2Fsck = job2Pins.paths.getValue("fsck.erofs")
                assertTrue(job2Fsck.contains(v2Id), "Job 2 fsck path must point to $v2Id, got $job2Fsck")

                // Stage 1 of Job 2: extracts with new version
                cli.execute(distro, listOf("mkdir", "-p", testOut2))
                val fsckRes2 = cli.execute(distro, listOf(job2Fsck, "--extract=$testOut2", testImg1))
                assertEquals(
                    0,
                    fsckRes2.exitCode,
                    "Job 2 fsck.erofs (pinned $v2Id) must succeed: ${fsckRes2.error}",
                )
                val extracted2 = cli.execute(distro, listOf("cat", "$testOut2/test.txt")).output.trim()
                assertEquals(sampleContent, extracted2, "Job 2 extracted content must match")

                coordinator.finishJob("job-extract-2")
                println("Scenario E: Completed successfully")

                // Cleanup test extraction dirs
                cli.execute(distro, listOf("rm", "-rf", testSrc1, testImg1, testOut1, testOut2))
            }
        } finally {
            pageJob.cancel()
            heartbeatMonitor?.close()
            cli.executeHost(listOf("wsl.exe", "--set-default", originalDefault))
        }
    }

    @Test
    fun scenarioF_stopWorkOnAppClose(): Unit = runBlocking {
        // Quickstart Scenario F: graceful exit and cancellation (US6, SC-006)
        if (!assumeWslRealHost()) return@runBlocking
        val distro = System.getenv("LTI_TEST_DISTRO") ?: System.getProperty("lti.test.distro") ?: "LtiRomTest"
        val cli = WslCliExecutor()
        val detector = WslEnvironmentDetector(cli)
        val home = detector.resolveWslUserHome(distro)
        val originalDefault = detector.listDistros().firstOrNull() ?: "Ubuntu"

        var monitor: DaemonHeartbeatMonitor? = null
        var monitor2: DaemonHeartbeatMonitor? = null
        var monitor3: DaemonHeartbeatMonitor? = null

        try {
            cli.executeHost(listOf("wsl.exe", "--set-default", distro))

            val env =
                SetupEnvironment(
                    distro = distro,
                    wslVersion = 2,
                    osId = "ubuntu",
                    osVersionId = "24.04",
                    user = home.substringAfterLast('/'),
                    home = home,
                )
            ServerArtifactInstaller(cli = cli).ensureInstalled(env)

            val supervisor = WslDaemonManager(WslDaemonConfig(distroName = distro), cli = cli, detector = detector)
            val transport = KtorRemoteTransportAdapter(supervisor, supervisor.httpClient)
            val m1 =
                DaemonHeartbeatMonitor(
                    supervisor = supervisor,
                    transport = transport,
                    intervalMs = 3_000L,
                )
            monitor = m1
            m1.start()

            val store =
                KotlinToolStoreAdapter(
                    transport = transport,
                    heartbeatMonitor = m1,
                    serverDirProvider = { "$home/.ltirom/server/current" },
                )
            val toolManager =
                ToolManager(
                    port = store,
                    catalog = ToolGroupCatalog,
                )

            // Clean up any leftovers from previous runs
            cli.execute(
                distro,
                listOf(
                    "rm",
                    "-rf",
                    "$home/LtiRomTools/bin/scenario-f-tool",
                    "$home/LtiRomTools/work",
                    "$home/scenario-f-slow-repo",
                ),
            )

            // Setup slow fixture tool
            val repoPath = "$home/scenario-f-slow-repo"
            val commit =
                Fixtures.setupSlowCMakeToolFixture(
                    transport = transport,
                    repoPath = repoPath,
                    toolName = "scenario-f-tool",
                    delaySeconds = 60,
                    tag = "v1.0.0",
                ).getOrThrow()

            val group =
                Fixtures.createTestToolGroup(
                    id = "scenario-f-tool",
                    repoUrl = repoPath,
                    ref = ToolRef.Tag("v1.0.0"),
                    commit = commit,
                )

            ToolGroupCatalog.withIsolatedTestGroup(group) {
                // Step 1: Close during active build -> stops within 10 s (SC-006)
                println("Scenario F: Step 1 - Launching build and testing graceful shutdown")
                val coordinator =
                    org.ide.lti.core.data.AppShutdownCoordinator(
                        transport = transport,
                        heartbeatMonitor = m1,
                        toolManager = toolManager,
                        toolStore = store,
                    )

                val opId1 = "op-scenario-f-1"
                val req1 =
                    org.ide.lti.core.domain.tools.InstallRequest(
                        opId = opId1,
                        group = group.id,
                        label = "v1.0.0",
                        source =
                        ResolvedSource.Git(
                            repoUrl = repoPath,
                            ref = ToolRef.Tag("v1.0.0"),
                            commit = commit,
                        ),
                        recipe = group.recipe,
                        outputs = group.outputs,
                        select = true,
                        choice = ChoiceUpdate.Record,
                    )

                var buildStarted = false
                val installJob1 =
                    store.install(req1).onEach { ev ->
                        if (ev is org.ide.lti.core.domain.tools.InstallEvent.Output &&
                            (ev.text.contains("cmake") || ev.text.contains("Running build recipe"))
                        ) {
                            buildStarted = true
                        }
                    }.launchIn(this)

                val waitStart1 = System.currentTimeMillis()
                var pgrepRunning1 = ""
                while (pgrepRunning1.isBlank() && System.currentTimeMillis() - waitStart1 < 30_000L) {
                    delay(200)
                    val res =
                        transport.execute(
                            io.ltirom.tooling.core.remote.ToolExecutionRequest(
                                "sh",
                                listOf("-c", "pgrep -x sleep || true"),
                                workingDirectory = "/tmp",
                            ),
                        )
                    pgrepRunning1 = res.stdout.trim()
                }
                assertTrue(pgrepRunning1.isNotBlank(), "Build process (sleep) must be active before shutdown")

                // Verify work/<opId>/op.json was written at start
                val workDir1 = "$home/LtiRomTools/work/$opId1"
                val opJsonRes1 =
                    transport.execute(
                        io.ltirom.tooling.core.remote.ToolExecutionRequest(
                            "cat",
                            listOf("$workDir1/op.json"),
                            workingDirectory = "/tmp",
                        ),
                    )
                assertEquals(0, opJsonRes1.exitCode, "work/<opId>/op.json must exist while build runs")

                val shutdownDuration =
                    measureTimeMillis {
                        val report = coordinator.shutdown(10.seconds)
                        assertIs<org.ide.lti.core.domain.tools.ShutdownReport.Stopped>(
                            report,
                            "Shutdown report must be Stopped",
                        )
                    }
                assertTrue(shutdownDuration <= 10_000L, "Shutdown took $shutdownDuration ms, must be <= 10 000 ms")

                installJob1.cancel()

                val pgrepAfter1 =
                    transport.execute(
                        io.ltirom.tooling.core.remote.ToolExecutionRequest(
                            "sh",
                            listOf("-c", "pgrep -f '[s]leep 60' || true"),
                            workingDirectory = "/tmp",
                        ),
                    )
                assertEquals("", pgrepAfter1.stdout.trim(), "No build process left after graceful shutdown")
                assertTrue(m1.isShutdown, "Heartbeat monitor must be stopped after shutdown")

                // Step 2: Kill app mid-build / stop heartbeats -> watchdog cancels within 30 s
                println("Scenario F: Step 2 - Simulating crash / lost heartbeats mid-build")
                val opId2 = "op-scenario-f-2"
                val javaBin =
                    java.lang.ProcessHandle.current().info().command().orElseGet {
                        val javaHome = System.getProperty("java.home")
                        val isWin = System.getProperty("os.name").lowercase().contains("win")
                        if (isWin) "$javaHome\\bin\\java.exe" else "$javaHome/bin/java"
                    }
                val classPath = System.getProperty("java.class.path")
                val argFile =
                    java.io.File.createTempFile("crash-client-args", ".txt").apply {
                        deleteOnExit()
                    }
                val escapedCp = classPath.replace("\\", "/")
                argFile.writeText(
                    "-cp\n\"$escapedCp\"\norg.ide.lti.desktop.realhost.TestWatchdogCrashClient\n\"$distro\"\n\"$repoPath\"\n\"$commit\"\n\"$opId2\"\n\"$home\"",
                )

                val clientPb = ProcessBuilder(javaBin, "@${argFile.absolutePath}")
                clientPb.redirectError(ProcessBuilder.Redirect.INHERIT)
                val clientProcess = clientPb.start()

                try {
                    val reader = java.io.BufferedReader(java.io.InputStreamReader(clientProcess.inputStream))
                    val readDeadline = System.currentTimeMillis() + 45_000L
                    var clientActive = false
                    while (System.currentTimeMillis() < readDeadline) {
                        val line = reader.readLine() ?: break
                        if (line.contains("CRASH_CLIENT_ACTIVE")) {
                            clientActive = true
                            break
                        }
                    }
                    assertTrue(clientActive, "Separate client process must start build and become active")

                    // Independent probe (via cli, NOT transport) verifies build process is active in WSL
                    val pgrepActive =
                        cli.execute(
                            distro,
                            listOf("sh", "-c", "pgrep -f '[s]leep 60' || true"),
                        ).output.trim()
                    assertTrue(pgrepActive.isNotBlank(), "Build process (sleep) must be active for crash test")

                    // Abruptly terminate the client process at OS level (crash simulation).
                    // No coroutine cleanup runs, no client-side cancel request is sent, heartbeats stop completely.
                    clientProcess.destroyForcibly()
                    clientProcess.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
                    assertFalse(clientProcess.isAlive, "Separate client process must be forcibly terminated")

                    // Observe processes through an independent read-only channel (cli.execute),
                    // without using transport which could send heartbeats or re-establish admission.
                    // First, verify build process does NOT immediately die (proving client didn't cancel it):
                    val pgrepImmediatelyAfterCrash =
                        cli.execute(
                            distro,
                            listOf("sh", "-c", "pgrep -f '[s]leep 60' || true"),
                        ).output.trim()
                    assertTrue(
                        pgrepImmediatelyAfterCrash.isNotBlank(),
                        "Process must still be running immediately after client crash",
                    )

                    // Wait for watchdog to cancel within 30 s of lost heartbeats (SC-006)
                    val waitWatchdogStart = System.currentTimeMillis()
                    var pgrepAfterCrash = "running"
                    var killTimeMs = -1L
                    while (System.currentTimeMillis() - waitWatchdogStart < 45_000L) {
                        delay(1000)
                        val elapsed = System.currentTimeMillis() - waitWatchdogStart
                        val res = cli.execute(distro, listOf("sh", "-c", "pgrep -f '[s]leep 60' || true"))
                        pgrepAfterCrash = res.output.trim()
                        println("Scenario F: Watchdog wait at $elapsed ms, pgrep='$pgrepAfterCrash'")
                        if (pgrepAfterCrash.isBlank()) {
                            killTimeMs = elapsed
                            break
                        }
                    }
                    assertEquals(
                        "",
                        pgrepAfterCrash,
                        "Watchdog must kill the build process within watchdog deadline",
                    )
                    assertTrue(
                        killTimeMs >= 25_000L,
                        "Process must survive until watchdog deadline (died at $killTimeMs ms)",
                    )
                    assertTrue(
                        killTimeMs < 45_000L,
                        "Process must be killed well before sleep 60 finishes (killed at $killTimeMs ms)",
                    )
                } finally {
                    try {
                        clientProcess.destroyForcibly()
                    } catch (_: Throwable) {
                    }
                    argFile.delete()
                }

                // Step 3: Reopen -> true state shows, interrupted op reported from disk, no partial version folder
                println("Scenario F: Step 3 - Reopening client and verifying recovery")
                val m3 =
                    DaemonHeartbeatMonitor(
                        supervisor = supervisor,
                        transport = transport,
                        intervalMs = 3_000L,
                    )
                monitor3 = m3
                assertTrue(m3.awaitInitialHeartbeat(), "Client must establish admission upon reopen")
                m3.start()

                val store3 =
                    KotlinToolStoreAdapter(
                        transport = transport,
                        heartbeatMonitor = m3,
                        serverDirProvider = { "$home/.ltirom/server/current" },
                    )

                val inv = store3.status(ToolGroupCatalog).getOrThrow()
                val gState = inv.groups[group.id]
                assertNotNull(gState, "Group state must exist on reopen")
                assertNotNull(gState.interrupted, "Group state must report interrupted op on reopen")
                assertEquals(opId2, gState.interrupted?.opId)
                assertTrue(gState.installed.isEmpty(), "No partial version folder in bin/")

                // Next operation for the group cleans older work/* folders
                val commitFast =
                    Fixtures.setupWorkingCMakeToolFixture(
                        transport = transport,
                        repoPath = repoPath,
                        toolName = "scenario-f-tool",
                        probeExitCode = 0,
                        tag = "v2.0.0",
                    ).getOrThrow()

                val opId3 = "op-scenario-f-3"
                val req3 =
                    org.ide.lti.core.domain.tools.InstallRequest(
                        opId = opId3,
                        group = group.id,
                        label = "v2.0.0",
                        source =
                        ResolvedSource.Git(
                            repoUrl = repoPath,
                            ref = ToolRef.Tag("v2.0.0"),
                            commit = commitFast,
                        ),
                        recipe = group.recipe,
                        outputs = group.outputs,
                        select = true,
                        choice = ChoiceUpdate.Record,
                    )

                val events3 = mutableListOf<org.ide.lti.core.domain.tools.InstallEvent>()
                store3.install(req3).collect { events3.add(it) }
                val finished3 = events3.filterIsInstance<org.ide.lti.core.domain.tools.InstallEvent.Finished>().firstOrNull()
                assertNotNull(finished3)
                assertIs<OperationResult.Installed>(finished3.result)

                // Verify older work dirs deleted
                val oldWorkCheck1 =
                    transport.execute(
                        io.ltirom.tooling.core.remote.ToolExecutionRequest(
                            "test",
                            listOf("-d", workDir1),
                            workingDirectory = "/tmp",
                        ),
                    )
                assertTrue(oldWorkCheck1.exitCode != 0, "Older work directory 1 must be deleted by next op")

                println("Scenario F: Completed successfully")
            }
        } finally {
            monitor?.close()
            monitor2?.close()
            monitor3?.close()
            cli.executeHost(listOf("wsl.exe", "--set-default", originalDefault))
        }
    }

    @Test
    fun scenarioG_downloadedToolsVerifySha256() = runBlocking {
        // Quickstart Scenario G: Picks survive updates and deletions (FR-019)
        if (!assumeWslRealHost()) return@runBlocking
        val distro = System.getenv("LTI_TEST_DISTRO") ?: System.getProperty("lti.test.distro") ?: "LtiRomTest"
        val cli = WslCliExecutor()
        val detector = WslEnvironmentDetector(cli)
        val home = detector.resolveWslUserHome(distro)

        val originalDefault = detector.listDistros().firstOrNull() ?: "Ubuntu"
        var heartbeatMonitor: DaemonHeartbeatMonitor? = null
        try {
            cli.executeHost(listOf("wsl.exe", "--set-default", distro))

            val env =
                SetupEnvironment(
                    distro = distro,
                    wslVersion = 2,
                    osId = "ubuntu",
                    osVersionId = "24.04",
                    user = "lti",
                    home = home,
                )

            val supervisor = WslDaemonManager(WslDaemonConfig(distroName = distro), cli = cli, detector = detector)
            val transport = KtorRemoteTransportAdapter(supervisor, supervisor.httpClient)
            val monitor =
                DaemonHeartbeatMonitor(
                    supervisor = supervisor,
                    transport = transport,
                    intervalMs = 5_000L,
                )
            heartbeatMonitor = monitor
            val installer = ServerArtifactInstaller(cli = cli)
            val serverArtifactAdapter = ServerArtifactAdapter(installer = installer, daemonManager = supervisor)
            val servicePrep =
                ServicePreparation(
                    hostPrerequisitePort = WslHostPrerequisiteProbe(detector, cli),
                    supervisor = supervisor,
                    transport = transport,
                    serverArtifactPort = serverArtifactAdapter,
                )

            val distroStage = DistroStage(detector)
            val serviceStage =
                ServiceStage(
                    installer = installer,
                    supervisor = supervisor,
                    transport = transport,
                    heartbeatMonitor = monitor,
                    servicePreparation = servicePrep,
                )
            val packagesStage = PackagesStage(servicePreparation = servicePrep)

            val store =
                KotlinToolStoreAdapter(
                    transport = transport,
                    heartbeatMonitor = monitor,
                    serverDirProvider = { "$home/.ltirom/server/current" },
                )
            val toolManager =
                ToolManager(
                    port = store,
                    catalog = ToolGroupCatalog,
                )
            val toolsStage = ToolsStage(toolManager = toolManager)
            val check = EnvironmentCheck(listOf(distroStage, serviceStage, packagesStage, toolsStage))

            val readOnlyCommands = ReadOnlyCommands(cli)
            val stateFlow = MutableStateFlow(ToolchainSetupState())
            val scope = CheckScope("Ubuntu", "scenarioG-check", stateFlow) { "scenarioG-check" }

            val ctx =
                CheckContext(
                    environment = env,
                    readOnlyCommands = readOnlyCommands,
                    userHome = home,
                    workDir = "$home/LtiRomWorkDir",
                    binDir = "$home/LtiRomTools/bin",
                    extDir = "$home/LtiRomTools/external",
                    transport = transport,
                )

            monitor.start()

            val sFile = ToolSettingsFile(RemoteCommandRunner(transport), "$home/LtiRomTools")

            // Step 1: Ensure erofs-utils pick is in place (from Scenario C or explicitly installed & picked)
            val pickedTag = "v1.8.4-250126"
            val pickedCommit = "620bf019a3eb6a9a2d8fa807a3034e5cc2b38d8a"
            val erofsRecUrl = (ToolGroupCatalog.EROFS_UTILS.source as ToolSource.Git).recommendedUrl
            val pickedSource = ResolvedSource.Git(erofsRecUrl, ToolRef.Tag(pickedTag), pickedCommit)

            val currentTarget =
                cli.execute(
                    distro,
                    listOf("readlink", "$home/LtiRomTools/bin/erofs-utils/current"),
                ).output.trim()
            val currentSelections = sFile.readSelections().getOrThrow()
            val erofsSelection = currentSelections[ToolGroupId("erofs-utils")]
            val selectionGit = erofsSelection?.second as? ResolvedSource.Git
            if (currentTarget != pickedTag || erofsSelection?.first != pickedTag || selectionGit?.ref !is ToolRef.Tag) {
                val installPickRes = toolManager.installAndUse(ToolGroupId("erofs-utils"), pickedSource)
                assertTrue(
                    installPickRes is OperationResult.Installed || installPickRes is OperationResult.Reused ||
                        installPickRes is OperationResult.Used,
                )
            }

            val originalImg2sdatFolder = "sixteen-601317e"
            val origImgExists =
                cli.execute(distro, listOf("test", "-d", "$home/LtiRomTools/bin/img2sdat/$originalImg2sdatFolder")).exitCode == 0
            if (!origImgExists) {
                val origSource = ToolGroupCatalog.IMG2SDAT.source as ToolSource.Git
                val origResolved =
                    ResolvedSource.Git(
                        origSource.recommendedUrl,
                        origSource.pinnedRef,
                        origSource.pinnedCommit,
                    )
                toolManager.installAndUse(ToolGroupId("img2sdat"), origResolved)
            }

            // Step 2: Change the test catalog's pinned versions for erofs-utils and img2sdat to simulate a new Studio release
            // erofs-utils new Studio pin: v1.8.1-240810 (commit 625c4b5674edba64f74198d59745c18e6fb9dd56)
            // img2sdat new Studio pin: branch sixteen at commit fc05bbf92bc0218d86145e85145a3fbdcbad0ce6
            val newImg2sdatCommit = "fc05bbf92bc0218d86145e85145a3fbdcbad0ce6"
            val newImg2sdatVersionId = "sixteen-fc05bbf"

            val simulatedErofs =
                ToolGroupCatalog.EROFS_UTILS.copy(
                    source =
                    ToolSource.Git(
                        recommendedUrl = erofsRecUrl,
                        pinnedRef = ToolRef.Tag("v1.8.1-240810"),
                        pinnedCommit = "625c4b5674edba64f74198d59745c18e6fb9dd56",
                    ),
                )
            val simulatedImg2sdat =
                ToolGroupCatalog.IMG2SDAT.copy(
                    source =
                    ToolSource.Git(
                        recommendedUrl = (ToolGroupCatalog.IMG2SDAT.source as ToolSource.Git).recommendedUrl,
                        pinnedRef = ToolRef.Branch("sixteen"),
                        pinnedCommit = newImg2sdatCommit,
                    ),
                )
            val simulatedGroups =
                ToolGroupCatalog.DEFAULT_GROUPS.map { g ->
                    when (g.id.value) {
                        "erofs-utils" -> simulatedErofs
                        "img2sdat" -> simulatedImg2sdat
                        else -> g
                    }
                }

            ToolGroupCatalog.withIsolatedTestGroups(simulatedGroups) {
                // Step 3: Run check & prepareMissing
                val outcome1 = check.run(ctx, scope)
                val summary1 = toolManager.prepareMissing()

                // img2sdat (which had no pick) builds the new pinned version
                val imgResult = summary1.groupResults[ToolGroupId("img2sdat")]
                assertTrue(
                    imgResult is GroupSetupResult.Installed || imgResult is GroupSetupResult.Reused,
                    "img2sdat must be Installed or Reused: $imgResult",
                )
                val imgCurrent =
                    cli.execute(
                        distro,
                        listOf("readlink", "$home/LtiRomTools/bin/img2sdat/current"),
                    ).output.trim()
                assertEquals(
                    newImg2sdatVersionId,
                    imgCurrent,
                    "img2sdat current must point to new Studio pinned version $newImg2sdatVersionId",
                )

                // Previous img2sdat version directory remains intact
                val oldImgExists =
                    cli.execute(
                        distro,
                        listOf("test", "-d", "$home/LtiRomTools/bin/img2sdat/$originalImg2sdatFolder"),
                    ).exitCode
                assertEquals(0, oldImgExists, "Old img2sdat directory $originalImg2sdatFolder must remain intact")

                // erofs-utils (which has user pick) keeps user pick
                val erofsCurrent =
                    cli.execute(
                        distro,
                        listOf("readlink", "$home/LtiRomTools/bin/erofs-utils/current"),
                    ).output.trim()
                assertEquals(pickedTag, erofsCurrent, "erofs-utils must retain user pick $pickedTag")
                val erofsSelection = sFile.readSelections().getOrThrow()[ToolGroupId("erofs-utils")]
                assertEquals(
                    pickedTag,
                    erofsSelection?.first,
                    "settings.json must retain user pick for erofs-utils",
                )

                // Step 4: Delete the picked folder on disk
                val pickedDir = "$home/LtiRomTools/bin/erofs-utils/$pickedTag"
                cli.execute(distro, listOf("rm", "-rf", pickedDir))
                assertEquals(
                    1,
                    cli.execute(distro, listOf("test", "-d", pickedDir)).exitCode,
                    "Picked folder must be deleted",
                )

                // Run check again -> detected as damaged/needs repair
                val outcome2 = check.run(ctx, scope)
                val inv2 = toolManager.refresh().getOrThrow()
                val erofsStatus2 = inv2.groups[ToolGroupId("erofs-utils")]
                assertTrue(
                    erofsStatus2?.status == GroupStatus.NeedsRepair ||
                        erofsStatus2?.status == GroupStatus.NotInstalled ||
                        erofsStatus2?.status == GroupStatus.Interrupted,
                    "erofs-utils must report NeedsRepair, NotInstalled or Interrupted after deleting picked folder: ${erofsStatus2?.status}",
                )

                // Repair / prepareMissing rebuilds the PICKED version (same commit) into the same folder name
                val summary2 = toolManager.prepareMissing()
                val erofsResult2 = summary2.groupResults[ToolGroupId("erofs-utils")]
                assertTrue(
                    erofsResult2 is GroupSetupResult.Installed || erofsResult2 is GroupSetupResult.Reused,
                    "erofs-utils must be rebuilt: $erofsResult2",
                )

                // Verify picked folder is restored
                assertEquals(
                    0,
                    cli.execute(distro, listOf("test", "-d", pickedDir)).exitCode,
                    "Picked folder must be restored",
                )
                val erofsCurrentAfterRepair =
                    cli.execute(
                        distro,
                        listOf("readlink", "$home/LtiRomTools/bin/erofs-utils/current"),
                    ).output.trim()
                assertEquals(
                    pickedTag,
                    erofsCurrentAfterRepair,
                    "Current must remain the picked version after repair, not Studio's version",
                )
                val erofsPickAfterRepair = sFile.readSelections().getOrThrow()[ToolGroupId("erofs-utils")]
                assertEquals(pickedTag, erofsPickAfterRepair?.first, "Pick in settings.json must remain $pickedTag")

                // Step 5: Invoke toolManager.followStudio on erofs-utils
                val followRes = toolManager.followStudio(ToolGroupId("erofs-utils"))
                assertTrue(
                    followRes is OperationResult.Used || followRes is OperationResult.Installed,
                    "followStudio must succeed: $followRes",
                )

                // Pick is removed from settings.json
                val selectionsAfterFollow = sFile.readSelections().getOrThrow()
                assertFalse(
                    selectionsAfterFollow.containsKey(ToolGroupId("erofs-utils")),
                    "erofs-utils entry must be removed from settings.json",
                )

                // erofs-utils moves to Studio's pinned version (v1.8.1-240810)
                val erofsCurrentAfterFollow =
                    cli.execute(
                        distro,
                        listOf("readlink", "$home/LtiRomTools/bin/erofs-utils/current"),
                    ).output.trim()
                assertEquals(
                    "v1.8.1-240810",
                    erofsCurrentAfterFollow,
                    "erofs-utils must point to Studio's pinned version",
                )
                assertEquals(
                    0,
                    cli.execute(
                        distro,
                        listOf("test", "-x", "$home/LtiRomTools/bin/erofs-utils/current/mkfs.erofs"),
                    ).exitCode,
                    "mkfs.erofs must be executable",
                )
            }
        } finally {
            heartbeatMonitor?.close()
            cli.executeHost(listOf("wsl.exe", "--set-default", originalDefault))
        }
    }
}
