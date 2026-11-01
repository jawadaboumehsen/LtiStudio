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

/** What an app's manifest says that matters when removing it. */
public data class AppFacts(
    val packageId: String? = null,
    val sharedUserId: String? = null,
    /** `android:persistent`: Android keeps it running all the time. */
    val persistent: Boolean = false,
    /** An activity (or activity-alias) in the HOME category: a home screen (launcher, setup, fallback). */
    val home: Boolean = false,
    /** A service for `android.view.InputMethod`: a keyboard or other input method. */
    val inputMethod: Boolean = false,
    /** A receiver for `BOOT_COMPLETED`: it starts by itself after every boot. */
    val bootReceiver: Boolean = false,
    /** Libraries it cannot start without (`<uses-library>` that is not `required="false"`). */
    val requiredLibraries: Set<String> = emptySet(),
    /** Libraries it provides to other apps (`<library>`). */
    val providedLibraries: Set<String> = emptySet(),
    /** Packages this app is a resource overlay for. */
    val overlayTargets: Set<String> = emptySet(),
    /** `versionName`, as the manifest gives it. */
    val version: String? = null,
    /** `targetSdkVersion`: the Android version the app was made for. */
    val targetSdk: Int? = null,
    /** Packages it asks to see by name (`<queries><package>`): it looks for them when it runs. */
    val queries: Set<String> = emptySet(),
    /** The name a user sees ("Game Space"), when the app has one. */
    val label: String? = null,
    /** Its icon's file name in the icon folder [AppManifests.LABELS_SCRIPT] wrote (bitmap icons only). */
    val icon: String? = null,
    /** The first signer's certificate subject (RFC 2253), as [AppManifests.SIGNERS_SCRIPT] read it. */
    val signer: String? = null,
    /** The first 16 hex digits of that certificate's SHA-256: equal digests are the same signing key. */
    val signerDigest: String? = null,
    /** aapt2 could not read its manifest: every fact above is unknown, not absent. */
    val unreadable: Boolean = false,
) {
    /** The signer's common name (`CN=`), else its organisation, else the whole subject. */
    public val signerName: String?
        get() = signer?.let { s ->
            listOf("CN", "O").firstNotNullOfOrNull { key ->
                Regex("""(?:^|,)$key=((?:\\,|[^,])*)""").find(s)?.groupValues?.get(1)?.replace("\\,", ",")
            } ?: s
        }

    /** Two APKs of one app (splits) as one app. */
    public operator fun plus(other: AppFacts): AppFacts = AppFacts(
        packageId = packageId ?: other.packageId,
        sharedUserId = sharedUserId ?: other.sharedUserId,
        persistent = persistent || other.persistent,
        home = home || other.home,
        inputMethod = inputMethod || other.inputMethod,
        bootReceiver = bootReceiver || other.bootReceiver,
        requiredLibraries = requiredLibraries + other.requiredLibraries,
        providedLibraries = providedLibraries + other.providedLibraries,
        overlayTargets = overlayTargets + other.overlayTargets,
        version = version ?: other.version,
        targetSdk = targetSdk ?: other.targetSdk,
        queries = queries + other.queries,
        label = label ?: other.label,
        icon = icon ?: other.icon,
        signer = signer ?: other.signer,
        signerDigest = signerDigest ?: other.signerDigest,
        unreadable = unreadable || other.unreadable,
    )
}

/**
 * Reads every APK manifest of the work tree with aapt2 (the one apktool ships, cached by the jar's hash), eight at a
 * time, and keeps only the [AppFacts] lines: the whole xmltree of a ROM is about 15 MB, the facts under 100 KB.
 */
public object AppManifests {
    private const val HEX = 16

