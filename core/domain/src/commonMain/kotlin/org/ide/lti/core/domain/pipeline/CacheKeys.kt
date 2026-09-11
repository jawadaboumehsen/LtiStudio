package org.ide.lti.core.domain.pipeline

import java.security.MessageDigest
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.ConfigurationSnapshot

public object CacheKeys {
    public fun sha256(text: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(text.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    public fun stage1AcquisitionKey(snapshot: ConfigurationSnapshot): String {
        val raw = listOf(
            snapshot.firmware.acquisitionUrl.orEmpty(),
            if (snapshot.firmware.checksumUnavailable) "UNVERIFIED" else snapshot.firmware.sha256.orEmpty(),
            snapshot.firmware.sourceKind.name,
            snapshot.acquisitionMode.name
        ).joinToString("|")
        return sha256(raw)
    }

    public fun stage2ExtractionKey(stage1Key: String, target: TargetDevice): String {
        val raw = listOf(
            stage1Key,
            target.dynamicPartitions.sorted().joinToString(","),
            target.bootPartitions.sorted().joinToString(","),
            target.activeSlotSuffix
        ).joinToString("|")
        return sha256(raw)
    }

    public fun stage3AssemblyKey(stage2Key: String, target: TargetDevice, snapshot: ConfigurationSnapshot): String {
        val raw = listOf(
            stage2Key,
            target.hasStandaloneSystemExt.toString(),
            snapshot.buildType,
            snapshot.romVersion
        ).joinToString("|")
        return sha256(raw)
    }

    public fun stage4ModuleKey(stage3Key: String, snapshot: ConfigurationSnapshot): String {
        val raw = listOf(
            stage3Key,
            snapshot.enabledModuleIds.sorted().joinToString(",")
        ).joinToString("|")
        return sha256(raw)
    }

    public fun stage5BuildZipKey(
        stage4Key: String,
        snapshot: ConfigurationSnapshot,
        avbPubkeySha1: String = "",
        platformCertSha1: String = ""
    ): String {
        val raw = listOf(
            stage4Key,
            snapshot.packagePolicy.flashablePartitions.sorted().joinToString(","),
            snapshot.packagePolicy.flashableBootPartitions.sorted().joinToString(","),
            snapshot.signing.signImages.toString(),
            snapshot.signing.signPackage.toString(),
            avbPubkeySha1,
            platformCertSha1,
            snapshot.romVersion
        ).joinToString("|")
        return sha256(raw)
    }

    public fun stage6ManifestKey(stage5Key: String, snapshot: ConfigurationSnapshot): String {
        val raw = listOf(
            stage5Key,
            snapshot.otaBaseUrl
        ).joinToString("|")
        return sha256(raw)
    }
}
