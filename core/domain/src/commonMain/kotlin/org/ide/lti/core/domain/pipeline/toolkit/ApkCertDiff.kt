/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.toolkit

import kotlinx.serialization.Serializable

/** One X.509 signing certificate discovered inside an APK's v1 JAR block or v2/v3 APK Signing Block. */
@Serializable
data class ApkCertificateInfo(val scheme: String, val sha256: String, val subject: String, val issuer: String)

/** Parsed signing posture of a single APK file. */
@Serializable
data class ApkSignatureReport(
    val schemes: List<String> = emptyList(),
    val certificates: List<ApkCertificateInfo> = emptyList(),
) {
    val isSigned: Boolean get() = schemes.isNotEmpty() || certificates.isNotEmpty()
    val fingerprints: Set<String> get() = certificates.map { it.sha256.lowercase() }.toSet()
}

/** Side-by-side comparison of APK signing schemes and X.509 certificates between stock and build. */
@Serializable
data class ApkSignatureDiff(
    val left: ApkSignatureReport,
    val right: ApkSignatureReport,
    val schemesChanged: Boolean,
    val certificatesChanged: Boolean,
    val summary: String,
) {
    val hasSecurityChange: Boolean get() = certificatesChanged || !right.isSigned
}

/**
 * Extracts v1 (JAR PKCS#7) and v2/v3 (APK Signature Scheme Block) certificates using only
 * Python 3 standard library and `openssl`, and compares signing identity across two APK revisions.
 */
object ApkCertInspector {

    /** Read by `sh -c`: indentation removed, or Python refuses the heredoc's first line. */
    val INSPECT_SCRIPT: String = """
        [ -f "$1" ] && [ ! -L "$1" ] || exit 0
        python3 - "$1" << 'PY'
        import hashlib, re, struct, subprocess, sys, zipfile

        V2_ID = 0x7109871a
        V3_ID = 0xf05368c0

        def openssl_der_info(der):
            r = subprocess.run(
                ['openssl', 'x509', '-inform', 'DER', '-noout', '-subject', '-issuer'],
                input=der, capture_output=True
            )
            sha = hashlib.sha256(der).hexdigest()
            subj = iss = ''
            if r.returncode == 0:
                for line in r.stdout.decode(errors='replace').splitlines():
                    if line.startswith('subject='):
                        subj = line[len('subject='):].strip()
                    elif line.startswith('issuer='):
                        iss = line[len('issuer='):].strip()
            return sha, subj, iss

        def read_lp_items(buf):
            out, i = [], 0
            while i + 4 <= len(buf):
                ln = struct.unpack_from('<I', buf, i)[0]
                i += 4
                if i + ln > len(buf):
                    break
                out.append(buf[i:i+ln])
                i += ln
            return out

        def extract_v2_v3(path):
            schemes, certs = [], []
            with open(path, 'rb') as f:
                data = f.read()
            eocd = data.rfind(b'PK\x05\x06', max(0, len(data) - 65557))
            if eocd < 0 or eocd + 20 > len(data):
                return schemes, certs
            cd_off = struct.unpack_from('<I', data, eocd + 16)[0]
            if cd_off < 32 or cd_off > len(data) or data[cd_off - 16:cd_off] != b'APK Sig Block 42':
                return schemes, certs
            footer_sz = struct.unpack_from('<Q', data, cd_off - 24)[0]
            blk_start = cd_off - 8 - footer_sz
            if blk_start < 0 or struct.unpack_from('<Q', data, blk_start)[0] != footer_sz:
                return schemes, certs
            p = blk_start + 8
            end = cd_off - 24
            while p + 12 <= end:
                plen = struct.unpack_from('<Q', data, p)[0]
                p += 8
                if plen < 4 or p + plen > end:
                    break
                pid = struct.unpack_from('<I', data, p)[0]
                pval = data[p + 4:p + plen]
                p += plen
                if pid in (V2_ID, V3_ID):
                    scheme = 'v2' if pid == V2_ID else 'v3'
                    if scheme not in schemes:
                        schemes.append(scheme)
                    for signers_seq in read_lp_items(pval):
                        for signer in read_lp_items(signers_seq):
                            items = read_lp_items(signer)
                            if items:
                                signed_data_parts = read_lp_items(items[0])
                                if len(signed_data_parts) >= 2:
                                    for cert_der in read_lp_items(signed_data_parts[1]):
                                        sha, subj, iss = openssl_der_info(cert_der)
                                        certs.append((scheme, sha, subj, iss))
            return schemes, certs

        def extract_v1(path):
            schemes, certs = [], []
            try:
                with zipfile.ZipFile(path, 'r') as z:
                    sig_names = [
                        n for n in z.namelist()
                        if re.match(r'^META-INF/[^/]+\.(RSA|DSA|EC)$', n, re.IGNORECASE)
                    ]
                    if sig_names:
                        schemes.append('v1')
                    for name in sorted(sig_names):
                        p = subprocess.run(
                            ['openssl', 'pkcs7', '-inform', 'DER', '-print_certs'],
                            input=z.read(name), capture_output=True
                        )
                        if p.returncode != 0:
                            continue
                        pem_text = p.stdout.decode(errors='replace')
                        cert_pat = r'-----BEGIN CERTIFICATE-----.*?-----END CERTIFICATE-----'
                        for blk in re.findall(cert_pat, pem_text, re.S):
                            r = subprocess.run(
                                ['openssl', 'x509', '-noout', '-fingerprint', '-sha256', '-subject', '-issuer'],
                                input=blk.encode(), capture_output=True
                            )
                            if r.returncode != 0:
                                continue
                            sha = subj = iss = ''
                            for line in r.stdout.decode(errors='replace').splitlines():
                                if 'Fingerprint=' in line:
                                    sha = line.split('=', 1)[1].replace(':', '').lower().strip()
                                elif line.startswith('subject='):
                                    subj = line[len('subject='):].strip()
                                elif line.startswith('issuer='):
                                    iss = line[len('issuer='):].strip()
                            if sha:
                                certs.append(('v1', sha, subj, iss))
            except Exception:
                pass
            return schemes, certs

        p = sys.argv[1]
        s2, c2 = extract_v2_v3(p)
        s1, c1 = extract_v1(p)
        for s in sorted(set(s1 + s2)):
            print(f'scheme\t{s}')
        seen = set()
        for scheme, sha, subj, iss in c2 + c1:
            k = (scheme, sha)
            if k not in seen:
                seen.add(k)
                print(f'cert\t{scheme}\t{sha}\t{subj}\t{iss}')
        PY
    """.trimIndent()