    /** `sh -c SCRIPT sh <work dir>`: `@ <apk path relative to work>` then that APK's fact lines. */
    public const val SCRIPT: String = """
        set -e
        w="$1"
        j=$(sed -n 's/.*"executable" *: *"\([^"]*\)".*/\1/p' "${'$'}HOME/.ltirom/tools.d/apktool.json" 2>/dev/null)
        [ -f "${'$'}j" ] || { echo "apktool is not installed" >&2; exit 3; }
        c="${'$'}HOME/.ltirom/cache/aapt2-$(sha256sum "${'$'}j" | cut -c1-12)"
        [ -x "${'$'}c/aapt2" ] || { mkdir -p "${'$'}c" && unzip -ojq "${'$'}j" prebuilt/linux/aapt2 -d "${'$'}c" && chmod 755 "${'$'}c/aapt2"; }
        t=$(mktemp -d)
        trap 'rm -rf "${'$'}t"' EXIT
        export A="${'$'}c/aapt2" W="${'$'}w" T="${'$'}t"
        find "${'$'}w" -mindepth 3 -maxdepth 7 -type f -name '*.apk' -print0 | xargs -0 -r -P8 -n1 sh -c '
        o="${'$'}T/$(printf %s "$1" | sha1sum | cut -c1-16)"
        { echo "@ ${'$'}{1#${'$'}W/}"; { "${'$'}A" dump xmltree --file AndroidManifest.xml "$1" 2>/dev/null || echo "!unreadable"; } | awk "
        /^!unreadable/ { print \"unreadable\"; next }
        /^ *E: / { d = index(\$0, \"E:\"); while (sp > 0 && sc[sp] >= d) sp--; sp++; sc[sp] = d; sn[sp] = \$2; next }
        /^ *A: / {
          a = \$0; sub(/^ *A: /, \"\", a); name = a; sub(/=.*/, \"\", name); sub(/\\(0x[0-9a-f]+\\)/, \"\", name); sub(/.*:/, \"\", name)
          v = a; sub(/^[^=]*=/, \"\", v); if (v ~ /^\"/) { v = substr(v, 2); sub(/\".*/, \"\", v) } else sub(/ .*/, \"\", v)
          e = sn[sp]; p = (sp > 1) ? sn[sp - 1] : \"\"; g = (sp > 2) ? sn[sp - 2] : \"\"
          if (e == \"manifest\" && name == \"package\") print \"pkg \" v
          else if (e == \"manifest\" && name == \"sharedUserId\") print \"uid \" v
          else if (e == \"application\" && name == \"persistent\" && (v == \"true\" || v == \"0xffffffff\")) print \"persistent\"
          else if (e == \"uses-library\" && name == \"name\") { lib = v; print \"uses \" v }
          else if (e == \"uses-library\" && name == \"required\" && (v == \"false\" || v == \"0x0\")) print \"optional \" lib
          else if (e == \"library\" && name == \"name\") print \"lib \" v
          else if (e == \"overlay\" && name == \"targetPackage\") print \"overlay \" v
          else if (e == \"manifest\" && name == \"versionName\") print \"ver \" v
          else if (e == \"uses-sdk\" && name == \"targetSdkVersion\") { s = a; sub(/.*\\)/, \"\", s); print \"sdk \" s }
          else if (e == \"package\" && p == \"queries\" && name == \"name\") print \"asks \" v
          else if (name == \"name\" && p == \"intent-filter\") {
            if (e == \"category\" && v == \"android.intent.category.HOME\" && (g == \"activity\" || g == \"activity-alias\")) print \"home\"
            else if (e == \"action\" && v == \"android.view.InputMethod\" && g == \"service\") print \"ime\"
            else if (e == \"action\" && v ~ /^android\\.intent\\.action\\.(LOCKED_)?BOOT_COMPLETED\$/ && g == \"receiver\") print \"boot\"
          }
        }" | sort -u; } > "${'$'}o"
        ' sh
        cat "${'$'}t"/* 2>/dev/null || true
    """

