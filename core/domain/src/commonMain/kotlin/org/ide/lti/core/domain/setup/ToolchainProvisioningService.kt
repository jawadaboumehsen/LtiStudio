/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.setup

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.ide.lti.core.model.setup.DistroStatus
import org.ide.lti.core.model.setup.SetupAttemptRecord

/**
 * Stages in the environment check and toolchain provisioning pipeline.
 */
public enum class SetupStepStage(public val displayName: String) {
    WSL_DETECTION("WSL 2 Runtime"),
    SYSTEM_PACKAGES("System Packages"),
    SERVER_CONNECTIVITY("Server Bridge"),
    SYSTEM_DIAGNOSTICS("Pre-Flight Doctor"),
    REPO_SYNCHRONIZATION("Source Repositories"),
    TOOLCHAIN_COMPILATION("Toolchain Binaries"),
}

/**
 * Execution status for an individual step or the overall setup pipeline.
 */
public enum class StepStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    WARNING,
    FAILED,
}

/**
 * Execution environment scope of a toolchain component.
 */
public enum class ToolScope(public val displayName: String) {
    WSL2("WSL 2 Linux"),
    HOST("Windows Host"),
}

/**
 * Categorization of a toolchain binary by its functional role in the ROM engineering pipeline.
 */
public enum class ToolCategory(public val displayName: String) {
    DYNAMIC_PARTITIONS("Dynamic Partitions"),
    FILESYSTEM_AND_IMAGES("Filesystem & Images"),
    BOOT_AND_KERNEL("Boot & Kernel"),
    SIGNING_AND_SECURITY("Signing & Security"),
    BRIDGE_AND_FLASHING("Bridge & Flashing"),
    PACKAGING_AND_TOOLS("Packaging & Tools"),
}

/**
 * Categorization of a pre-flight doctor diagnostic check by the subsystem it verifies.
 */
public enum class DiagnosticCategory(public val displayName: String) {
    COMPILERS("Compilers"),
    LIBRARIES("Libraries"),
    RUNTIMES("Runtimes"),
    OS_UTILITIES("OS Utilities"),
    KERNEL_AND_MOUNT("Kernel & Mount"),
    STORAGE("Storage"),
    SECURITY("Security"),
    TOOLCHAIN("Toolchain"),
    PERFORMANCE("Performance"),
}

/**
 * Individual tool component item modeled after Android Studio SDK Tools & JetBrains Toolchains.
 */
public data class ToolComponentItem(
    val id: String,
    val name: String,
    val category: ToolCategory,
    val binaryName: String,
    val scope: ToolScope = ToolScope.WSL2,
    val version: String? = null,
    val path: String? = null,
    val status: StepStatus = StepStatus.PENDING,
    val isCore: Boolean = true,
    val sizeBytes: Long? = null,
    val capabilities: List<String> = emptyList(),
    val lastTested: String? = null,
)

/**
 * Individual pre-flight diagnostic item mirroring the system doctor.
 */
public data class DiagnosticCheckItem(
    val id: String,
    val title: String,
    val status: StepStatus = StepStatus.PENDING,
    val detail: String = "",
    val remediation: String? = null,
    val category: DiagnosticCategory = DiagnosticCategory.TOOLCHAIN,
    val copyableCommand: String? = null,
    val isOptionalForRuntime: Boolean = false,
)

/**
 * Provenance of a setup step's result: restored from persistent history or measured live in this process.
 */
public enum class StepProvenance {
    RESTORED,
    LIVE,
}

/**
 * Detail metadata for a specific setup step.
 */
public data class SetupStepDetail(
    val stage: SetupStepStage,
    val title: String,
    val description: String,
    val status: StepStatus = StepStatus.PENDING,
    val error: String? = null,
    val durationMs: Long = 0L,
    val provenance: StepProvenance = StepProvenance.LIVE,
    val verifiedAtEpochMs: Long? = null,
    val runId: String? = null,
    val failureCategory: org.ide.lti.core.domain.ports.DaemonFailureCategory? = null,
    val toolchainFailureCategory: org.ide.lti.core.domain.ports.ToolchainFailureCategory? = null,
    val startedAt: Long? = null,
)

