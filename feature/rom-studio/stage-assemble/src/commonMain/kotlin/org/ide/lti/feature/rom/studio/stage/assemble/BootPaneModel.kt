/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.assemble

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ide.lti.core.domain.pipeline.toolkit.BootFlagDiff
import org.ide.lti.core.domain.pipeline.toolkit.BootParts
import org.ide.lti.core.domain.pipeline.toolkit.DeviceTreeDiff
import org.ide.lti.core.domain.pipeline.toolkit.KernelFacts
import org.ide.lti.core.domain.pipeline.toolkit.Refusal
import org.ide.lti.core.domain.pipeline.toolkit.RefusalException
import org.ide.lti.core.domain.pipeline.toolkit.RootStatus
import org.ide.lti.core.domain.pipeline.toolkit.VbmetaFlag
import org.ide.lti.core.domain.ports.AppEnvironmentPort
import org.ide.lti.core.domain.workbench.BootWorkbench
import org.ide.lti.core.domain.workbench.RiskyFeature
import org.ide.lti.core.domain.workbench.WorkbenchRunner

/** The boot-chain workbench as the pane shows it (FR-020..FR-026). */
public data class BootPaneState(
    val image: String = "boot",
    val parts: BootParts? = null,
    val rootStatus: RootStatus = RootStatus.NotPatched,
    val deviceTree: String? = null,
    val deviceTreeDiff: DeviceTreeDiff? = null,
    val signWithWorkspaceKey: Boolean = true,
    val vbmetaFlags: Int? = null,
    val vbmetaEdited: Int? = null,
    val busy: String? = null,
    val message: String? = null,
    val refusal: Refusal? = null,
    /** A risky feature waiting for its acknowledgement (FR-035), and the action it unblocks. */
    val pendingAck: String? = null,
    /** The boot kernel's version and what it can read; null until read. */
    val kernel: KernelFacts? = null,
    val kernelRead: Boolean = false,
) {
    val vbmetaDiff: BootFlagDiff?
        get() = if (vbmetaFlags != null && vbmetaEdited != null) {
            BootFlagDiff.compute(vbmetaFlags, vbmetaEdited)
        } else {
            null
        }
}

/** What the boot pane can ask for; each one is a real job in the workspace (see [BootPaneModel]). */
public class BootPaneActions(
    public val selectImage: (String) -> Unit = {},
    public val unpack: () -> Unit = {},
    public val openRamdisk: () -> Unit = {},
    public val removeVerityKeys: () -> Unit = {},
    public val editDeviceTree: (String) -> Unit = {},
    public val previewDeviceTree: () -> Unit = {},
    public val saveDeviceTree: () -> Unit = {},
    public val setSign: (Boolean) -> Unit = {},
    public val repack: () -> Unit = {},
    public val dropOverride: (String) -> Unit = {},
    public val importPatched: () -> Unit = {},
    public val patchApatch: (String) -> Unit = {},
    public val loadVbmeta: () -> Unit = {},
    public val toggleVbmeta: (VbmetaFlag) -> Unit = {},
    public val writeVbmeta: () -> Unit = {},
    public val cancel: () -> Unit = {},
    public val dismiss: () -> Unit = {},
    public val acknowledge: (String) -> Unit = {},
    public val dismissAck: () -> Unit = {},
    public val readKernel: () -> Unit = {},
    /** Adds EROFS, ext4 and F2FS mount lines to the first-stage fstab and saves the image as an override. */
    public val addMountLines: () -> Unit = {},
)

/**
 * The boot pane's state and jobs. A repacked or imported image becomes the partition's override, recorded in the
 * Assemble settings through [overrides] / [setOverrides] (the next build uses it).
 */
