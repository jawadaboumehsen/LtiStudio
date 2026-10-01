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

import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import org.ide.lti.core.model.workspace.ExtractionSettings
import java.security.MessageDigest

public object CacheKeys {
    public fun sha256(text: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(text.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    public fun stage2ExtractionKey(stage1Key: String, target: TargetDevice): String =
        stage2ExtractionKey(stage1Key, target, ExtractionSettings())

    public fun stage2ExtractionKey(stage1Key: String, target: TargetDevice, extraction: ExtractionSettings): String {
        val raw = listOf(
            stage1Key,
            target.dynamicPartitions.sorted().joinToString(","),
            target.bootPartitions.sorted().joinToString(","),
            target.activeSlotSuffix,
            extraction.adapter,
            extraction.dynamicPartitions.sorted().joinToString(","),
            extraction.bootPartitions.sorted().joinToString(","),
            extraction.slot.orEmpty(),
            extraction.reuseVerifiedExtraction.toString(),
        ).joinToString("|")
        return sha256(raw)
    }

    public fun stage2ExtractionKey(stage1Key: String, target: TargetDevice, snapshot: ConfigurationSnapshot): String =
        stage2ExtractionKey(stage1Key, target, snapshot.extraction)

    public fun stage3AssemblyKey(stage2Key: String, target: TargetDevice, snapshot: ConfigurationSnapshot): String {
        val raw = listOf(
            stage2Key,
            target.hasStandaloneSystemExt.toString(),
            snapshot.assembly.buildType,
            snapshot.assembly.romVersion,
            snapshot.assembly.includedPartitions.sorted().joinToString(","),
            snapshot.assembly.systemExtMode,
            snapshot.assembly.bootFooterPolicy,
            snapshot.assembly.baselineCleanup.toString(),
            snapshot.assembly.propertyOverrides.toSortedMap().entries.joinToString(",") { "${it.key}=${it.value}" },
        ).joinToString("|")
        return sha256(raw)
    }

    public fun stage4ModuleKey(stage3Key: String, snapshot: ConfigurationSnapshot): String {
        val raw = listOf(
            stage3Key,
            snapshot.customization.enabledPackages.sorted().joinToString(","),
        ).joinToString("|")
        return sha256(raw)
    }

    public fun stage5BuildZipKey(
        stage4Key: String,
        snapshot: ConfigurationSnapshot,
        avbPubkeySha1: String = "",
        platformCertSha1: String = "",
    ): String {
        val raw = listOf(
            stage4Key,
            snapshot.build.filesystem,
            snapshot.build.compression,
            snapshot.build.level?.toString().orEmpty(),
            snapshot.build.blockSize.toString(),
            snapshot.build.alignment?.toString().orEmpty(),
            snapshot.build.packagePolicy.flashablePartitions.sorted().joinToString(","),
            snapshot.build.packagePolicy.flashableBootPartitions.sorted().joinToString(","),
            snapshot.build.packagePolicy.bootSlots.sorted().joinToString(","),
            snapshot.build.packagePolicy.excludeVbmeta.toString(),
            snapshot.build.packagePolicy.recoverySystemMountPoint,
            snapshot.build.packagePolicy.metadataPolicy.name,
            snapshot.build.signing.signImages.toString(),
            snapshot.build.signing.signPackage.toString(),
            // The template names the produced zip, so it is part of this stage's identity: a different
            // name is a different artifact even when the image bytes are identical.
            snapshot.build.filenameTemplate,
            avbPubkeySha1,
            platformCertSha1,
            snapshot.assembly.romVersion,
        ).joinToString("|")
        return sha256(raw)
    }

    public fun stage6ManifestKey(stage5Key: String, snapshot: ConfigurationSnapshot): String {
        val raw = listOf(
            stage5Key,
            snapshot.release.otaBaseUrl,
            snapshot.release.channel,
            snapshot.release.changelog,
        ).joinToString("|")
        return sha256(raw)
    }
}