    /**
     * `sh -c LABELS_SCRIPT sh <work dir> <icon dir>`: `@ <apk path>` then `label <name>` and `icon <file>`, the icon
     * copied into the icon dir. Adaptive (XML) icons have no bitmap to copy, so only some apps get one.
     */
    public const val LABELS_SCRIPT: String = """
        set -e
        w="$1"; d="$2"
        j=$(sed -n 's/.*"executable" *: *"\([^"]*\)".*/\1/p' "${'$'}HOME/.ltirom/tools.d/apktool.json" 2>/dev/null)
        [ -f "${'$'}j" ] || { echo "apktool is not installed" >&2; exit 3; }
        c="${'$'}HOME/.ltirom/cache/aapt2-$(sha256sum "${'$'}j" | cut -c1-12)"
        [ -x "${'$'}c/aapt2" ] || { mkdir -p "${'$'}c" && unzip -ojq "${'$'}j" prebuilt/linux/aapt2 -d "${'$'}c" && chmod 755 "${'$'}c/aapt2"; }
        rm -rf "${'$'}d" && mkdir -p "${'$'}d"
        t=$(mktemp -d)
        trap 'rm -rf "${'$'}t"' EXIT
        export A="${'$'}c/aapt2" W="${'$'}w" T="${'$'}t" D="${'$'}d"
        find "${'$'}w" -mindepth 3 -maxdepth 7 -type f -name '*.apk' -print0 | xargs -0 -r -P8 -n1 sh -c '
        h=$(printf %s "$1" | sha1sum | cut -c1-16)
        b=$("${'$'}A" dump badging "$1" 2>/dev/null || true)
        l=$(printf "%s\n" "${'$'}b" | sed -n "s/^application-label:.\(.*\).$/\1/p" | head -n1)
        i=$(printf "%s\n" "${'$'}b" | sed -n "s/^application-icon-[0-9]*:.\(.*\).$/\1/p" | grep -Ei "\.(png|webp)$" | tail -n1)
        if [ -z "${'$'}i" ]; then
          n=$(printf "%s\n" "${'$'}b" | sed -n "s/^application-icon-[0-9]*:.\(.*\).$/\1/p" | tail -n1 | sed "s#.*/##; s#\.[a-z]*\$##")
          [ -n "${'$'}n" ] && i=$(unzip -Z1 "$1" 2>/dev/null | grep -E "^res/(mipmap|drawable)[^/]*/${'$'}n\.(png|webp)$" | sort | tail -n1)
        fi
        {
          echo "@ ${'$'}{1#${'$'}W/}"
          [ -n "${'$'}l" ] && echo "label ${'$'}l"
          if [ -n "${'$'}i" ] && unzip -p "$1" "${'$'}i" > "${'$'}D/${'$'}h.img" 2>/dev/null && [ -s "${'$'}D/${'$'}h.img" ]; then echo "icon ${'$'}h.img"; else rm -f "${'$'}D/${'$'}h.img"; fi
        } > "${'$'}T/${'$'}h"
        ' sh
        cat "${'$'}t"/* 2>/dev/null || true
    """

    /**
     * `sh -c SIGNERS_SCRIPT sh <work dir>`: `@ <apk path>` then `signer <digest> <subject>` for every APK with a
     * signature. A small JDK program (JDK 17 is a Setup requirement) reads the first signer's certificate from the
     * APK Signing Block (v3, else v2), else from the v1 `.RSA`/`.DSA`/`.EC` in META-INF: only the end of each APK
     * is read.
     */
    public const val SIGNERS_SCRIPT: String = """
        set -e
        w="$1"
        t=$(mktemp -d)
        trap 'rm -rf "${'$'}t"' EXIT
        cat > "${'$'}t/Signers.java" <<'JAVA'
import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.cert.*;
import java.util.*;
import java.util.zip.*;

public class Signers {
    static final ByteOrder LE = ByteOrder.LITTLE_ENDIAN;

    public static void main(String[] a) throws Exception {
        Path root = Paths.get(a[0]);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        StringBuilder out = new StringBuilder();
        for (String line; (line = in.readLine()) != null; ) {
            Path p = Paths.get(line);
            X509Certificate c = null;
            try { c = blockCert(p, cf); } catch (Exception e) { }
            if (c == null) try { c = v1Cert(p, cf); } catch (Exception e) { }
            out.append("@ ").append(root.relativize(p)).append('\n');
            if (c != null) {
                byte[] d = MessageDigest.getInstance("SHA-256").digest(c.getEncoded());
                StringBuilder h = new StringBuilder();
                for (int i = 0; i < 8; i++) h.append(String.format("%02x", d[i]));
                out.append("signer ").append(h).append(' ')
                    .append(c.getSubjectX500Principal().getName("RFC2253")).append('\n');
            }
        }
        System.out.print(out);
    }

    static ByteBuffer read(FileChannel ch, long at, int n) throws IOException {
        ByteBuffer b = ByteBuffer.allocate(n).order(LE);
        while (b.hasRemaining()) if (ch.read(b, at + b.position()) < 0) throw new EOFException();
        b.flip();
        return b;
    }

    static ByteBuffer lengthPrefixed(ByteBuffer b) {
        int n = b.getInt();
        ByteBuffer r = b.slice().order(LE);
        r.limit(n);
        b.position(b.position() + n);
        return r;
    }

    static X509Certificate blockCert(Path p, CertificateFactory cf) throws Exception {
        try (FileChannel ch = FileChannel.open(p)) {
            long size = ch.size();
            int tail = (int) Math.min(size, 65557);
            ByteBuffer t = read(ch, size - tail, tail);
            int eocd = -1;
            for (int i = tail - 22; i >= 0; i--) if (t.getInt(i) == 0x06054b50) { eocd = i; break; }
            if (eocd < 0) return null;
            long cd = t.getInt(eocd + 16) & 0xffffffffL;
            if (cd < 32) return null;
            ByteBuffer foot = read(ch, cd - 24, 24);
            if (!"APK Sig Block 42".equals(new String(foot.array(), 8, 16, "US-ASCII"))) return null;
            long blockSize = foot.getLong(0);
            ByteBuffer pairs = read(ch, cd - blockSize, (int) (blockSize - 24));
            ByteBuffer v2 = null, v3 = null;
            while (pairs.remaining() >= 12) {
                long len = pairs.getLong();
                int id = pairs.getInt();
                ByteBuffer v = pairs.slice().order(LE);
                v.limit((int) len - 4);
                pairs.position(pairs.position() + (int) len - 4);
                if (id == 0xf05368c0) v3 = v; else if (id == 0x7109871a) v2 = v;
            }
            ByteBuffer scheme = v3 != null ? v3 : v2;
            if (scheme == null) return null;
            ByteBuffer signedData = lengthPrefixed(lengthPrefixed(lengthPrefixed(scheme)));
            lengthPrefixed(signedData);
            ByteBuffer cert = lengthPrefixed(lengthPrefixed(signedData));
            byte[] der = new byte[cert.remaining()];
            cert.get(der);
            return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(der));
        }
    }

    static X509Certificate v1Cert(Path p, CertificateFactory cf) throws Exception {
        try (ZipFile z = new ZipFile(p.toFile())) {
            for (Enumeration<? extends ZipEntry> e = z.entries(); e.hasMoreElements(); ) {
                ZipEntry en = e.nextElement();
                String n = en.getName().toUpperCase(Locale.ROOT);
                boolean block = n.endsWith(".RSA") || n.endsWith(".DSA") || n.endsWith(".EC");
                if (!n.startsWith("META-INF/") || !block) continue;
                try (InputStream is = z.getInputStream(en)) {
                    Collection<? extends Certificate> cs = cf.generateCertificates(is);
                    if (!cs.isEmpty()) return (X509Certificate) cs.iterator().next();
                }
            }
        }
        return null;
    }
}
JAVA
        find "${'$'}w" -mindepth 3 -maxdepth 7 -type f -name '*.apk' | java "${'$'}t/Signers.java" "${'$'}w"
    """

