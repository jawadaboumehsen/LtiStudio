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

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.ide.lti.core.domain.pipeline.digestToken
import org.ide.lti.core.domain.pipeline.text.FstabEditor
import org.ide.lti.core.domain.pipeline.toolkit.AvbFooterInfo
import org.ide.lti.core.domain.pipeline.toolkit.BootFlagDiff
import org.ide.lti.core.domain.pipeline.toolkit.BootHeaderFacts
import org.ide.lti.core.domain.pipeline.toolkit.BootParts
import org.ide.lti.core.domain.pipeline.toolkit.BuildFilesystems
import org.ide.lti.core.domain.pipeline.toolkit.DeviceTreeDiff
import org.ide.lti.core.domain.pipeline.toolkit.FormatDetector
import org.ide.lti.core.domain.pipeline.toolkit.KernelFacts
import org.ide.lti.core.domain.pipeline.toolkit.KernelOverrides
import org.ide.lti.core.domain.pipeline.toolkit.MountTable
import org.ide.lti.core.domain.pipeline.toolkit.RamdiskCheck
import org.ide.lti.core.domain.pipeline.toolkit.RamdiskCheckResult
import org.ide.lti.core.domain.pipeline.toolkit.RamdiskEntry
import org.ide.lti.core.domain.pipeline.toolkit.Refusal
import org.ide.lti.core.domain.pipeline.toolkit.RefusalException
import org.ide.lti.core.domain.pipeline.toolkit.RootPatcherType
import org.ide.lti.core.domain.pipeline.toolkit.RootStatus
import org.ide.lti.core.domain.pipeline.toolkit.SigningPlan
import org.ide.lti.core.domain.pipeline.toolkit.VerityRemovalResult

/** A repacked image saved as the partition's override, with the SHA-256 the Assemble settings record. */
public data class SavedOverride(val partition: String, val sha256: String, val check: RamdiskCheckResult)

/** What [BootWorkbench.editFstabs] does to the first-stage mount table. */
public enum class FstabEdit(public val title: String) {
    /**
     * Lets every dynamic partition mount as each buildable filesystem: a line is added for whichever is missing,
     * after the existing ones, so the stock one is still tried first.
     */
    MOUNT_LINES("Add mount lines") {
        override fun apply(fstab: String): String {
            val points = FstabEditor.logicalMountPoints(fstab)
            return BuildFilesystems.SUPPORTED.fold(fstab) { t, fs ->
                FstabEditor.withFilesystems(t, points.associateWith { fs })
            }
        }
    },

    /** Removes the AVB and verity flags: no partition is mounted with dm-verity. */
    NO_INTEGRITY("Remove integrity flags") {
        override fun apply(fstab: String): String = FstabEditor.withoutIntegrity(fstab)
    },
    ;

    public abstract fun apply(fstab: String): String
}

/**
 * Boot, vendor_boot and init_boot work in WSL with the AOSP tools: `unpack_bootimg` / `mkbootimg` (the
 * `--format mkbootimg` arguments make the round trip exact), `cpio` / `mkbootfs` for the ramdisk folder,
 * `dtc`, `avbtool`; APatch with KernelPatch's `kptools`.
 */