/**
 * Immutable reactive snapshot of the environment and toolchain setup status.
 */
public data class ToolchainSetupState(
    val steps: List<SetupStepDetail> = defaultSteps(),
    val diagnostics: List<DiagnosticCheckItem> = defaultDiagnostics(),
    val isAvbKeyProvisioned: Boolean = false,
    val avbKeyPath: String? = null,
    val currentStage: SetupStepStage? = null,
    val isRunning: Boolean = false,
    val isChecking: Boolean = false,
    val logs: List<String> = emptyList(),
    val activeLogLine: String? = null,
    val activeDistro: String? = null,
    val installedDistros: List<String> = emptyList(),
    /** Classification of every WSL distro from the last live detection; empty until one ran. */
    val distroStatuses: List<DistroStatus> = emptyList(),
    val daemonPingMs: Long? = null,
    /**
     * Epoch millis of the last time the environment was actually confirmed ready via a live
     * verifyEnvironment() probe or a completed runFullSetup() - not merely restored from a persisted
     * flag. Null means never verified in a way that recorded a timestamp.
     */
    val lastVerifiedTimestamp: Long? = null,
    val checkedAt: Long? = null,
    val lastReadyAt: Long? = null,
    val toolsMatrix: List<ToolComponentItem> = defaultToolsMatrix(),
    val publishedToolIds: Set<String> = emptySet(),
    val unpublishedToolIds: Set<String> = emptySet(),
    // TODO: not yet populated by any ToolchainProvisioningService implementation -- the durable-run
    // reattach indicator UI that reads this (EnvironmentStepContent.kt) will always see an empty map
    // until WslToolchainProvisioner threads RunHandle.runId out of SubmoduleSyncEngine/
    // ToolchainBuildEngine's startRun() calls and into a _state.update.
    val stepRunIds: Map<SetupStepStage, String> = emptyMap(),
    val storageAvailableBytes: Long? = null,
    val storageMeasuredAt: Long? = null,
    val activeOperationId: String? = null,
    /**
     * Journaled attempt that has not been reconciled against server truth yet (US3). Non-null after a
     * restart or dropped connection until [ToolchainProvisioningService.recover] archives it; while set,
     * every mutation is refused with a typed Interrupted outcome. Reconnect reuses this attemptId.
     */
    val pendingAttemptId: String? = null,
    /** The journal record behind [pendingAttemptId] (kind, status, evidence) for the Recovery view. */
    val pendingAttempt: SetupAttemptRecord? = null,
    /** Durable recovery block reason: the journal is ambiguous and needs user-directed recovery. */
    val recoveryBlockReason: String? = null,
    val userHome: String? = null,
    val workDirLinuxPath: String? = null,
) {
    val isBusy: Boolean get() = isRunning || isChecking || activeOperationId != null

    /**
     * True if every step was verified live in this process and is SUCCESS or tolerated WARNING.
     */
    val isAllReady: Boolean
        get() = steps.isNotEmpty() &&
            steps.all { step ->
                step.provenance == StepProvenance.LIVE &&
                    (step.status == StepStatus.SUCCESS || step.status == StepStatus.WARNING)
            }

    /**
     * True if the local LtiRomServer daemon bridge is connected and responsive.
     */
    val isServerConnected: Boolean
        get() = steps.firstOrNull { it.stage == SetupStepStage.SERVER_CONNECTIVITY }?.status == StepStatus.SUCCESS

    /**
     * True if native toolchain binaries have been built/verified in ~/LtiRomTools/bin.
     */
    val isToolsVerified: Boolean
        get() = steps.firstOrNull { it.stage == SetupStepStage.TOOLCHAIN_COMPILATION }?.status == StepStatus.SUCCESS

    /**
     * True if workspace launch prerequisites are satisfied (strictly equivalent to [isAllReady]).
     */
    val canLaunchWorkspace: Boolean
        get() = isAllReady

    val healthScorePercentage: Int
        get() {
            val total = diagnostics.size
            if (total == 0) return 100
            val passing = diagnostics.count { it.status == StepStatus.SUCCESS }
            val warnings = diagnostics.count { it.status == StepStatus.WARNING }
            return ((passing * 100 + warnings * 60) / total).coerceIn(0, 100)
        }

    public companion object {
        public fun defaultSteps(): List<SetupStepDetail> = listOf(
            SetupStepDetail(
                stage = SetupStepStage.WSL_DETECTION,
                title = "WSL 2 Runtime",
                description = "Detect Windows Subsystem for Linux installation and active distribution.",
            ),
            SetupStepDetail(
                stage = SetupStepStage.SYSTEM_PACKAGES,
                title = "System Packages",
                description = "Prerequisite system packages and Java runtime.",
            ),
            SetupStepDetail(
                stage = SetupStepStage.SERVER_CONNECTIVITY,
                title = "Server Bridge",
                description = "Connect to local LtiRomServer daemon and verify API health.",
            ),
            SetupStepDetail(
                stage = SetupStepStage.SYSTEM_DIAGNOSTICS,
                title = "Pre-Flight Doctor",
                description = "Verify compilers, C++ build libraries, Python crypto, dual JDK, FUSE, and storage.",
            ),
            SetupStepDetail(
                stage = SetupStepStage.REPO_SYNCHRONIZATION,
                title = "Source Repositories",
                description = "Validate LtiRomTools/external and synchronize pinned submodules.",
            ),
            SetupStepDetail(
                stage = SetupStepStage.TOOLCHAIN_COMPILATION,
                title = "Toolchain Binaries",
                description = "Verify and compile tool binaries in LtiRomTools/bin.",
            ),
        )

        public fun defaultDiagnostics(): List<DiagnosticCheckItem> = listOf(
            DiagnosticCheckItem(
                id = "host_compilers",
                title = "Host Compilers & Build Engine",
                category = DiagnosticCategory.COMPILERS,
                detail = "cmake, make, clang/gcc, and clang++/g++ for compiling native C++ tools from source.",
            ),
            DiagnosticCheckItem(
                id = "host_libraries",
                title = "Native C/C++ Build Dependencies",
                category = DiagnosticCategory.LIBRARIES,
                detail = "pkg-config, protobuf, brotli, lz4, zstd, pcre2, libusb, uuid, and libfuse3 development " +
                    "headers.",
            ),
            DiagnosticCheckItem(
                id = "python_crypto",
                title = "Python 3 Runtime & Cryptography",
                category = DiagnosticCategory.RUNTIMES,
                detail = "Python 3, pip, cryptography, and pyasn1 for AVB 2.0 signing, mkbootimg, and img2sdat.",
            ),
            DiagnosticCheckItem(
                id = "jdk_dual",
                title = "Dual JDK Runtimes & Compiler",
                category = DiagnosticCategory.RUNTIMES,
                detail = "Java and javac compiler for OpenJDK 17 (apktool/signapk) and OpenJDK 21 (toolchain build).",
            ),
            DiagnosticCheckItem(
                id = "selinux_attr",
                title = "SELinux Extended Attributes",
                category = DiagnosticCategory.OS_UTILITIES,
                detail = "attr and getfattr utilities for reading and preserving security.selinux file contexts.",
            ),
            DiagnosticCheckItem(
                id = "archive_tools",
                title = "Archive & Compression Tools",
                category = DiagnosticCategory.OS_UTILITIES,
                detail = "zip and unzip for firmware OTA unpacking and flashable recovery package generation.",
            ),
            DiagnosticCheckItem(
                id = "transfer_tools",
                title = "Host Transfer & Sync Utilities",
                category = DiagnosticCategory.OS_UTILITIES,
                detail = "curl, git, rsync, and tar for prebuilt tool acquisition and workspace synchronization.",
            ),
            DiagnosticCheckItem(
                id = "sudo_noninteractive",
                title = "Non-Interactive Sudo Elevation",
                category = DiagnosticCategory.OS_UTILITIES,
                detail = "Non-interactive passwordless sudo for headless package management and automated system " +
                    "configuration.",
                isOptionalForRuntime = true,
            ),
            DiagnosticCheckItem(
                id = "dev_fuse",
                title = "Kernel FUSE & Userspace Runtime",
                category = DiagnosticCategory.KERNEL_AND_MOUNT,
                detail = "/dev/fuse device node and fuse3/fusermount userspace tools for erofsfuse mounting.",
            ),
            DiagnosticCheckItem(
                id = "loop_mount",
                title = "Linux Loop Devices & Mount Elevation",
                category = DiagnosticCategory.KERNEL_AND_MOUNT,
                detail = "/dev/loop-control and non-interactive sudo elevation for ext4/f2fs partition mounting.",
                isOptionalForRuntime = true,
            ),
            DiagnosticCheckItem(
                id = "disk_headroom",
                title = "ext4 Storage Headroom (>= 45 GB)",
                category = DiagnosticCategory.STORAGE,
                detail = "Sufficient disk space in ~/LtiRomWorkDir for worktree staging.",
            ),
            DiagnosticCheckItem(
                id = "openssl_tools",
                title = "OpenSSL Cryptography Tooling",
                category = DiagnosticCategory.SECURITY,
                detail = "OpenSSL for AVB 2.0 RSA-4096 machine key generation and verification.",
            ),
            DiagnosticCheckItem(
                id = "toolchain_binaries",
                title = "Compiled Toolchain Binaries Integrity",
                category = DiagnosticCategory.TOOLCHAIN,
                detail = "Integrity of the 14 core project tools in ~/LtiRomTools/bin.",
            ),
            DiagnosticCheckItem(
                id = "build_cache",
                title = "Build Cache Accelerator (ccache)",
                category = DiagnosticCategory.PERFORMANCE,
                detail = "Optional compiler cache for accelerating C/C++ submodule rebuilds.",
                isOptionalForRuntime = true,
            ),
        )

        public fun defaultToolsMatrix(): List<ToolComponentItem> = listOf(
            // Dynamic Partitions
            ToolComponentItem(
                id = "lpmake",
                name = "LP Make",
                category = ToolCategory.DYNAMIC_PARTITIONS,
                binaryName = "lpmake",
                capabilities = listOf("create_super_image", "metadata_geometry", "extents"),
                isCore = true,
            ),
            ToolComponentItem(
                id = "lpunpack",
                name = "LP Unpack",
                category = ToolCategory.DYNAMIC_PARTITIONS,
                binaryName = "lpunpack",
                capabilities = listOf("unpack_super_image", "extract_partitions"),
                isCore = true,
            ),
            ToolComponentItem(
                id = "lpdump",
                name = "LP Dump",
                category = ToolCategory.DYNAMIC_PARTITIONS,
                binaryName = "lpdump",
                capabilities = listOf("dump_metadata", "slot_metadata", "verify_super"),
                isCore = true,
            ),
            ToolComponentItem(
                id = "lpadd",
                name = "LP Add",
                category = ToolCategory.DYNAMIC_PARTITIONS,
                binaryName = "lpadd",
                capabilities = listOf("add_partition", "resize_group"),
                isCore = false,
            ),
            ToolComponentItem(
                id = "lpflash",
                name = "LP Flash",
                category = ToolCategory.DYNAMIC_PARTITIONS,
                binaryName = "lpflash",
                capabilities = listOf("flash_partition", "dynamic_remap"),
                isCore = false,
            ),

            // Filesystem & Images
            ToolComponentItem(
                id = "mkfs.erofs",
                name = "Make EROFS",
                category = ToolCategory.FILESYSTEM_AND_IMAGES,
                binaryName = "mkfs.erofs",
                capabilities = listOf("create_erofs_image", "lz4_compression", "dedupe"),
                isCore = true,
            ),
            ToolComponentItem(
                id = "dump.erofs",
                name = "Dump EROFS",
                category = ToolCategory.FILESYSTEM_AND_IMAGES,
                binaryName = "dump.erofs",
                capabilities = listOf("inspect_superblock", "list_inodes", "stats"),
                isCore = true,
            ),
            ToolComponentItem(
                id = "fsck.erofs",
                name = "FSCK EROFS",
                category = ToolCategory.FILESYSTEM_AND_IMAGES,
                binaryName = "fsck.erofs",
                capabilities = listOf("check_integrity", "validate_blocks"),
                isCore = true,
            ),
            ToolComponentItem(
                id = "erofsfuse",
                name = "EROFS FUSE",
                category = ToolCategory.FILESYSTEM_AND_IMAGES,
                binaryName = "erofsfuse",
                capabilities = listOf("userspace_mount", "read_only_vfs"),
                isCore = true,
            ),
            ToolComponentItem(
                id = "mke2fs",
                name = "Make EXT4",
                category = ToolCategory.FILESYSTEM_AND_IMAGES,
                binaryName = "mke2fs",
                capabilities = listOf("format_ext4", "journaling", "sparse_ext4"),
                isCore = true,
            ),
            ToolComponentItem(
                id = "e2fsdroid",
                name = "E2FS Droid",
                category = ToolCategory.FILESYSTEM_AND_IMAGES,
                binaryName = "e2fsdroid",
                capabilities = listOf("fs_config", "ext4_inject", "selinux_contexts"),
                isCore = true,
            ),
            ToolComponentItem(
                id = "mkuserimg_mke2fs",
                name = "Make UserImg EXT4",
                category = ToolCategory.FILESYSTEM_AND_IMAGES,
                binaryName = "mkuserimg_mke2fs",
                capabilities = listOf("build_ext4_image", "file_contexts"),
                isCore = false,
            ),
            ToolComponentItem(
                id = "make_f2fs",
                name = "Make F2FS",
                category = ToolCategory.FILESYSTEM_AND_IMAGES,
                binaryName = "make_f2fs",
                capabilities = listOf("format_f2fs", "flash_friendly_fs"),
                isCore = false,
            ),
            ToolComponentItem(
                id = "mkf2fsuserimg",
                name = "Make F2FS UserImg",
                category = ToolCategory.FILESYSTEM_AND_IMAGES,
                binaryName = "mkf2fsuserimg",
                capabilities = listOf("build_f2fs_image", "android_sparse"),
                isCore = false,
            ),
            ToolComponentItem(
                id = "sload_f2fs",
                name = "SLOAD F2FS",
                category = ToolCategory.FILESYSTEM_AND_IMAGES,
                binaryName = "sload_f2fs",
                capabilities = listOf("populate_f2fs", "selinux_injector"),
                isCore = false,
            ),
            ToolComponentItem(
                id = "simg2img",
                name = "SIMG to IMG",
                category = ToolCategory.FILESYSTEM_AND_IMAGES,
                binaryName = "simg2img",
                capabilities = listOf("unsparse", "raw_extents"),
                isCore = true,
            ),
            ToolComponentItem(
                id = "img2simg",
                name = "IMG to SIMG",
                category = ToolCategory.FILESYSTEM_AND_IMAGES,
                binaryName = "img2simg",
                capabilities = listOf("convert_sparse", "block_chunking"),
                isCore = true,
            ),
            ToolComponentItem(
                id = "ext2simg",
                name = "EXT2 to SIMG",
                category = ToolCategory.FILESYSTEM_AND_IMAGES,
                binaryName = "ext2simg",
                capabilities = listOf("convert_ext4_sparse"),
                isCore = false,
            ),
            ToolComponentItem(
                id = "append2simg",
                name = "Append to SIMG",
                category = ToolCategory.FILESYSTEM_AND_IMAGES,
                binaryName = "append2simg",
                capabilities = listOf("append_sparse_chunk"),
                isCore = false,
            ),
            ToolComponentItem(
                id = "img2sdat",
                name = "IMG to SDAT",
                category = ToolCategory.FILESYSTEM_AND_IMAGES,
                binaryName = "img2sdat",
                capabilities = listOf("convert_block_sparse", "transfer_list"),
                isCore = true,
            ),

            // Boot & Kernel
            ToolComponentItem(
                id = "mkbootimg",
                name = "Make BootImg",
                category = ToolCategory.BOOT_AND_KERNEL,
                binaryName = "mkbootimg",
                capabilities = listOf("create_boot_image", "header_v4", "init_boot"),
                isCore = true,
            ),
            ToolComponentItem(
                id = "unpack_bootimg",
                name = "Unpack BootImg",
                category = ToolCategory.BOOT_AND_KERNEL,
                binaryName = "unpack_bootimg",
                capabilities = listOf("unpack_boot", "extract_kernel", "extract_ramdisk"),
                isCore = true,
            ),
            ToolComponentItem(
                id = "repack_bootimg",
                name = "Repack BootImg",
                category = ToolCategory.BOOT_AND_KERNEL,
                binaryName = "repack_bootimg",
                capabilities = listOf("repack_boot", "kernel_cmdline", "pagesize"),
                isCore = true,
            ),
            ToolComponentItem(
                id = "mkbootfs",
                name = "Make BootFS",
                category = ToolCategory.BOOT_AND_KERNEL,
                binaryName = "mkbootfs",
                capabilities = listOf("create_ramdisk", "cpio_archive"),
                isCore = false,
            ),
            ToolComponentItem(
                id = "mkdtboimg",
                name = "Make DTBO Img",
                category = ToolCategory.BOOT_AND_KERNEL,
                binaryName = "mkdtboimg",
                capabilities = listOf("create_dtbo_image", "device_tree_table"),
                isCore = false,
            ),

            // Signing & Security
            ToolComponentItem(
                id = "avbtool",
                name = "AVB Tool",
                category = ToolCategory.SIGNING_AND_SECURITY,
                binaryName = "avbtool",
                capabilities = listOf("make_vbmeta_image", "add_hash_footer", "rsa4096_signing"),
                isCore = true,
            ),
            ToolComponentItem(
                id = "signapk",
                name = "Sign APK",
                category = ToolCategory.SIGNING_AND_SECURITY,
                binaryName = "signapk",
                capabilities = listOf("sign_apk", "sign_ota", "v1_v2_v3_signatures"),
                isCore = true,
            ),
            ToolComponentItem(
                id = "fec",
                name = "FEC",
                category = ToolCategory.SIGNING_AND_SECURITY,
                binaryName = "fec",
                capabilities = listOf("encode_forward_error_correction", "decode_fec"),
                isCore = false,
            ),

            // Bridge & Flashing
            ToolComponentItem(
                id = "adb",
                name = "Android Debug Bridge",
                category = ToolCategory.BRIDGE_AND_FLASHING,
                binaryName = "adb",
                capabilities = listOf("devices", "push", "pull", "shell", "logcat"),
                isCore = true,
            ),
            ToolComponentItem(
                id = "fastboot",
                name = "Fastboot",
                category = ToolCategory.BRIDGE_AND_FLASHING,
                binaryName = "fastboot",
                capabilities = listOf("flash", "boot", "getvar", "reboot_fastbootd"),
                isCore = true,
            ),

            // Packaging & Tools
            ToolComponentItem(
                id = "aapt2",
                name = "AAPT2",
                category = ToolCategory.PACKAGING_AND_TOOLS,
                binaryName = "aapt2",
                capabilities = listOf("compile", "link", "dump_badging"),
                isCore = true,
            ),
            ToolComponentItem(
                id = "apktool",
                name = "Apktool",
                category = ToolCategory.PACKAGING_AND_TOOLS,
                binaryName = "apktool",
                capabilities = listOf("decode_resources", "build_apk", "smali_disasm"),
                isCore = true,
            ),
            ToolComponentItem(
                id = "zipalign",
                name = "ZipAlign",
                category = ToolCategory.PACKAGING_AND_TOOLS,
                binaryName = "zipalign",
                capabilities = listOf("align_zip_4byte", "page_align_so", "verify"),
                isCore = true,
            ),
            ToolComponentItem(
                id = "payload-dumper-go",
                name = "Payload Dumper Go",
                category = ToolCategory.PACKAGING_AND_TOOLS,
                binaryName = "payload-dumper-go",
                capabilities = listOf("extract_payload_bin", "concurrent_chunk_decompression"),
                isCore = true,
            ),
            ToolComponentItem(
                id = "gh",
                name = "GitHub CLI",
                category = ToolCategory.PACKAGING_AND_TOOLS,
                binaryName = "gh",
                capabilities = listOf("repo_clone", "release_download", "api"),
                isCore = false,
            ),
        )
    }
}

