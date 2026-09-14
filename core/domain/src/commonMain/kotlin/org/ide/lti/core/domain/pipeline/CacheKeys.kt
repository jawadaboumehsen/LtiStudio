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
import java.security.MessageDigest

public object CacheKeys {
    public fun sha256(text: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(text.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    public fun stage2ExtractionKey(stage1Key: String, target: TargetDevice): String {
        val raw = listOf(
            stage1Key,
            target.dynamicPartitions.sorted().joinToString(","),
            target.bootPartitions.sorted().joinToString(","),
            target.activeSlotSuffix,
        ).joinToString("|")
        return sha256(raw)
    }

    public fun stage3AssemblyKey(stage2Key: String, target: TargetDevice, snapshot: ConfigurationSnapshot): String {
        val raw = listOf(
            stage2Key,
            target.hasStandaloneSystemExt.toString(),
            snapshot.assembly.buildType,
            snapshot.assembly.romVersion,
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
            snapshot.build.packagePolicy.flashablePartitions.sorted().joinToString(","),
            snapshot.build.packagePolicy.flashableBootPartitions.sorted().joinToString(","),
            snapshot.build.signing.signImages.toString(),
            snapshot.build.signing.signPackage.toString(),
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
        ).joinToString("|")
        return sha256(raw)
    }
}
