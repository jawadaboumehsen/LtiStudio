/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.remote

import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import io.ltirom.tooling.core.remote.ToolExecutionResponse
import org.ide.lti.core.domain.ports.KeySource
import org.ide.lti.core.model.workspace.WorkspaceKeys

/**
 * Provisions per-workspace cryptographic keys (AVB 2.0 RSA-4096 and platform RSA-2048 x509+pk8)
 * strictly via [RemoteTransportPort] tool execution requests.
 */
public class WorkspaceKeyProvisioner(
    private val transport: RemoteTransportPort,
) {
    public suspend fun provisionKeys(
        workspaceRoot: String,
        keySource: KeySource = KeySource.GENERATE,
        globalAvbKeyPath: String? = null,
    ): WorkspaceKeys {
        val keysDir = "$workspaceRoot/keys"
        val avbPem = "$keysDir/avb.pem"
        val avbPub = "$keysDir/avb.avbpubkey"
        val platformKeyPem = "$keysDir/platform.key.pem"
        val platformCertPem = "$keysDir/platform.x509.pem"
        val platformPk8 = "$keysDir/platform.pk8"

        when (keySource) {
            KeySource.COPY_GLOBAL -> {
                val sourceKey = requireNotNull(globalAvbKeyPath) { "COPY_GLOBAL requires the global AVB key path" }
                copyGlobalAvbKey(workspaceRoot, sourceKey, avbPem)
            }
            KeySource.GENERATE -> generateAvbKey(workspaceRoot, avbPem)
        }
        extractAvbPublicKey(workspaceRoot, avbPem, avbPub)
        generatePlatformKeyPair(workspaceRoot, platformKeyPem, platformCertPem, platformPk8)

        return WorkspaceKeys(
            avbPrivateKey = "keys/avb.pem",
            avbPublicKey = "keys/avb.avbpubkey",
            platformPk8 = "keys/platform.pk8",
            platformCert = "keys/platform.x509.pem",
            avbPublicKeySha1 = sha1Of(workspaceRoot, avbPub),
            platformCertSha1 = sha1Of(workspaceRoot, platformCertPem),
        )
    }

    private suspend fun copyGlobalAvbKey(workspaceRoot: String, sourceKey: String, avbPem: String) {
        val cpRes = run(workspaceRoot, "cp", sourceKey, avbPem)
        check(cpRes.exitCode == 0) { "Failed to copy global AVB key: ${cpRes.stderr}" }
        run(workspaceRoot, "chmod", "600", avbPem)
    }

    private suspend fun generateAvbKey(workspaceRoot: String, avbPem: String) {
        val genRes = run(
            workspaceRoot, "openssl",
            "genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:4096", "-out", avbPem,
        )
        check(genRes.exitCode == 0) { "Failed to generate AVB RSA-4096 key: ${genRes.stderr}" }
        run(workspaceRoot, "chmod", "600", avbPem)

        val checkRes = run(workspaceRoot, "openssl", "pkey", "-text", "-noout", "-in", avbPem)
        check(checkRes.exitCode == 0) { "Failed to verify generated AVB key: ${checkRes.stderr}" }
    }

    private suspend fun extractAvbPublicKey(workspaceRoot: String, avbPem: String, avbPub: String) {
        val extractRes = run(workspaceRoot, "avbtool", "extract_public_key", "--key", avbPem, "--output", avbPub)
        check(extractRes.exitCode == 0) { "Failed to extract AVB public key: ${extractRes.stderr}" }
        run(workspaceRoot, "chmod", "644", avbPub)
    }

    private suspend fun generatePlatformKeyPair(
        workspaceRoot: String,
        platformKeyPem: String,
        platformCertPem: String,
        platformPk8: String,
    ) {
        val rsaRes = run(workspaceRoot, "openssl", "genrsa", "-out", platformKeyPem, "2048")
        check(rsaRes.exitCode == 0) { "Failed to generate platform RSA-2048 key: ${rsaRes.stderr}" }

        val certRes = run(
            workspaceRoot, "openssl",
            "req", "-new", "-x509", "-key", platformKeyPem, "-out", platformCertPem, "-days", "10000",
            "-subj", PLATFORM_CERT_SUBJECT,
        )
        check(certRes.exitCode == 0) { "Failed to generate platform certificate: ${certRes.stderr}" }

        val pk8Res = run(
            workspaceRoot, "openssl",
            "pkcs8", "-in", platformKeyPem, "-topk8", "-outform", "DER", "-out", platformPk8, "-nocrypt",
        )
        check(pk8Res.exitCode == 0) { "Failed to convert platform key to PK8: ${pk8Res.stderr}" }

        run(workspaceRoot, "chmod", "600", platformKeyPem, platformPk8)
        run(workspaceRoot, "chmod", "644", platformCertPem)
    }

    private suspend fun sha1Of(workspaceRoot: String, path: String): String? =
        run(workspaceRoot, "sha1sum", path).stdout.trim().split(Regex("\\s+")).firstOrNull()

    private suspend fun run(workingDirectory: String, toolId: String, vararg arguments: String): ToolExecutionResponse =
        transport.execute(
            ToolExecutionRequest(
                toolId = toolId,
                arguments = arguments.toList(),
                workingDirectory = workingDirectory,
            ),
        )

    private companion object {
        const val PLATFORM_CERT_SUBJECT =
            "/C=US/ST=N-A/L=N-A/O=LtiRom/OU=LtiRom/CN=LtiRom Platform/emailAddress=platform@ltirom.local"
    }
}
