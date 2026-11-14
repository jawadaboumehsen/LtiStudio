/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.workbench

import kotlinx.coroutines.delay
import org.ide.lti.core.domain.pipeline.toolkit.Refusal
import org.ide.lti.core.domain.pipeline.toolkit.RefusalException

/** How a phone is connected, as adb or fastboot report it. */
public enum class PhoneMode(public val words: String, public val fastboot: Boolean = false) {
    ANDROID("Android, over adb"),
    RECOVERY("recovery, over adb"),
    SIDELOAD("recovery sideload"),
    BOOTLOADER("bootloader (fastboot)", fastboot = true),
    FASTBOOTD("fastbootd (fastboot in Android's userspace)", fastboot = true),
    UNAUTHORIZED("adb, not authorized: accept the prompt on the phone"),
    NO_PERMISSION("seen, but this user may not open it (udev rules)"),
}

/** One partition as the phone reports it (`fastboot getvar all`). */
public data class PhonePartition(val name: String, val sizeBytes: Long?, val logical: Boolean)

/**
 * What a connected phone says about itself. [values] are its answers as read (`getvar all`, or `getprop` over adb),
 * kept whole; the rest is taken from them. A null fact was not answered: nothing here is assumed.
 */
public data class PhoneFacts(
    val serial: String,
    val mode: PhoneMode,
    val values: Map<String, String> = emptyMap(),
    /** Why its answers are missing or incomplete (the command failed or timed out); null when it answered. */
    val readError: String? = null,
) {
    private fun first(vararg keys: String) = keys.firstNotNullOfOrNull { values[it]?.takeIf(String::isNotBlank) }

    /** Fastboot's `product`, else Android's device name. */
    public val product: String? get() = first("product", "ro.product.device", "ro.product.vendor.device")
    public val model: String? get() = first("ro.product.model", "ro.product.vendor.model")

    /** The running slot as a suffix (`_a`), or null on a phone without slots. */
    public val currentSlot: String?
        get() = first("current-slot", "ro.boot.slot_suffix")?.let { if (it.startsWith("_")) it else "_$it" }

    public val slotCount: Int? get() = first("slot-count")?.toIntOrNull()

    /** Whether fastboot will write: `unlocked`, else Android's `ro.boot.flash.locked`. */
    public val unlocked: Boolean?
        get() = first("unlocked")?.let { it == "yes" } ?: first("ro.boot.flash.locked")?.let { it == "0" }

    /** Virtual A/B: `none`, `snapshotted` or `merging`. */
    public val snapshotStatus: String? get() = first("snapshot-update-status")
    public val maxDownloadBytes: Long? get() = first("max-download-size")?.let(::number)
    public val superName: String? get() = first("super-partition-name")

    /** Empty unless fastboot was asked: Android does not list its partitions to a user. */
    public val partitions: List<PhonePartition>
        get() = values.keys.filter { it.startsWith(SIZE) }.map { key ->
            val name = key.removePrefix(SIZE)
            PhonePartition(name, number(values.getValue(key)), values["$LOGICAL$name"] == "yes")
        }.sortedBy { it.name }

    public fun partition(name: String): PhonePartition? = partitions.firstOrNull { it.name == name }

    private companion object {
        const val SIZE = "partition-size:"
        const val LOGICAL = "is-logical:"
        const val HEX = 16

        fun number(text: String): Long? = text.trim().let {
            if (it.startsWith("0x", ignoreCase = true)) it.drop(2).toLongOrNull(HEX) else it.toLongOrNull()
        }
    }
}