/**
 * Domain Service contract for orchestrating environment validation and toolchain setup.
 *
 * Adheres to:
 * - Single Responsibility Principle: coordinates environment checks and provisioning.
 * - Dependency Inversion Principle: UI layers depend on this abstraction, not concrete WSL runners.
 */
public interface ToolchainProvisioningService {
    /** Reactive stream of setup state updates */
    public val state: StateFlow<ToolchainSetupState>

    /** Verifies the environment using live probes and updates [state]. */
    public suspend fun verifyEnvironment(): ToolchainSetupState

    /** Probes current status without mutating environment. Delegates to [verifyEnvironment]. */
    public suspend fun checkStatus(): ToolchainSetupState = verifyEnvironment()

    /**
     * Provisions or verifies the machine-local AVB 2.0 RSA-4096 signing key. This is an explicit,
     * user-initiated action only: no setup, retry, remediation or repair plan ever invokes it
     * (FR-003 "Setup never silently creates legacy machine keys").
     */
    public suspend fun provisionAvbKey(): Boolean

    // ---- Plan-driven operation contract ----
    //
    // Every environment mutation (full setup, stage retry, Auto-Fix remediation, tool repair, cache
    // reset) is previewed with [prepare] and executed only by [confirm]; there is no no-argument
    // entry point that starts an unpreviewed mutation. These members are deliberately abstract: no
    // default body may fabricate a plan or report success on an implementer's behalf. "A normal
    // return is not success unless the typed outcome is Succeeded." An implementation that has not
    // wired a behaviour yet must return a typed non-success outcome with a reason, never a
    // synthesized [SetupOutcome.Succeeded].