    fun parseReport(output: String): ApkSignatureReport {
        val schemes = linkedSetOf<String>()
        val certs = mutableListOf<ApkCertificateInfo>()
        output.lineSequence().forEach { line ->
            val parts = line.split('\t')
            when (parts.firstOrNull()) {
                "scheme" -> parts.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }?.let(schemes::add)
                "cert" -> if (parts.size >= 5) {
                    certs += ApkCertificateInfo(
                        scheme = parts[1].trim(),
                        sha256 = parts[2].trim().lowercase(),
                        subject = parts[3].trim(),
                        issuer = parts[4].trim(),
                    )
                }
            }
        }
        return ApkSignatureReport(
            schemes = schemes.sorted(),
            certificates = certs.sortedWith(compareBy({ it.scheme }, { it.sha256 })),
        )
    }

    fun diff(left: ApkSignatureReport, right: ApkSignatureReport): ApkSignatureDiff {
        val schemesChanged = left.schemes.toSet() != right.schemes.toSet()
        val certsChanged = left.fingerprints != right.fingerprints
        val summary = when {
            !left.isSigned && !right.isSigned -> "Both APKs are unsigned."
            !right.isSigned -> "Removed signature on B (unsigned APK)."
            !left.isSigned -> "Added signature on B (${right.schemes.joinToString()})."
            certsChanged -> {
                val aShort = left.fingerprints.joinToString { it.take(12) }
                val bShort = right.fingerprints.joinToString { it.take(12) }
                "Signing certificate changed ($aShort -> $bShort)."
            }
            schemesChanged -> {
                val aSchemes = left.schemes.joinToString()
                val bSchemes = right.schemes.joinToString()
                "Same signing key; scheme changed ($aSchemes -> $bSchemes)."
            }
            else -> "Identical signing certificate (${right.schemes.joinToString()})."
        }
        return ApkSignatureDiff(
            left = left,
            right = right,
            schemesChanged = schemesChanged,
            certificatesChanged = certsChanged,
            summary = summary,
        )
    }
}
