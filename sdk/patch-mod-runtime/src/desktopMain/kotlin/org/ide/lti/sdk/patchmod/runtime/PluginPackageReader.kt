/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.sdk.patchmod.runtime

import kotlinx.serialization.SerializationException
import org.ide.lti.core.domain.pipeline.configuration.Severity
import org.ide.lti.core.domain.pipeline.configuration.ValidationError
import org.ide.lti.core.domain.pipeline.configuration.ValidationReport
import org.ide.lti.core.domain.plugin.PluginPackageCodec
import org.ide.lti.core.domain.plugin.PluginPackagePolicy
import org.ide.lti.core.domain.plugin.PluginPlanValidator
import org.ide.lti.core.model.plugin.PayloadEntry
import org.ide.lti.core.model.plugin.PluginManifest
import org.ide.lti.core.model.plugin.PluginPlanTemplate
import org.ide.lti.core.model.run.StageId
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipFile

data class ReadPackage(
    val manifest: PluginManifest,
    val template: PluginPlanTemplate,
    val readme: String,
    val license: String,
    val contentDigest: String,
    val hasSignature: Boolean,
)

sealed interface PackageReadResult {
    data class Ok(val pkg: ReadPackage) : PackageReadResult
    data class Rejected(val report: ValidationReport) : PackageReadResult
}

/**
 * Reads a `.lti-mod.zip` without extracting it. Every size limit is enforced while streaming - entry
 * headers are never trusted - and each check runs before the next, more expensive one.
 */
object PluginPackageReader {
    const val PACKAGE_TOO_LARGE: String = "PACKAGE_TOO_LARGE"
    const val MEMBER_TOO_LARGE: String = "MEMBER_TOO_LARGE"
    const val PACKAGE_EXPANDED_TOO_LARGE: String = "PACKAGE_EXPANDED_TOO_LARGE"
    const val UNSUPPORTED_SCHEMA: String = "UNSUPPORTED_SCHEMA"
    const val INVALID_JSON: String = "INVALID_JSON"
    const val PAYLOAD_MISMATCH: String = "PAYLOAD_MISMATCH"
    const val INVALID_ZIP: String = "INVALID_ZIP"

    // The policy has no separate LICENSE limit; a licence text is bounded like the other small text members.
    private const val LICENSE_MAX_BYTES = PluginPackagePolicy.PLAN_MAX_BYTES

    fun read(zip: Path): PackageReadResult = try {
        PackageReadResult.Ok(Reader(zip).read())
    } catch (e: Rejection) {
        PackageReadResult.Rejected(e.report)
    }

    private class Rejection(val report: ValidationReport) : RuntimeException()

    private class Reader(private val zip: Path) {
        private var expandedBytes = 0L

        fun read(): ReadPackage {
            val compressed = runCatching { Files.size(zip) }.getOrElse { reject("zip", INVALID_ZIP, it.message) }
            if (compressed > PluginPackagePolicy.PACKAGE_MAX_COMPRESSED_BYTES) {
                reject("zip", PACKAGE_TOO_LARGE, "Package compressed size ($compressed) exceeds limit")
            }
            val zf = runCatching { ZipFile(zip.toFile()) }.getOrElse { reject("zip", INVALID_ZIP, it.message) }
            return zf.use { readEntries(it) }
        }