/** Reads what adb and fastboot print. */
public object PhoneReadout {
    /** The phones `adb devices -l` and `fastboot devices` list, with the mode each is in. */
    public fun devices(adb: String, fastboot: String): List<Pair<String, PhoneMode>> {
        val overAdb = adb.lines().drop(1).mapNotNull { line ->
            val parts = line.trim().split(Regex("\\s+"))
            val serial = parts.firstOrNull()?.takeIf {
                it.isNotEmpty() && !it.startsWith(
                    "*",
                )
            } ?: return@mapNotNull null
            val mode = when {
                "no permissions" in line -> PhoneMode.NO_PERMISSION

                else -> when (parts.getOrNull(1)) {
                    "device" -> PhoneMode.ANDROID
                    "recovery" -> PhoneMode.RECOVERY
                    "sideload" -> PhoneMode.SIDELOAD
                    "unauthorized" -> PhoneMode.UNAUTHORIZED
                    else -> null
                }
            }
            mode?.let { serial to it }
        }
        val overFastboot = fastboot.lines().mapNotNull { line ->
            val parts = line.trim().split(Regex("\\s+"))
            when {
                parts.size < 2 -> null
                "no permissions" in line -> parts[0] to PhoneMode.NO_PERMISSION
                parts[1] == "fastboot" -> parts[0] to PhoneMode.BOOTLOADER
                else -> null
            }
        }
        return overAdb + overFastboot
    }

    /**
     * `fastboot getvar all` (`(bootloader) partition-size:boot_a:0x6000000`): each variable by its name, the
     * partition ones keeping theirs (`partition-size:boot_a`).
     */
    public fun fastbootVars(text: String): Map<String, String> = text.lines().mapNotNull { raw ->
        val line = raw.trim().removePrefix("(bootloader)").trim()
        val name = line.substringBefore(':', "")
        // A per-partition variable names its partition too; any other value may itself hold a colon.
        val keyEnd = if (name in PER_PARTITION) line.indexOf(':', name.length + 1) else name.length
        if (name.isEmpty() || keyEnd <= 0 || name == "all" || ' ' in name) return@mapNotNull null
        line.substring(0, keyEnd).trim() to line.substring(keyEnd + 1).trim()
    }.toMap()

    private val PER_PARTITION =
        setOf("partition-size", "partition-type", "is-logical", "has-slot", "slot-successful", "slot-unbootable")

    /** `adb shell getprop` (`[ro.product.device]: [PQ84P01]`). */
    public fun props(text: String): Map<String, String> = text.lines().mapNotNull { line ->
        PROP.find(line)?.destructured?.let { (key, value) -> key to value }
    }.toMap()

    private val PROP = Regex("""^\[([^\]]+)]: \[(.*)]$""")
}

/** An image of the project that could go to the phone: built by the last build, or as the firmware shipped it. */
public data class ProjectImage(
    /** Workspace-relative path. */
    val path: String,
    /** The partition it is for (the file name without `.img`). */
    val partition: String,
    val bytes: Long,
    /** What it holds written out: a sparse image's expanded size, else [bytes]. */
    val fullBytes: Long,
    val sparse: Boolean,
    val built: Boolean,
)

/**
 * Talks to a phone attached to WSL (usbipd) with the managed adb and fastboot. It only reads, and reboots on request:
 * it never writes a partition.
 */
public class PhoneProbe(private val runner: WorkbenchRunner) {
    /** Every connected phone and what it answers. */
    public suspend fun read(workspaceId: String): List<PhoneFacts> = runner.read(workspaceId) {
        val adb = toolPath("adb")
        val fastboot = toolPath("fastboot")
        val adbList = answer("adb devices", LIST_SCRIPT, adb)
        val fastbootList = answer("fastboot devices", LIST_SCRIPT, fastboot)
        listOfNotNull(adbList.second, fastbootList.second).firstOrNull()?.let { error ->
            throw RefusalException(Refusal(error, "the connected phone", "Check usbipd and the phone's cable."))
        }
        PhoneReadout.devices(adbList.first, fastbootList.first).map { (serial, mode) ->
            when (mode) {
                PhoneMode.BOOTLOADER -> {
                    val (out, error) = answer("fastboot getvar all", GETVAR_SCRIPT, fastboot, serial)
                    val vars = PhoneReadout.fastbootVars(out)
                    PhoneFacts(serial, if (vars["is-userspace"] == "yes") PhoneMode.FASTBOOTD else mode, vars, error)
                }

                PhoneMode.ANDROID, PhoneMode.RECOVERY -> {
                    val (out, error) = answer("adb shell getprop", GETPROP_SCRIPT, adb, serial)
                    PhoneFacts(serial, mode, PhoneReadout.props(out), error)
                }

                else -> PhoneFacts(serial, mode)
            }
        }
    }