public class BootWorkbench(
    private val runner: WorkbenchRunner,
    /** A Windows path as WSL sees it (`C:\boot.img` -> `/mnt/c/boot.img`). */
    private val toLinuxPath: (String) -> String,
) {

    /** Unpacks [partition]'s current image (override, assembled, or extracted) into `workbench/boot/<p>/`. */
    public suspend fun unpack(workspaceId: String, partition: String): BootParts =
        runner.job(workspaceId, "Unpack $partition") {
            val source = sourceOf(this, partition)
            val dir = dir(partition)
            sh("rm -rf \"$1\" && mkdir -p \"$1/unpacked\"", abs(dir))
            val info = run(
                "unpack_bootimg",
                "--boot_img",
                abs(source),
                "--out",
                abs("$dir/unpacked"),
                "--format",
                "info",
            )
            val args = run(
                "unpack_bootimg",
                "--boot_img",
                abs(source),
                "--out",
                abs("$dir/unpacked"),
                "--format",
                "mkbootimg",
                "-0",
            )
            writeText("$dir/$ARGS", args)

            val ramdisk = RAMDISK_NAMES.firstOrNull { tryRun("test", "-s", abs("$dir/unpacked/$it")).exitCode == 0 }
            val compression = ramdisk?.let { name ->
                val magic = run("xxd", "-p", "-l", "6", abs("$dir/unpacked/$name")).trim()
                RamdiskCompression.of(FormatDetector.fromHex(magic)).also { c ->
                    sh(c.decompress, abs("$dir/unpacked/$name"), abs("$dir/ramdisk.cpio"))
                    // Inside fakeroot, whose state keeps what a user cannot create (the /dev nodes of an init_boot
                    // ramdisk, root ownership) for repack to put back.
                    sh(
                        "mkdir -p \"$1\" && fakeroot -s \"$3\" -- sh -c " +
                            "'cd \"$1\" && cpio -idmu --no-absolute-filenames --quiet < \"$2\"' sh \"$1\" \"$2\"",
                        abs("$dir/$RAMDISK_DIR"),
                        abs("$dir/ramdisk.cpio"),
                        abs("$dir/$FAKEROOT_STATE"),
                    )
                }
            }
            val hasDtb = tryRun("test", "-s", abs("$dir/unpacked/dtb")).exitCode == 0
            if (hasDtb) sh(DTB_UNPACK_SCRIPT, abs(dir), "dtc")
            val footer = footerOf(this, source)
            // The size a new footer is made for is the stock partition's: the assembled copy in work/kernel has no
            // footer under the "erase" policy, and its file size is the bare image, not the partition.
            val partitionSize = (footerOf(this, "firmware/extracted/$partition.img") ?: footer)?.imageSize
            val state = BootState(source, ramdisk, compression, partitionSize, fingerprintOf(this, dir))
            writeText("$dir/$STATE", json.encodeToString(BootState.serializer(), state))
            BootParts(
                imageName = partition,
                kernelPath = "$dir/unpacked/kernel".takeIf { exists(it) },
                ramdiskFolderPath = ramdisk?.let { "$dir/$RAMDISK_DIR" },
                dtbPath = "$dir/$DTS".takeIf { hasDtb },
                header = parseHeader(info),
                footer = footer,
            )
        }

    /**
     * The kernel of the boot image the next build uses: its version and what it can read (EROFS compression,
     * ramdisk formats), from its embedded config. Null when the image carries no kernel (init_boot, vendor_boot).
     */
    public suspend fun kernelFacts(workspaceId: String): KernelFacts? = runner.read(workspaceId) {
        KernelFacts.parse(sh(KernelFacts.SCRIPT, abs(sourceOf(this, KERNEL_IMAGE)), toolPath("unpack_bootimg")))
    }

    /** The ramdisk folder as Windows sees it (to open in Explorer); refuses when the image has none (FR-021). */
    public suspend fun ramdiskFolder(workspaceId: String, partition: String): String = runner.read(workspaceId) {
        val folder = "${dir(partition)}/$RAMDISK_DIR"
        if (!exists(folder)) {
            throw RefusalException(
                Refusal("This image has no ramdisk.", "$partition.img", "It may live in vendor_boot or init_boot."),
            )
        }
        hostPath(folder) ?: abs(folder)
    }

    /** Removes the dm-verity keys from the unpacked ramdisk, nothing else (FR-025). */
    public suspend fun removeVerityKeys(workspaceId: String, partition: String): VerityRemovalResult =
        runner.job(workspaceId, "Remove verity keys") {
            val folder = abs("${dir(partition)}/$RAMDISK_DIR")
            val found = run("find", folder, "-type", "f", "(", "-name", "verity_key", "-o", "-name", "*.avbpubkey", ")")
                .lines().filter { it.isNotBlank() }
            found.forEach { run("rm", "-f", it) }
            VerityRemovalResult(removedKeys = found.map { it.removePrefix("$folder/") }, restorePointId = null)
        }

    /**
     * Applies [edit] to every fstab of [partition]'s unpacked ramdisk (the first-stage mount table in vendor_boot).
     * Returns the fstab files changed; repack to keep the change.
     */
    public suspend fun editFstabs(workspaceId: String, partition: String, edit: FstabEdit): List<String> =
        runner.job(workspaceId, edit.title) {
            val folder = "${dir(partition)}/$RAMDISK_DIR"
            if (!exists(folder)) {
                throw RefusalException(Refusal("$partition.img is not unpacked.", "$partition.img", "Unpack it first."))
            }
            run(
                "find",
                abs(folder),
                "-type",
                "f",
                "-name",
                "fstab.*",
            ).lines().filter { it.isNotBlank() }.mapNotNull { f ->
                val rel = "$folder/${f.removePrefix(abs(folder) + "/")}"
                val text = readText(rel) ?: return@mapNotNull null
                val edited = edit.apply(text)
                rel.takeIf { edited != text }?.also { writeText(it, edited) }
            }
        }

    /** The device tree source of the unpacked image, for editing (FR-022). */
    public suspend fun deviceTree(workspaceId: String, partition: String): String? =
        runner.read(workspaceId) { readText("${dir(partition)}/$DTS") }

    /** Shows what saving [newDts] would change, without saving. */
    public suspend fun previewDeviceTree(workspaceId: String, partition: String, newDts: String): DeviceTreeDiff {
        val old = deviceTree(workspaceId, partition).orEmpty()
        return DeviceTreeDiff(old, newDts, lineChanges(old, newDts))
    }

    public suspend fun saveDeviceTree(workspaceId: String, partition: String, newDts: String) {
        runner.job(workspaceId, "Edit device tree") {
            val candidate = "${dir(partition)}/$DTS.new"
            writeText(candidate, newDts)
            val checked = tryRun("dtc", "-q", "-I", "dts", "-O", "dts", "-o", "/dev/null", abs(candidate))
            if (checked.exitCode != 0) {
                tryRun("rm", "-f", abs(candidate))
                throw RefusalException(
                    Refusal(
                        "The device tree does not compile; the saved one is unchanged.",
                        "$partition: $DTS",
                        "Fix the error and save again.",
                        details = checked.stderr.trim(),
                    ),
                )
            }
            run("mv", "-f", abs(candidate), abs("${dir(partition)}/$DTS"))
        }
    }

    /**
     * Rebuilds the image from the unpacked parts and saves it as the partition's override. The new ramdisk is
     * read back and compared with the folder first; a mismatch blocks the result (FR-021). When [plan] is given
     * the AVB footer is re-signed with it; an image that had a footer gets an unsigned one otherwise (FR-024).
     */
    public suspend fun repack(workspaceId: String, partition: String, plan: SigningPlan? = null): SavedOverride =
        runner.job(workspaceId, "Repack $partition") {
            val dir = dir(partition)
            val state = readText("$dir/$STATE")?.let { json.decodeFromString(BootState.serializer(), it) }
                ?: throw RefusalException(Refusal("The image is not unpacked.", "$partition.img", "Unpack it first."))
            var check = RamdiskCheckResult(ok = true)
            if (state.ramdisk != null && state.compression != null) {
                val folder = abs("$dir/$RAMDISK_DIR")
                // The unpack's fakeroot state brings back the device nodes; without it they would be empty files.
                sh(
                    "if [ -f \"$4\" ]; then fakeroot -i \"$4\" -s \"$4\" -- \"$3\" \"$1\" > \"$2\"; " +
                        "else \"$3\" \"$1\" > \"$2\"; fi",
                    folder,
                    abs("$dir/ramdisk.new.cpio"),
                    toolPath("mkbootfs"),
                    abs("$dir/$FAKEROOT_STATE"),
                )
                val archived = sh("cpio -itv --quiet --numeric-uid-gid < \"$1\"", abs("$dir/ramdisk.new.cpio"))
                val listed = run("find", folder, "-mindepth", "1", "-printf", "%y %s %P\\n")
                RamdiskCheck.missingInit(
                    partition,
                    folderEntries(listed).map { it.path },
                )?.let { throw RefusalException(it) }
                val initHead = sh(
                    "if [ -L \"$1/init\" ]; then echo link; elif [ -f \"$1/init\" ]; then " +
                        "h=$(xxd -p -l 4 \"$1/init\"); echo \"${'$'}{h:-empty}\"; " +
                        "elif [ -e \"$1/init\" ]; then echo notfile; fi",
                    folder,
                )
                RamdiskCheck.badInit(partition, initHead.trim())?.let { throw RefusalException(it) }
                check = RamdiskCheck.compare(cpioEntries(archived), folderEntries(listed))
                if (!check.ok) return@job SavedOverride(partition, "", check)
                sh(state.compression.compress, abs("$dir/ramdisk.new.cpio"), abs("$dir/unpacked/${state.ramdisk}"))
            }
            if (exists("$dir/$DTS")) sh(DTB_REPACK_SCRIPT, abs(dir), "dtc")
            // Empty values stay (`--ramdisk_name ''` of a v4 vendor ramdisk); only the trailing newline goes.
            val args = mkbootimgArgs(readText("$dir/$ARGS").orEmpty())
            // A vendor_boot is written with --vendor_boot: with --output, mkbootimg refuses its ramdisk fragments.
            val vendor = run("xxd", "-p", "-l", "8", abs(state.source)).trim() == VENDOR_BOOT_MAGIC_HEX
            run("mkbootimg", *args.toTypedArray(), if (vendor) "--vendor_boot" else "--output", abs("$dir/new.img"))
            val partitionSize = plan?.partitionSize ?: state.footerImageSize
            if (partitionSize != null) {
                val sign = plan?.let { listOf("--key", abs(it.keyPath), "--algorithm", it.algorithmName) }
                    ?: listOf("--algorithm", "NONE")
                run(
                    "avbtool", "add_hash_footer", "--image", abs("$dir/new.img"),
                    "--partition_size", partitionSize.toString(), "--partition_name", partition, *sign.toTypedArray(),
                )
            }
            run("unpack_bootimg", "--boot_img", abs("$dir/new.img"), "--out", abs("$dir/verify"), "--format", "info")
            val saved = saveOverride(this, partition, "$dir/new.img").copy(check = check)
            // What is in the folder now is built: only later edits count as not built.
            writeText(
                "$dir/$STATE",
                json.encodeToString(BootState.serializer(), state.copy(fingerprint = fingerprintOf(this, dir))),
            )
            saved
        }

    /**
     * Copies a boot image patched elsewhere (the Magisk app) from [windowsPath] into the partition's override,
     * refusing one the device would not boot: an AVB footer avbtool cannot read (the bootloader reads it first), or
     * a ramdisk compressed in a format the boot kernel does not unpack.
     */
    public suspend fun importPatched(workspaceId: String, partition: String, windowsPath: String): SavedOverride =
        runner.job(workspaceId, "Import $partition") {
            val linuxSource = toLinuxPath(windowsPath)
            val head = run("xxd", "-p", "-l", "8", linuxSource).trim()
            val wanted = if (partition.startsWith("vendor_boot")) VENDOR_BOOT_MAGIC_HEX else BOOT_MAGIC_HEX
            if (head != wanted) {
                throw RefusalException(
                    Refusal(
                        "The file is not a $partition image.",
                        linuxSource.substringAfterLast('/'),
                        "Pick the patched .img file.",
                    ),
                )
            }
            checkImported(this, partition, linuxSource)
            saveOverride(this, partition, linuxSource)
        }

    // A lambda, not a function: the class is at detekt's function limit.
    private val checkImported: suspend (WorkbenchRunner.Job, String, String) -> Unit = { job, partition, image ->
        with(job) {
            if (footerOf(this, image) == null &&
                sh("tail -c 64 \"$1\" | head -c 4", image).trim() == AVB_FOOTER_MAGIC
            ) {
                throw RefusalException(
                    Refusal(
                        "Its AVB footer cannot be read (it points outside the image or at no vbmeta block).",
                        "$partition.img",
                        "Patch the stock image again and import the new file.",
                    ),
                )
            }
            val t = "${dir(partition)}/import-check"
            sh("rm -rf \"$1\" && mkdir -p \"$1\"", abs(t))
            // The magic bytes alone do not make a boot image: one unpack_bootimg cannot read is never saved.
            val unpacked = tryRun("unpack_bootimg", "--boot_img", image, "--out", abs(t))
            if (unpacked.exitCode != 0) {
                tryRun("rm", "-rf", abs(t))
                throw RefusalException(
                    Refusal(
                        "unpack_bootimg cannot read it: ${unpacked.stderr.trim().lines().lastOrNull().orEmpty()}",
                        "$partition.img",
                        "Patch the stock image again and import the new file.",
                    ),
                )
            }
            val ramdisk = RAMDISK_NAMES.firstOrNull { tryRun("test", "-s", abs("$t/$it")).exitCode == 0 }
            val format = ramdisk?.let {
                val magic = run("xxd", "-p", "-l", "6", abs("$t/$it")).trim()
                RamdiskCompression.of(FormatDetector.fromHex(magic)).kernelName
            }
            tryRun("rm", "-rf", abs(t))
            // The kernel that unpacks it: the imported boot's own, else the workspace's boot image.
            val kernelImage = if (partition == KERNEL_IMAGE) image else abs(sourceOf(this, KERNEL_IMAGE))
            val kernel = KernelFacts.parse(sh(KernelFacts.SCRIPT, kernelImage, toolPath("unpack_bootimg")))
            RamdiskCheck.unreadableRamdisk(partition, format, kernel?.ramdiskFormats)
                ?.let { throw RefusalException(it) }
        }
    }

    /** Whether the unpacked image already carries Magisk (ramdisk) or APatch (kernel) (FR-026). */
    public suspend fun rootStatus(workspaceId: String, partition: String): RootStatus = runner.read(workspaceId) {
        val dir = dir(partition)
        when {
            exists("$dir/$RAMDISK_DIR/.backup/.magisk") || exists("$dir/$RAMDISK_DIR/overlay.d/sbin") ->
                RootStatus.AlreadyPatched(RootPatcherType.MAGISK, "Magisk files in the ramdisk")

            exists("$dir/unpacked/kernel") && kpatchInfo(this, "$dir/unpacked/kernel")?.contains("[kpimg]") == true ->
                RootStatus.AlreadyPatched(RootPatcherType.APATCH, "KernelPatch in the kernel")

            else -> RootStatus.NotPatched
        }
    }

    /**
     * Patches the unpacked kernel with KernelPatch (APatch) and [superKey]. The key reaches `kptools` through
     * a file descriptor, never the command line, and the file is removed afterwards. Repack to save.
     */
    public suspend fun patchApatch(workspaceId: String, partition: String, superKey: String) {
        require(superKey.length >= MIN_SUPERKEY) { "The superkey needs at least $MIN_SUPERKEY characters." }
        runner.job(workspaceId, "APatch $partition") {
            val kernel = "${dir(partition)}/unpacked/kernel"
            if (!exists(kernel)) {
                throw RefusalException(
                    Refusal("This image has no kernel.", "$partition.img", "Patch boot.img instead."),
                )
            }
            if (kpatchInfo(this, kernel)?.contains("[kpimg]") == true) {
                throw RefusalException(
                    Refusal("The kernel is already patched with APatch.", "$partition.img", "Nothing to do."),
                )
            }
            val kptools = toolPath("kptools")
            // kpimg is the kernel-side half of the same KernelPatch release; fetched once, checked by SHA-256.
            sh(
                "[ -f \"$1\" ] && echo \"$2  $1\" | sha256sum -c --status && exit 0; " +
                    "mkdir -p \"$(dirname \"$1\")\" && curl -fsSL --retry 3 \"$3\" -o \"$1.partial\" && " +
                    "echo \"$2  $1.partial\" | sha256sum -c --status && mv -f \"$1.partial\" \"$1\"",
                abs(KPIMG),
                KPIMG_SHA256,
                KPIMG_URL,
            )
            val keyFile = "${dir(partition)}/.skey"
            sh("umask 077 && : > \"$1\"", abs(keyFile))
            writeText(keyFile, superKey)
            try {
                sh(
                    "\"$3\" -p -i \"$1\" -k \"$4\" --skey-fd 3 -o \"$1.new\" 3< \"$2\" && mv -f \"$1.new\" \"$1\"",
                    abs(kernel),
                    abs(keyFile),
                    kptools,
                    abs(KPIMG),
                )
            } finally {
                withContext(NonCancellable) { tryRun("rm", "-f", abs(keyFile)) }
            }
        }
    }

    /** The AVB flags of the workspace's `vbmeta` image (FR-023). */
    public suspend fun vbmetaFlags(workspaceId: String, image: String = VBMETA): Int = runner.read(workspaceId) {
        readVbmetaFlags(this, image)
    }

    /**
     * Writes a copy of [image] with [newFlags] to `out/toolkit/` (vbmeta is flashed by hand, never packaged) and
     * returns the change and the copy's path.
     */
    public suspend fun writeVbmetaFlags(
        workspaceId: String,
        newFlags: Int,
        image: String = VBMETA,
    ): Pair<BootFlagDiff, String> = runner.job(workspaceId, "Edit vbmeta flags") {
        val old = readVbmetaFlags(this, image)
        val out = "out/toolkit/${image.substringAfterLast('/')}"
        sh("mkdir -p \"$(dirname \"$2\")\" && cp -f \"$1\" \"$2\"", abs(image), abs(out))
        val bytes = (0 until INT_BYTES).joinToString("") { i ->
            "\\" + ((newFlags ushr (BITS_PER_BYTE * (INT_BYTES - 1 - i))) and BYTE_MASK).toString(
                OCTAL,
            ).padStart(3, '0')
        }
        sh("printf '$bytes' | dd of=\"$1\" bs=1 seek=$VBMETA_FLAGS_OFFSET conv=notrunc status=none", abs(out))
        BootFlagDiff.compute(old, newFlags) to out
    }

    /** The signing plan for [partition]: the workspace AVB key and the image's partition size (FR-024). */
    public suspend fun signingPlan(
        workspaceId: String,
        partition: String,
        keyRef: String,
        algorithm: String = DEFAULT_ALGORITHM,
    ): SigningPlan = runner.read(workspaceId) {
        if (!exists(keyRef)) {
            throw RefusalException(Refusal("The AVB key is missing.", keyRef, "Set the AVB key in Build & Sign."))
        }
        val source = sourceOf(this, partition)
        // The stock partition's size, as in unpack: work/kernel's copy may have had its footer erased.
        // Only a footer says the partition's size; a bare image is usually smaller than its partition.
        val size = (footerOf(this, "firmware/extracted/$partition.img") ?: footerOf(this, source))?.imageSize
            ?: throw RefusalException(
                Refusal(
                    "The partition size is unknown: neither the stock nor this $partition.img has an AVB footer.",
                    "$partition.img",
                    "Read it on the phone (fastboot getvar partition-size:$partition) and sign it by hand.",
                ),
            )
        SigningPlan(partitionName = partition, algorithmName = algorithm, keyPath = keyRef, partitionSize = size)
    }

    private suspend fun saveOverride(job: WorkbenchRunner.Job, partition: String, image: String): SavedOverride = with(
        job,
    ) {
        val target = KernelOverrides.path(partition)
        if (exists(target)) backup(TOOL, target)
        sh("mkdir -p \"$(dirname \"$2\")\" && cp -f \"$1\" \"$2\"", abs(image), abs(target))
        SavedOverride(partition, run("sha256sum", abs(target)).digestToken(), RamdiskCheckResult(ok = true))
    }

    private suspend fun sourceOf(job: WorkbenchRunner.Job, partition: String): String =
        listOf(KernelOverrides.path(partition), "work/kernel/$partition.img", "firmware/extracted/$partition.img")
            .firstOrNull { job.exists(it) }
            ?: throw RefusalException(
                Refusal(
                    "No $partition.img in the workspace.",
                    "$partition.img",
                    "Run the build through Extract first.",
                ),
            )

    private suspend fun footerOf(job: WorkbenchRunner.Job, image: String): AvbFooterInfo? {
        val info = job.tryRun("avbtool", "info_image", "--image", job.abs(image))
        if (info.exitCode != 0 || "Footer version" !in info.stdout) return null
        fun field(name: String) = Regex(
            """^\s*$name:\s*(.+)$""",
            RegexOption.MULTILINE,
        ).find(info.stdout)?.groupValues?.get(1)?.trim()
        return AvbFooterInfo(
            algorithmName = field("Algorithm").orEmpty(),
            keyId = field("Public key \\(sha1\\)").orEmpty(),
            partitionName = field("Partition Name").orEmpty(),
            imageSize = field("Image size")?.substringBefore(' ')?.toLongOrNull() ?: 0L,
            originalFooterSize = field("Original image size")?.substringBefore(' ')?.toLongOrNull() ?: 0L,
        )
    }

    private suspend fun readVbmetaFlags(job: WorkbenchRunner.Job, image: String): Int {
        if (!job.exists(image)) {
            throw RefusalException(
                Refusal("No vbmeta image in the workspace.", image, "Run the build through Extract first."),
            )
        }
        val magic = job.run("xxd", "-p", "-l", "4", job.abs(image)).trim()
        if (magic != AVB_MAGIC_HEX) {
            throw RefusalException(Refusal("The file is not a vbmeta image.", image, "Pick the stock vbmeta.img."))
        }
        return job.run(
            "xxd",
            "-p",
            "-s",
            "$VBMETA_FLAGS_OFFSET",
            "-l",
            "$INT_BYTES",
            job.abs(image),
        ).trim().toLong(HEX).toInt()
    }

    private fun dir(partition: String): String = "workbench/boot/$partition"

    public companion object {
        public const val TOOL: String = "boot"
        private const val KERNEL_IMAGE = "boot"

        public const val VBMETA: String = "firmware/extracted/vbmeta.img"
        public const val DEFAULT_ALGORITHM: String = "SHA256_RSA4096"
        private const val ARGS = "mkbootimg.args"
        private const val STATE = "state.json"
        private const val FAKEROOT_STATE = "fakeroot.state"

        /**
         * `sh -c FINGERPRINT_SCRIPT sh <unpacked dir>`: one hash over the ramdisk folder (paths, modes, link targets,
         * contents) and the device tree source, the parts a user edits.
         */
        internal const val FINGERPRINT_SCRIPT: String = """
            cd "$1" && {
              if [ -d ramdisk ]; then
                (cd ramdisk && find . -printf '%p %m %l\n' | LC_ALL=C sort && find . -type f -print0 | LC_ALL=C sort -z | xargs -0 -r sha256sum)
              fi
              if [ -f dtb.dts ]; then sha256sum dtb.dts; fi
              if [ -f unpacked/kernel ]; then sha256sum unpacked/kernel; fi
            } | sha256sum | cut -c1-64
        """

        private suspend fun fingerprintOf(job: WorkbenchRunner.Job, dir: String): String =
            job.sh(FINGERPRINT_SCRIPT, job.abs(dir)).trim()

        /**
         * `unpack_bootimg --format mkbootimg -0` output as arguments: NUL-separated, then one newline. Only that last
         * piece goes; an empty value before it (`--cmdline ''` of an init_boot) is an argument and stays.
         */
        public fun mkbootimgArgs(text: String): List<String> =
            text.split('\u0000').let { if (it.lastOrNull()?.isBlank() == true) it.dropLast(1) else it }

        /**
         * The device tree's fstab of the images in `work/kernel/` (boot and vendor_boot dtb, dtbo overlays)
         * and whether the phone uses it. Without mkdtboimg (optional in its tool group)
         * the dtbo overlays are not read.
         */
        public suspend fun deviceTreeMounts(runner: WorkbenchRunner, workspaceId: String): MountTable.DeviceTree =
            runner.read(workspaceId) {
                val dtbo = try {
                    toolPath("mkdtboimg")
                } catch (@Suppress("SwallowedException") e: RefusalException) {
                    ""
                }
                MountTable.deviceTree(sh(MountTable.SCRIPT, root, toolPath("unpack_bootimg"), dtbo))
            }

        /**
         * The unpacked boot images whose ramdisk folder or device tree changed since they were unpacked or last
         * repacked: those edits are not in the next build until repacked.
         */
        public suspend fun unbuiltEdits(runner: WorkbenchRunner, workspaceId: String): List<String> =
            runner.read(workspaceId) {
                // Nothing unpacked yet: no folder, so find fails and finds nothing.
                tryRun("find", abs("workbench/boot"), "-mindepth", "2", "-maxdepth", "2", "-name", STATE).stdout
                    .lines().filter(String::isNotBlank).mapNotNull { path ->
                        val dir = path.removePrefix("$root/").substringBeforeLast('/')
                        val state = readText("$dir/$STATE")?.let { json.decodeFromString(BootState.serializer(), it) }
                        dir.substringAfterLast('/').takeIf {
                            state?.fingerprint != null && state.fingerprint != fingerprintOf(this, dir)
                        }
                    }.sorted()
            }
        private const val DTS = "dtb.dts"

        /**
         * `sh -c DTB_UNPACK_SCRIPT sh <unpacked dir> <dtc>`: keeps the dtb as found (`dtb.orig`; a vendor_boot dtb is
         * often several device trees back to back), writes the first one as `dtb.dts` for editing, headed by how many
         * there are, and records its hash so repack can tell whether it was edited.
         */
        public const val DTB_UNPACK_SCRIPT: String = """
            set -e
            cd "$1"
            cp -f unpacked/dtb dtb.orig
            size=${'$'}(stat -c %s dtb.orig); off=0; n=0
            while [ ${'$'}off -lt ${'$'}size ] && [ "${'$'}(xxd -p -s ${'$'}off -l 4 dtb.orig)" = d00dfeed ]; do
              sz=${'$'}((0x${'$'}(xxd -p -s ${'$'}((off + 4)) -l 4 dtb.orig)))
              [ ${'$'}sz -gt 0 ] && [ ${'$'}((off + sz)) -le ${'$'}size ] || break
              off=${'$'}((off + sz)); n=${'$'}((n + 1))
            done
            "$2" -q -I dtb -O dts -o dtb.first.dts dtb.orig
            {
              if [ ${'$'}n -gt 1 ]; then
                echo "// This dtb holds ${'$'}n device trees; this is the first. Repack keeps the others as they are."
              fi
              cat dtb.first.dts
            } > dtb.dts
            rm -f dtb.first.dts
            sha256sum dtb.dts > dtb.dts.sha
        """

        /**
         * `sh -c DTB_REPACK_SCRIPT sh <unpacked dir> <dtc>`: the dtb as found when `dtb.dts` was not edited; otherwise
         * the edited first device tree followed by the others byte for byte (dtc reads only the first).
         */
        public const val DTB_REPACK_SCRIPT: String = """
            set -e
            cd "$1"
            if [ ! -f dtb.orig ] || { [ -f dtb.dts.sha ] && sha256sum -c --status dtb.dts.sha; }; then
              [ -f dtb.orig ] && cp -f dtb.orig unpacked/dtb
              exit 0
            fi
            "$2" -q -I dts -O dtb -o dtb.first dtb.dts
            first=${'$'}((0x${'$'}(xxd -p -s 4 -l 4 dtb.orig)))
            { cat dtb.first; tail -c +${'$'}((first + 1)) dtb.orig; } > unpacked/dtb
            rm -f dtb.first
        """
        private const val RAMDISK_DIR = "ramdisk"
        private val RAMDISK_NAMES = listOf("ramdisk", "vendor_ramdisk00", "vendor_ramdisk")
        private const val BOOT_MAGIC_HEX = "414e44524f494421" // ANDROID!
        private const val VENDOR_BOOT_MAGIC_HEX = "564e4452424f4f54" // VNDRBOOT
        private const val AVB_MAGIC_HEX = "41564230" // AVB0
        private const val AVB_FOOTER_MAGIC = "AVBf" // the last 64 bytes of an image with an AVB footer
        private const val VBMETA_FLAGS_OFFSET = 120 // AvbVBMetaImageHeader.flags, big-endian uint32
        private const val INT_BYTES = 4
        private const val BITS_PER_BYTE = 8
        private const val BYTE_MASK = 0xFF
        private const val OCTAL = 8
        private const val HEX = 16
        private const val MIN_SUPERKEY = 8

        /** KernelPatch 0.13.9's kpimg, matching the pinned kptools (`ToolGroupCatalog.KPTOOLS`). */
        private const val KPIMG = "workbench/kernelpatch/0.13.9/kpimg-android"
        private const val KPIMG_URL = "https://github.com/bmax121/KernelPatch/releases/download/0.13.9/kpimg-android"
        private const val KPIMG_SHA256 = "eb2e3d16fe69410150efcf6f9425baf8cad9ac0c39f051fc8b40ce57c7143adb"
        private val json = Json { ignoreUnknownKeys = true }

        /** `unpack_bootimg --format info` lines (`key: value`) as header facts. */
        public fun parseHeader(info: String): BootHeaderFacts {
            val fields = info.lines().mapNotNull { line ->
                line.split(':', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim().lowercase() to it[1].trim() }
            }.toMap()
            fun long(vararg keys: String) = keys.firstNotNullOfOrNull {
                fields[it]?.substringBefore(
                    ' ',
                )?.toLongOrNull()
            } ?: 0L
            return BootHeaderFacts(
                headerVersion = long(
                    "boot image header version",
                    "header version",
                    "vendor boot image header version",
                ).toInt(),
                osVersion = fields["os version"].orEmpty(),
                osPatchLevel = fields["os patch level"].orEmpty(),
                pageSize = long("page size").toInt().takeIf { it > 0 } ?: BootHeaderFacts().pageSize,
                cmdline = fields["command line args"] ?: fields["vendor command line args"].orEmpty(),
                kernelSize = long("kernel_size", "kernel size"),
                ramdiskSize = long("ramdisk size", "vendor ramdisk total size"),
                dtbSize = long("dtb size"),
                recoveryDtboSize = long("recovery dtbo size"),
            )
        }

        /** kptools' listing of [kernel]'s KernelPatch parts, or null when it has none. */
        private suspend fun kpatchInfo(job: WorkbenchRunner.Job, kernel: String): String? =
            job.tryRun("kptools", "-l", "-i", job.abs(kernel)).takeIf { it.exitCode == 0 }?.stdout

        /**
         * `cpio -itv --numeric-uid-gid` lines: mode, links, uid, gid, size, three date fields, name (`a -> b` for a
         * link). A directory's size is not compared (it differs between cpio and a filesystem).
         */
        internal fun cpioEntries(listing: String): List<RamdiskEntry> = listing.lines().mapNotNull { line ->
            val t = line.trim().split(Regex("""\s+"""))
            // A device node lists "major, minor" where the size is: one field more, and no size.
            val device = t.getOrNull(CPIO_SIZE_FIELD)?.endsWith(",") == true
            val nameField = CPIO_NAME_FIELD + if (device) 1 else 0
            if (t.size < nameField + 1) return@mapNotNull null
            val name = t.drop(nameField).joinToString(" ").substringBefore(" -> ").removePrefix("./")
            val dir = t[0].startsWith("d")
            val size = if (dir || device) 0L else t[CPIO_SIZE_FIELD].toLongOrNull() ?: 0L
            RamdiskEntry(name, size, dir)
                .takeIf { name.isNotEmpty() && name != "." }
        }

        /** `find -printf '%y %s %P'` lines of the ramdisk folder, as [cpioEntries] reads the archive. */
        internal fun folderEntries(listing: String): List<RamdiskEntry> = listing.lines().mapNotNull { line ->
            val parts = line.split(
                ' ',
                limit = 3,
            ).takeIf { it.size == 3 && it[2].isNotEmpty() } ?: return@mapNotNull null
            val dir = parts[0] == "d"
            RamdiskEntry(parts[2], if (dir) 0L else parts[1].toLongOrNull() ?: 0L, dir)
        }

        private const val CPIO_SIZE_FIELD = 4
        private const val CPIO_NAME_FIELD = 8

        private fun lineChanges(old: String, new: String): List<String> {
            val a = old.lines()
            val b = new.lines()
            return (0 until maxOf(a.size, b.size)).mapNotNull { i ->
                val was = a.getOrNull(i)
                val now = b.getOrNull(i)
                when {
                    was == now -> null
                    was == null -> "Line ${i + 1}: + $now"
                    now == null -> "Line ${i + 1}: - $was"
                    else -> "Line ${i + 1}: - $was | + $now"
                }
            }
        }
    }
}