    /** [SCRIPT]'s, [LABELS_SCRIPT]'s or [SIGNERS_SCRIPT]'s output as facts per APK path (relative to `work/`). */
    public fun parse(output: String): Map<String, AppFacts> {
        val blocks = linkedMapOf<String, MutableList<String>>()
        var current: MutableList<String>? = null
        output.lineSequence().map(String::trim).filter(String::isNotEmpty).forEach { line ->
            if (line.startsWith("@ ")) {
                current = blocks.getOrPut(line.removePrefix("@ ")) { mutableListOf() }
            } else {
                current?.add(line)
            }
        }
        return blocks.mapValues { (_, lines) -> facts(lines) }
    }

    /** One APK's lines (`pkg x`, `persistent`, `uses lib`, ...) as facts. */
    private fun facts(lines: List<String>): AppFacts {
        fun values(key: String) = lines.filter { it.startsWith("$key ") }.map { it.substringAfter(' ') }.toSet()
        return AppFacts(
            packageId = values("pkg").firstOrNull(),
            sharedUserId = values("uid").firstOrNull(),
            persistent = "persistent" in lines,
            home = "home" in lines,
            inputMethod = "ime" in lines,
            bootReceiver = "boot" in lines,
            requiredLibraries = values("uses") - values("optional"),
            providedLibraries = values("lib"),
            overlayTargets = values("overlay"),
            version = values("ver").firstOrNull(),
            // What follows the attribute's `)`: `=31` from aapt2 2.19, a typed `0x22` (hex) from older ones.
            targetSdk = values("sdk").firstOrNull()?.removePrefix("=")?.let { v ->
                if (v.startsWith("0x")) v.removePrefix("0x").toIntOrNull(HEX) else v.toIntOrNull()
            },
            queries = values("asks"),
            label = lines.firstOrNull { it.startsWith("label ") }?.substringAfter(' '),
            icon = values("icon").firstOrNull(),
            signer = lines.firstOrNull { it.startsWith("signer ") }?.substringAfter(' ')?.substringAfter(' '),
            signerDigest = lines.firstOrNull { it.startsWith("signer ") }?.split(' ')?.getOrNull(1),
            unreadable = "unreadable" in lines,
        )
    }
}