        private fun readEntries(zf: ZipFile): ReadPackage {
            val members = zf.entries().asSequence().map { it.name }.toList()
            if (PluginPackageCodec.MANIFEST !in members) {
                reject(
                    PluginPackageCodec.MANIFEST,
                    PluginPackageCodec.MISSING_REQUIRED_MEMBER,
                    "manifest.json is missing",
                )
            }
            val manifest =
                decode(
                    PluginPackageCodec.MANIFEST,
                    PluginPackagePolicy.MANIFEST_MAX_BYTES,
                    zf,
                    PluginPackageCodec::decodeManifest,
                )
            if (manifest.schemaVersion != 1) {
                reject(PluginPackageCodec.MANIFEST, UNSUPPORTED_SCHEMA, "Unsupported schemaVersion")
            }
            PluginPackageCodec.validateMembers(members, manifest).rejectIfBlocking()

            val template =
                decode(PluginPackageCodec.PLAN, PluginPackagePolicy.PLAN_MAX_BYTES, zf, PluginPackageCodec::decodePlan)
            text(zf, PluginPackageCodec.SETTINGS, PluginPackagePolicy.SETTINGS_MAX_BYTES) // size cap only
            val readme = text(zf, PluginPackageCodec.README, PluginPackagePolicy.README_MAX_BYTES)
            val license = text(zf, PluginPackageCodec.LICENSE, LICENSE_MAX_BYTES)
            PluginPlanValidator.validate(template).rejectIfBlocking()
            verifyPayloads(zf, manifest.payloads)

            return ReadPackage(
                manifest = manifest,
                template = template,
                readme = readme,
                license = license,
                contentDigest = PluginPackageCodec.contentIdentity(manifest.payloads),
                hasSignature = zf.getEntry(PluginPackageCodec.SIGNATURE) != null,
            )
        }

        private fun <T> decode(member: String, cap: Long, zf: ZipFile, decoder: (String) -> T): T = try {
            decoder(text(zf, member, cap))
        } catch (e: SerializationException) {
            reject(member, INVALID_JSON, e.message)
        } catch (e: IllegalArgumentException) {
            reject(member, INVALID_JSON, e.message)
        }

        private fun text(zf: ZipFile, member: String, cap: Long): String =
            BoundedStream(zf.getInputStream(zf.getEntry(member)), member, cap).use {
                it.reader(Charsets.UTF_8).readText()
            }

        private fun verifyPayloads(zf: ZipFile, payloads: List<PayloadEntry>) {
            val buffer = ByteArray(PluginPackagePolicy.TRANSFER_CHUNK_BYTES.toInt())
            val errors = payloads.mapNotNull { payload ->
                val md = MessageDigest.getInstance("SHA-256")
                var size = 0L
                BoundedStream(zf.getInputStream(zf.getEntry(payload.path)), payload.path, Long.MAX_VALUE).use { input ->
                    while (true) {
                        val n = input.read(buffer)
                        if (n == -1) break
                        size += n
                        md.update(buffer, 0, n)
                    }
                }
                val sha = md.digest().joinToString("") { "%02x".format(it) }
                if (size == payload.sizeBytes && sha == payload.sha256) {
                    null
                } else {
                    error(
                        payload.path,
                        PAYLOAD_MISMATCH,
                        "Payload '${payload.path}' mismatch: expected size=${payload.sizeBytes}, " +
                            "sha256=${payload.sha256}; actual size=$size, sha256=$sha",
                    )
                }
            }
            ValidationReport(errors = errors).rejectIfBlocking()
        }

        private fun ValidationReport.rejectIfBlocking() {
            if (hasBlockingErrors()) throw Rejection(this)
        }

        private fun reject(member: String, code: String, message: String?): Nothing =
            throw Rejection(ValidationReport(errors = listOf(error(member, code, message ?: code))))

        /** Counts every byte read; a member over [cap] or a package over the expanded limit rejects immediately. */
        private inner class BoundedStream(
            private val delegate: InputStream,
            private val member: String,
            private val cap: Long,
        ) : InputStream() {
            private var consumed = 0L

            override fun read(): Int = delegate.read().also { if (it != -1) count(1) }

            override fun read(b: ByteArray, off: Int, len: Int): Int = delegate.read(b, off, len).also {
                if (it >
                    0
                ) {
                    count(it)
                }
            }

            override fun close() = delegate.close()

            private fun count(n: Int) {
                consumed += n
                expandedBytes += n
                if (consumed > cap) reject(member, MEMBER_TOO_LARGE, "Member exceeds size cap: '$member'")
                if (expandedBytes > PluginPackagePolicy.PACKAGE_MAX_EXPANDED_BYTES) {
                    reject(member, PACKAGE_EXPANDED_TOO_LARGE, "Package expanded size exceeds limit at: '$member'")
                }
            }
        }
    }

    private fun error(member: String, code: String, message: String) =
        ValidationError(StageId.MODULE_APPLICATION, member, member, code, Severity.ERROR, message)
}
