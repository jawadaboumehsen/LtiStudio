/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.ide.lti.core.domain.ports.FileStat
import org.ide.lti.core.domain.ports.PipelineExecutionPort
import org.ide.lti.core.domain.ports.RunHandle
import org.ide.lti.core.domain.ports.StepEvent
import org.ide.lti.core.domain.ports.StepStatus
import org.ide.lti.core.domain.ports.VendoredFile
import org.ide.lti.core.domain.ports.VendoredFilePort
import org.ide.lti.core.model.run.StepCommand
import org.ide.lti.core.model.workspace.Workspace

/**
 * A scripted execution service: keeps a virtual workspace filesystem and answers every tool the
 * six stages use with realistic output, so the orchestrator's re-plan/capture logic runs end to
 * end without WSL. Behaviour can be altered per test through [exitCodes] and [stdoutOverrides].
 */
@Suppress("CyclomaticComplexMethod", "ReturnCount", "MaxLineLength")
class SimulatedExecutionPort(private val wsRoot: String) : PipelineExecutionPort {
    val files = mutableMapOf<String, ByteArray>()
    val durableSteps = mutableListOf<StepCommand>()
    val syncCalls = mutableListOf<StepCommand>()
    val exitCodes = mutableMapOf<String, Int>()
    val stdoutOverrides = mutableMapOf<String, String>()
    var freeBytes: Long? = 200L * 1024 * 1024 * 1024

    /** Digest reported for the firmware archive so checksum verification can be exercised. */
    var archiveSha256: String? = null
    private var handleCounter = 0
    private val pending = mutableMapOf<String, ToolResult>()

    init {
        put("firmware/extracted/system/system/build.prop", STOCK_BUILD_PROP)
        put("work/system/system/build.prop", STOCK_BUILD_PROP)
    }

    fun put(rel: String, text: String) {
        files["$wsRoot/$rel"] = text.encodeToByteArray()
    }
    fun text(rel: String): String? = files["$wsRoot/$rel"]?.decodeToString()
    fun has(rel: String): Boolean = files.containsKey("$wsRoot/$rel")

    val statuses = mutableMapOf<String, StepStatus>()
    val observedFlows = mutableMapOf<String, (Long) -> Flow<StepEvent>>()

    override suspend fun startStep(ws: Workspace, step: StepCommand, idempotencyKey: String): RunHandle {
        durableSteps += step
        val id = "srv-${++handleCounter}"
        pending[id] = simulate(step)
        return RunHandle(id, "RUNNING", 0L)
    }

    override fun observe(serverRunId: String, fromSeq: Long): Flow<StepEvent> {
        val custom = observedFlows[serverRunId]
        if (custom != null) {
            return custom(fromSeq)
        }
        val result = pending.remove(serverRunId) ?: return flowOf(StepEvent.Finished(-1))
        return flowOf(StepEvent.Output(1L, result.stdout), StepEvent.Heartbeat, StepEvent.Finished(result.exitCode))
    }

    override suspend fun status(serverRunId: String): StepStatus? = statuses[serverRunId]
    override suspend fun cancel(serverRunId: String): Boolean = true

    override suspend fun uploadFile(ws: Workspace, relPath: String, bytes: ByteArray): Boolean {
        files["$wsRoot/$relPath"] = bytes
        return true
    }

    override suspend fun readFile(ws: Workspace, relPath: String): String? = text(relPath)

    override suspend fun stat(ws: Workspace, relPath: String): FileStat {
        val bytes = files["$wsRoot/$relPath"] ?: return FileStat(relPath, exists = false)
        return FileStat(relPath, exists = true, sizeBytes = bytes.size.toLong())
    }

    override suspend fun execute(ws: Workspace, command: StepCommand): ToolResult {
        syncCalls += command
        return simulate(command)
    }

    override suspend fun checkAvailableDiskSpace(ws: Workspace): Long? = freeBytes

    private fun simulate(cmd: StepCommand): ToolResult {
        val key = "${cmd.toolId} ${cmd.args.joinToString(" ")}"
        val forced = exitCodes.entries.firstOrNull { key.startsWith(it.key) }?.value
        val override = stdoutOverrides.entries.firstOrNull { key.startsWith(it.key) }?.value
        val stdout = override ?: defaultStdout(cmd)
        return ToolResult(forced ?: 0, stdout)
    }

    private fun defaultStdout(cmd: StepCommand): String {
        val args = cmd.args
        return when (cmd.toolId) {
            "curl" -> "".also { files[args[args.indexOf("-o") + 1]] = ByteArray(1_000_000) }
            "mv" -> "".also { files.remove(args[0])?.let { files[args[1]] = it } }
            "cp" -> "".also { copy(args) }
            "mkdir", "rm", "rsync", "find", "unzip", "payload-dumper-go", "lpunpack", "erofsfuse", "fusermount3",
            "truncate", "simg2img", "lpmake", "brotli", "zip", "unpack_bootimg", "chmod",
            -> defaultProduce(cmd)
            "file" -> "application/zip"
            "sha256sum" -> {
                val path = args.last()
                val digest = archiveSha256?.takeIf { "/firmware/downloaded/" in path } ?: fakeDigest(path, 64)
                "$digest  $path"
            }
            "sha1sum" -> "${fakeDigest(args.last(), 40)}  ${args.last()}"
            "stat" -> (files[args.last()]?.size ?: 0).toString()
            "cat" -> files[args.last()]?.decodeToString() ?: ""
            "xxd" -> "3aff26ed"
            "getfattr" -> getfattrDump(args.last())
            "mkfs.erofs" -> "".also { files[args[args.size - 2]] = ByteArray(IMAGE_BYTES) }
            "img2sdat" -> "".also {
                val name = args.last().substringAfterLast('/').removeSuffix(".img")
                files["${args[1]}/$name.new.dat"] = ByteArray(10)
                files["${args[1]}/$name.transfer.list"] = ByteArray(5)
            }
            "signapk" -> "".also { files[args.last()] = ByteArray(ZIP_BYTES) }
            "avbtool" -> avbtool(args)
            else -> ""
        }
    }