public class BootPaneModel(
    private val scope: CoroutineScope,
    private val workspaceId: String,
    private val boot: BootWorkbench?,
    private val runner: WorkbenchRunner?,
    private val environment: AppEnvironmentPort?,
    private val pickFile: suspend () -> String?,
    private val overrides: () -> Map<String, String>,
    private val setOverrides: (Map<String, String>) -> Unit,
    /** The device's boot-chain partitions, for the mount table fix (vendor_boot when there is one). */
    private val bootImages: () -> List<String> = { emptyList() },
) {
    public companion object {
        /** Where the first-stage fstab lives, in order of preference. */
        public val MOUNT_TABLE_IMAGES: List<String> = listOf("vendor_boot", "boot")
    }

    private val mutableState = MutableStateFlow(BootPaneState())
    public val state: StateFlow<BootPaneState> = mutableState.asStateFlow()

    private var job: Job? = null
    private var afterAck: (() -> Unit)? = null

    public val actions: BootPaneActions = BootPaneActions(
        selectImage = { image ->
            job?.cancel()
            mutableState.value = BootPaneState(image = image, signWithWorkspaceKey = state.value.signWithWorkspaceKey)
        },
        unpack = ::unpack,
        openRamdisk = ::openRamdisk,
        removeVerityKeys = ::removeVerityKeys,
        editDeviceTree = { text -> mutableState.update { it.copy(deviceTree = text, deviceTreeDiff = null) } },
        previewDeviceTree = ::previewDeviceTree,
        saveDeviceTree = ::saveDeviceTree,
        setSign = { sign -> mutableState.update { it.copy(signWithWorkspaceKey = sign) } },
        repack = { withAck(RiskyFeature.KERNEL_OVERRIDE, ::repack) },
        addMountLines = { withAck(RiskyFeature.KERNEL_OVERRIDE, ::addMountLines) },
        dropOverride = ::dropOverride,
        importPatched = { withAck(RiskyFeature.ROOT_PATCH, ::importPatched) },
        patchApatch = { key -> withAck(RiskyFeature.ROOT_PATCH) { patchApatch(key) } },
        loadVbmeta = ::loadVbmeta,
        toggleVbmeta = { flag ->
            mutableState.update { s ->
                s.vbmetaEdited?.let {
                    s.copy(
                        vbmetaEdited = BootFlagDiff.toggleFlag(it, flag),
                    )
                } ?: s
            }
        },
        writeVbmeta = ::writeVbmeta,
        cancel = { job?.cancel() },
        dismiss = { mutableState.update { it.copy(message = null, refusal = null) } },
        acknowledge = ::acknowledge,
        readKernel = ::readKernel,
        dismissAck = {
            afterAck = null
            mutableState.update { it.copy(pendingAck = null) }
        },
    )

    private fun unpack() = task("Unpacking ${state.value.image}") { boot ->
        val image = state.value.image
        val parts = boot.unpack(workspaceId, image)
        val dts = boot.deviceTree(workspaceId, image)
        val root = boot.rootStatus(workspaceId, image)
        mutableState.update { it.copy(parts = parts, deviceTree = dts, deviceTreeDiff = null, rootStatus = root) }
    }

    private fun openRamdisk() = task("Opening the ramdisk") { boot ->
        val folder = boot.ramdiskFolder(workspaceId, state.value.image)
        val opened = environment?.openFolder(folder) == true
        if (!opened) mutableState.update { it.copy(message = "Open $folder in Explorer") }
    }

    private fun removeVerityKeys() = task("Removing verity keys") { boot ->
        val result = boot.removeVerityKeys(workspaceId, state.value.image)
        val text = if (result.removedKeys.isEmpty()) {
            "No dm-verity key in the ramdisk."
        } else {
            "Removed ${result.removedKeys.joinToString()}. Repack to keep the change."
        }
        mutableState.update { it.copy(message = text) }
    }

    private fun previewDeviceTree() {
        val text = state.value.deviceTree ?: return
        task("Comparing the device tree") { boot ->
            val diff = boot.previewDeviceTree(workspaceId, state.value.image, text)
            mutableState.update { it.copy(deviceTreeDiff = diff) }
        }
    }

    private fun saveDeviceTree() {
        val text = state.value.deviceTree ?: return
        task("Saving the device tree") { boot ->
            boot.saveDeviceTree(workspaceId, state.value.image, text)
            mutableState.update {
                it.copy(
                    deviceTreeDiff = null,
                    message = "Device tree saved. Repack to build it in.",
                )
            }
        }
    }

    /**
     * Lets the phone mount every dynamic partition as ext4 or EROFS from its first stage: the fstab inside
     * vendor_boot (boot where there is none) gets the missing lines, and the image is repacked as its override.
     */
    private fun addMountLines() = task("Adding mount lines") { boot ->
        val image = MOUNT_TABLE_IMAGES.firstOrNull { it in bootImages() } ?: return@task
        boot.unpack(workspaceId, image)
        val changed = boot.addMountLines(workspaceId, image)
        if (changed.isEmpty()) {
            mutableState.update { it.copy(message = "The $image fstab already mounts every partition both ways.") }
            return@task
        }
        repackAs(boot, image)
        mutableState.update {
            it.copy(
                message = "Added ext4/EROFS lines to ${changed.joinToString { f ->
                    f.substringAfterLast(
                        '/',
                    )
                }}; " + (it.message ?: ""),
            )
        }
    }

    /** Repacks the unpacked image and makes it the partition's override. */
    private fun repack() = task("Repacking ${state.value.image}") { boot -> repackAs(boot, state.value.image) }

    private suspend fun repackAs(boot: BootWorkbench, image: String) {
        val plan = if (state.value.signWithWorkspaceKey) {
            boot.signingPlan(workspaceId, image, AssembleViewModel.AVB_KEY_REF)
        } else {
            null
        }
        val saved = boot.repack(workspaceId, image, plan)
        if (saved.check.ok) {
            setOverrides(overrides() + (image to saved.sha256))
            mutableState.update { it.copy(message = "Saved as the $image override; the next build uses it.") }
        } else {
            val refusal = Refusal(
                cause = "The rebuilt ramdisk does not match the folder.",
                item = "$image.img",
                nextStep = "Check the listed paths in the ramdisk folder and repack again.",
                details = saved.check.differences.joinToString("\n"),
            )
            mutableState.update { it.copy(refusal = refusal) }
        }
    }

    /** Imports a boot image patched in the Magisk app as the partition's override (FR-026). */
    private fun importPatched() {
        scope.launch {
            val path = pickFile() ?: return@launch
            task("Importing ${path.substringAfterLast('\\')}") { boot ->
                val image = state.value.image
                val saved = boot.importPatched(workspaceId, image, path)
                setOverrides(overrides() + (image to saved.sha256))
                mutableState.update { it.copy(message = "Imported as the $image override; the next build uses it.") }
            }
        }
    }

    /** Patches the unpacked kernel with APatch and [superKey]; repack to keep it (FR-026). */
    private fun patchApatch(superKey: String) = task("Patching with APatch") { boot ->
        val image = state.value.image
        boot.patchApatch(workspaceId, image, superKey)
        val root = boot.rootStatus(workspaceId, image)
        mutableState.update { it.copy(rootStatus = root, message = "Kernel patched. Repack to keep it.") }
    }

    /** Uses the stock image again for [image]: the override is no longer named in the settings. */
    private fun dropOverride(image: String) {
        setOverrides(overrides() - image)
        mutableState.update { it.copy(message = "$image uses the extracted image again.") }
    }

    private fun readKernel() = task("Reading the kernel") { boot ->
        val facts = boot.kernelFacts(workspaceId)
        mutableState.update { it.copy(kernel = facts, kernelRead = true) }
    }

    private fun loadVbmeta() = task("Reading vbmeta") { boot ->
        val flags = boot.vbmetaFlags(workspaceId)
        mutableState.update { it.copy(vbmetaFlags = flags, vbmetaEdited = flags) }
    }

    /** Writes a copy of vbmeta with the edited flags to `out/toolkit/` (vbmeta is flashed by hand) (FR-023). */
    private fun writeVbmeta() {
        val flags = state.value.vbmetaEdited ?: return
        withAck(RiskyFeature.VBMETA_FLAGS) {
            task("Writing vbmeta") { boot ->
                val (diff, out) = boot.writeVbmetaFlags(workspaceId, flags)
                val text = "Wrote $out (${diff.describe().joinToString("; ")})"
                mutableState.update { it.copy(vbmetaFlags = diff.newFlags, message = text) }
            }
        }
    }

    private fun acknowledge(feature: String) {
        val next = afterAck
        afterAck = null
        mutableState.update { it.copy(pendingAck = null) }
        scope.launch {
            runner?.acknowledge(workspaceId, feature)
            next?.invoke()
        }
    }

    /** Runs [action] once [feature] is acknowledged in this workspace, asking first when it is not (FR-035). */
    private fun withAck(feature: String, action: () -> Unit) {
        scope.launch {
            if (runner == null || runner.isAcknowledged(workspaceId, feature)) {
                action()
            } else {
                afterAck = action
                mutableState.update { it.copy(pendingAck = feature) }
            }
        }
    }

    private fun task(name: String, block: suspend (BootWorkbench) -> Unit) {
        val workbench = boot ?: return
        if (state.value.busy != null) return
        mutableState.update { it.copy(busy = name, message = null, refusal = null) }
        job = scope.launch {
            try {
                block(workbench)
            } catch (e: CancellationException) {
                mutableState.update { it.copy(message = "$name cancelled.") }
                throw e
            } catch (e: RefusalException) {
                mutableState.update { it.copy(refusal = e.refusal) }
            } catch (e: IllegalArgumentException) {
                val refusal = Refusal(e.message ?: "Invalid input.", name, "Correct it and try again.")
                mutableState.update { it.copy(refusal = refusal) }
            } finally {
                mutableState.update { it.copy(busy = null) }
            }
        }
    }
}