/** How the unpacked image was laid out, kept in `workbench/boot/<p>/state.json` for the repack. */
@Serializable
private data class BootState(
    val source: String,
    val ramdisk: String?,
    val compression: RamdiskCompression?,
    val footerImageSize: Long?,
    /** [BootWorkbench.FINGERPRINT_SCRIPT] of the folder when it was unpacked or last repacked. */
    val fingerprint: String? = null,
)

/** A ramdisk's compression, from its first bytes; the scripts take the input as `$1` and the output as `$2`. */
@Serializable
public enum class RamdiskCompression(
    public val decompress: String,
    public val compress: String,
    /** The kernel's name for its decoder (`CONFIG_RD_<name>`, `KernelFacts.ramdiskFormats`); null needs none. */
    public val kernelName: String?,
) {
    GZIP("gzip -dc \"$1\" > \"$2\"", "gzip -9nc \"$1\" > \"$2\"", "gzip"),
    LZ4_LEGACY("lz4 -dc \"$1\" > \"$2\"", "lz4 -l -12 --favor-decSpeed -c \"$1\" > \"$2\"", "lz4"),
    LZ4_FRAME("lz4 -dc \"$1\" > \"$2\"", "lz4 -12 -c \"$1\" > \"$2\"", "lz4"),
    XZ("xz -dc \"$1\" > \"$2\"", "xz --check=crc32 -c \"$1\" > \"$2\"", "xz"),
    NONE("cp -f \"$1\" \"$2\"", "cp -f \"$1\" \"$2\"", null),
    ;

    public companion object {
        public fun of(head: ByteArray): RamdiskCompression {
            fun starts(vararg b: Int) = head.size >= b.size && b.indices.all { head[it] == b[it].toByte() }
            return when {
                starts(0x1f, 0x8b) -> GZIP
                starts(0x02, 0x21, 0x4c, 0x18) -> LZ4_LEGACY
                starts(0x04, 0x22, 0x4d, 0x18) -> LZ4_FRAME
                starts(0xfd, 0x37, 0x7a, 0x58, 0x5a) -> XZ
                else -> NONE
            }
        }
    }
}