    private fun defaultProduce(cmd: StepCommand): String {
        val args = cmd.args
        when (cmd.toolId) {
            "find" -> if ("stat" in args) return statDump(args[0])
            "unzip" -> when {
                args[0] == "-l" -> return zipListing()
                args[0] == "-o" && args.size == 4 -> { // raw archive extracted into a directory
                    val dir = args.last()
                    BOOT.forEach { files["$dir/$it.img"] = ByteArray(bootBytes(it)) }
                    files["$dir/super.img"] = ByteArray(IMAGE_BYTES * 2)
                    files["$dir/super.raw.img"] = ByteArray(IMAGE_BYTES * 2)
                }
            }
            "payload-dumper-go" -> DYNAMIC.forEach { files["${args[1]}/$it.img"] = ByteArray(IMAGE_BYTES) }.also {
                BOOT.forEach { files["${args[1]}/$it.img"] = ByteArray(bootBytes(it)) }
            }
            "lpmake" -> files[args.last()] = ByteArray(65536)
            "lpunpack" -> DYNAMIC.forEach { files["${args.last()}/${it}_a.img"] = ByteArray(IMAGE_BYTES) }
            "brotli" -> files[args[2].removePrefix("--output=")] = ByteArray(8)
            "zip" -> files["${cmd.workingDir}/rom.zip"] = ByteArray(ZIP_BYTES)
        }
        return ""
    }

    private fun copy(args: List<String>) {
        val sources = args.filter { !it.startsWith("-") }.dropLast(1)
        val dest = args.last()
        sources.forEach { src ->
            val bytes = files[src] ?: ByteArray(100)
            files[if (dest.endsWith("/")) dest + src.substringAfterLast('/') else dest] = bytes
        }
    }

    private fun avbtool(args: List<String>): String = when (args[0]) {
        "add_hashtree_footer" -> if ("--calc_max_image_size" in args) {
            (args[args.indexOf("--partition_size") + 1].toLong() - AVB_OVERHEAD).toString()
        } else {
            val image = args[args.indexOf("--image") + 1]
            files[image] = ByteArray(args[args.indexOf("--partition_size") + 1].toInt())
            ""
        }
        "info_image" -> {
            val image = args[args.indexOf("--image") + 1]
            val name = image.substringAfterLast('/').removeSuffix(".img")
            "Footer version: 1.0\nImage size: ${files[image]?.size ?: 0} bytes\n" +
                "Partition Name: $name\nHash Algorithm: sha256\nAlgorithm: SHA256_RSA4096\n"
        }
        else -> ""
    }

    private fun statDump(mnt: String): String =
        "0 0 755 $mnt\n0 0 644 $mnt/build.prop\n0 2000 755 $mnt/bin/run-as\n"

    private fun getfattrDump(mnt: String): String = listOf(mnt, "$mnt/build.prop", "$mnt/bin/run-as").joinToString("\n") {
        "# file: $it\nsecurity.selinux=\"u:object_r:system_file:s0\"\n"
    }

    private fun zipListing(): String = buildString {
        appendLine("Archive: rom.zip")
        appendLine("  Length      Date    Time    Name")
        val members = DYNAMIC.flatMap { listOf("$it.new.dat.br", "$it.transfer.list") } + BOOT.map { "$it.img" } + listOf(
            "META-INF/com/android/otacert", "META-INF/com/android/metadata",
            "META-INF/com/google/android/update-binary", "META-INF/com/google/android/updater-script",
            "dynamic_partitions_op_list", "unsparse_super_empty.img", "build_info.txt", "changelog.md",
        )
        members.forEach { appendLine("     1000  2026-09-11 12:00   $it") }
    }

    companion object {
        const val IMAGE_BYTES = 4096 * 300
        const val ZIP_BYTES = 8_000_000
        const val AVB_OVERHEAD = 4096L * 32
        val DYNAMIC = listOf("odm", "product", "system", "system_dlkm", "system_ext", "vendor", "vendor_dlkm")
        val BOOT = listOf("boot", "init_boot", "vendor_boot", "dtbo")
        const val STOCK_BUILD_PROP = "ro.build.type=user\nro.build.fingerprint=nubia/NX_PQ84P01:15/AQ3A.240812.002/20260311:user/release-keys\n" +
            "ro.build.version.incremental=20260311.222527\nro.build.version.sdk=35\nro.build.version.security_patch=2026-02-01\n"

        fun bootBytes(image: String): Int = when (image) {
            "boot", "vendor_boot" -> 100663296
            "init_boot" -> 8388608
            else -> 25165824
        }

        fun fakeDigest(seed: String, length: Int): String {
            val hex = "0123456789abcdef"
            var h = seed.hashCode().toLong() and 0xffffffffL
            return buildString {
                repeat(length) {
                    h = (h * 1103515245 + 12345) and 0x7fffffff
                    append(hex[(h shr 8).toInt() and 15])
                }
            }
        }
    }
}

class FakeVendoredFiles(private val present: Boolean = true) : VendoredFilePort {
    override fun load(resourceId: String): VendoredFile? =
        if (present) VendoredFile("ELF-updater".encodeToByteArray(), "pinned") else null
}