    /**
     * Prepares an immutable [SetupPlan] describing the changes a confirmed operation would make.
     * Preparing never mutates the environment.
     */
    public suspend fun prepare(
        kind: SetupPlanKind,
        targetId: String? = null,
        autoDoctorEnabled: Boolean = true,
    ): SetupPlan

    /**
     * Confirms and executes the operation described by [planId] and [revisionHash].
     * "Confirmation requires the displayed plan ID and unchanged revision hash."
     */
    public suspend fun confirm(planId: String, revisionHash: String): SetupOutcome

    /**
     * Resumes an operation paused in [SetupOutcome.AwaitingUserAction] after external authorization.
     * Re-probes required packages and continues the remaining plan actions upon live verification.
     */
    public suspend fun resume(planId: String): SetupOutcome

    /** Observes the most recent typed outcome of the operation owner; null while nothing has run. */
    public fun observe(): Flow<SetupOutcome?>

    /**
     * Observes the stream of log events from environment checks, mutations, and tool tests.
     * Bounded to at most 10,000 lines and 8 MiB payload (FR-011, FR-012).
     */
    public fun observeActivity(): Flow<SetupLogEvent>

    /**
     * Attempts to reconcile an in-flight or interrupted attempt against durable/server truth.
     * Never resubmits an ambiguous receipt.
     */
    public suspend fun recover(attemptId: String? = null): SetupOutcome

    /** Explicitly cancels the active operation via the run cancel API; detach is not cancel. */
    public suspend fun cancel(): SetupOutcome

    /**
     * Tests an individual tool binary; success only from an executed probe with an accepted exit code.
     * A test acquires the environment operation owner (Busy when another operation is active) but
     * needs no mutation preview.
     */
    public suspend fun testTool(toolId: String): SetupOutcome

    /**
     * Makes [distro] the environment for this session (the WSL row picker) and re-runs the check
     * against it. Only a distro classified usable by the last detection is accepted.
     */
    public suspend fun selectDistro(distro: String): ToolchainSetupState =
        throw UnsupportedOperationException("Distro selection is not supported by this implementation")

    /** Sets progress for a long-running step (T063, contracts/ui-states.md § 2-3). */
    public fun setStepProgress(stage: SetupStepStage, currentItem: String, index: Int, total: Int) {}

    /** Retrieves the persisted attempt log text (T067, T068). Returns null if not found. */
    public suspend fun getAttemptLog(attemptId: String? = null): String? = null
}