    /**
     * Reboots [phone] into [to] and waits until it is really there (up to [WAIT_SECONDS]); returns it as read then,
     * or null when it did not come back in that mode.
     */
    public suspend fun reboot(workspaceId: String, phone: PhoneFacts, to: PhoneMode): PhoneFacts? {
        val target = when (to) {
            PhoneMode.BOOTLOADER -> "bootloader"
            PhoneMode.FASTBOOTD -> "fastboot"
            PhoneMode.RECOVERY -> "recovery"
            else -> ""
        }
        runner.read(workspaceId) {
            val tool = toolPath(if (phone.mode.fastboot) "fastboot" else "adb")
            sh(REBOOT_SCRIPT, tool, phone.serial, target)
        }
        repeat(WAIT_SECONDS / POLL_SECONDS) {
            delay(POLL_SECONDS * MILLIS)
            read(workspaceId).firstOrNull { it.serial == phone.serial && it.mode == to }?.let { return it }
        }
        return null
    }

    /** The images of the workspace that could be flashed: the build's, then the firmware's as shipped. */
    public suspend fun images(workspaceId: String): List<ProjectImage> = runner.read(workspaceId) {
        sh(IMAGES_SCRIPT, root).lines().filterNot { "super_empty" in it }.mapNotNull { line ->
            val f = line.split('\t').takeIf { it.size == IMAGE_FIELDS } ?: return@mapNotNull null
            ProjectImage(
                path = f[0],
                partition = f[0].substringAfterLast('/').removeSuffix(".img"),
                bytes = f[1].toLongOrNull() ?: return@mapNotNull null,
                fullBytes = f[2].toLongOrNull() ?: return@mapNotNull null,
                sparse = f[3] == "1",
                built = !f[0].startsWith("firmware/"),
            )
        }
    }

    public companion object {
        private const val LIST_SCRIPT = "timeout 15 \"\$1\" devices -l 2>&1"
        private const val GETVAR_SCRIPT = "timeout 30 \"\$1\" -s \"\$2\" getvar all 2>&1"
        private const val GETPROP_SCRIPT = "timeout 15 \"\$1\" -s \"\$2\" shell getprop 2>&1"
        private const val TIMED_OUT = 124
        private const val ERROR_SHOWN = 300

        /** A command's output, and why it failed (with the end of its output) or null. */
        private suspend fun WorkbenchRunner.Job.answer(
            what: String,
            script: String,
            vararg args: String,
        ): Pair<String, String?> {
            val result = tryRun("sh", "-c", script, "sh", *args)
            val error = when (result.exitCode) {
                0 -> null
                TIMED_OUT -> "$what did not answer within its time limit."
                else -> "$what failed (exit ${result.exitCode}): ${result.stdout.trim().takeLast(ERROR_SHOWN)}"
            }
            return result.stdout to error
        }
        private const val REBOOT_SCRIPT = "timeout 15 \"\$1\" -s \"\$2\" reboot \$3 2>&1"

        /**
         * Path, size, written-out size and 1 when sparse, per image. A sparse image's header holds the block size
         * (offset 12) and the block count (offset 16).
         */
        private const val IMAGES_SCRIPT = """cd "$1" || exit 1
for f in out/images/*.img out/package/.stage/*.img out/extra/super.img firmware/extracted/*.img; do
  [ -f "${'$'}f" ] || continue
  size=${'$'}(stat -c %s "${'$'}f"); full=${'$'}size; sparse=0
  if [ "${'$'}(od -An -tx4 -N4 "${'$'}f" | tr -d ' ')" = ed26ff3a ]; then
    blk=${'$'}(od -An -tu4 -j12 -N4 "${'$'}f" | tr -d ' '); n=${'$'}(od -An -tu4 -j16 -N4 "${'$'}f" | tr -d ' ')
    full=${'$'}((blk * n)); sparse=1
  fi
  printf '%s\t%s\t%s\t%s\n' "${'$'}f" "${'$'}size" "${'$'}full" "${'$'}sparse"
done"""
        private const val IMAGE_FIELDS = 4
        private const val WAIT_SECONDS = 90
        private const val POLL_SECONDS = 3
        private const val MILLIS = 1000L
    }
}
