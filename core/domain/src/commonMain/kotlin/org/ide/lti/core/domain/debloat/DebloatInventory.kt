/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.debloat

/**
 * The apps in the assembled work tree (`work/<partition>/…/<App>/<App>.apk`) and the rules that keep the
 * system bootable. Shared by the Debloat stage (which lists them during the run) and its editor.
 */
public object DebloatInventory {
    /** `find work -name '*.apk' -printf '%s\t%P\n'` arguments, relative to [workDir]. */
    public fun listingArgs(workDir: String): List<String> =
        listOf(workDir, "-mindepth", "3", "-maxdepth", "7", "-type", "f", "-name", "*.apk", "-printf", "%s\\t%P\\n")

    /**
     * One candidate per app folder: the folder holding the APKs, as `partition` + path inside that partition
     * (work-tree `system/system/app/Foo/Foo.apk` → partition `system`, path `system/app/Foo`).
     */
    public fun parse(listing: String): List<DebloatCandidate> = listing.lineSequence()
        .mapNotNull { line ->
            val (size, path) = line.trim().split('\t', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
            val dir = path.substringBeforeLast('/', "")
            val partition = dir.substringBefore('/')
            val inside = dir.substringAfter('/', "")
            if (partition.isEmpty() || inside.isEmpty() || partition in NOT_PARTITIONS) return@mapNotNull null
            Triple(partition, inside, size.toLongOrNull() ?: 0L)
        }
        .groupBy({ it.first to it.second }, { it.third })
        .map { (key, sizes) -> DebloatCandidate(key.first, key.second, sizes.sum(), packageId = null) }
        .sortedWith(compareBy({ it.partition }, { it.relativePath }))

    /** Apps whose removal breaks booting, setup, calls, installing apps or the settings screen. */
    public fun isProtected(candidate: DebloatCandidate): Boolean {
        val name = candidate.relativePath.substringAfterLast('/')
        // Vendor builds rename them (SystemUI_MFV_tablet_abroad, Telecom_MFV, WebViewGoogle64,
        // DocumentsUIGoogle): a protected name also covers its variants with a suffix.
        return PROTECTED_NAMES.any { protectedName -> variantOf(name, protectedName) } ||
            PROTECTED_PREFIXES.any { name.startsWith(it, ignoreCase = true) }
    }

    /** `partition:path` selectors of the protected apps in [inventory], for [DebloatResolver]. */
    public fun protectedSelectors(inventory: List<DebloatCandidate>): List<String> =
        inventory.filter(::isProtected).map { "${it.partition}:${it.relativePath}" }

    /** [name] is [base], or [base] followed by `_…`, `Google…`, a digit, or `Legacy`. */
    private fun variantOf(name: String, base: String): Boolean {
        if (!name.startsWith(base, ignoreCase = true)) return false
        val rest = name.substring(base.length)
        return rest.isEmpty() || rest.first() == '_' || rest.first().isDigit() ||
            VARIANT_SUFFIXES.any { rest.startsWith(it, ignoreCase = true) }
    }

    private val VARIANT_SUFFIXES = listOf("Google", "Legacy")

    private val NOT_PARTITIONS = setOf("configs", "kernel")

    private val PROTECTED_NAMES = setOf(
        "SystemUI", "SettingsProvider", "PermissionController", "GooglePermissionController",
        "PackageInstaller", "GooglePackageInstaller", "Shell", "ExtServices", "GoogleExtServices",
        "NetworkStack", "NetworkStackGoogle", "TeleService", "Telecom", "TelephonyProvider", "Phone",
        "Bluetooth", "BluetoothMidiService", "MediaProvider", "MediaProviderGoogle", "DocumentsUI",
        "DownloadProvider", "KeyChain", "CertInstaller", "InputDevices", "CarrierConfig", "NfcNci",
        "WebViewGoogle", "TrichromeLibrary", "FusedLocation", "ExternalStorageProvider", "ContactsProvider",
        "CaptivePortalLogin", "Provision", "SetupWizard", "StatementService", "DynamicSystemInstallationService",
        "LocalTransport", "BackupRestoreConfirmation", "ManagedProvisioning", "CompanionDeviceManager",
        "Phonesky", "GmsCore", "PrebuiltGmsCore", "GoogleServicesFramework",
    )

    /** Settings and launcher variants carry vendor suffixes (`Settings_MFV_abroad_tablet`). */
    private val PROTECTED_PREFIXES = listOf("Settings", "Launcher", "framework-res")
}
