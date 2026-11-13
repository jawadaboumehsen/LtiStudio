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

import org.ide.lti.core.domain.pipeline.text.SidecarEditor
import org.ide.lti.core.domain.pipeline.toolkit.ApexPatchResult
import org.ide.lti.core.domain.pipeline.toolkit.ApkPatchResult
import org.ide.lti.core.domain.pipeline.toolkit.ConfigEditDiff
import org.ide.lti.core.domain.pipeline.toolkit.ConfigKind
import org.ide.lti.core.domain.pipeline.toolkit.ConfigValidators
import org.ide.lti.core.domain.pipeline.toolkit.CoverageGate
import org.ide.lti.core.domain.pipeline.toolkit.CoverageResult
import org.ide.lti.core.domain.pipeline.toolkit.DeodexAppResult
import org.ide.lti.core.domain.pipeline.toolkit.DeodexScope
import org.ide.lti.core.domain.pipeline.toolkit.DeodexStatus
import org.ide.lti.core.domain.pipeline.toolkit.OmcDecode
import org.ide.lti.core.domain.pipeline.toolkit.OmcInfo
import org.ide.lti.core.domain.pipeline.toolkit.Refusal
import org.ide.lti.core.domain.pipeline.toolkit.RefusalException

/**
 * Tools over the assembled `work/` tree (FR-016, FR-028..FR-033). Every change backs up what it replaces first
 * (a restore point) and is checked after it is made.
 */
public class ContentWorkbench(private val runner: WorkbenchRunner) {

    /**
     * `sh -n` of a project script's [content], the check the build runs before any script: the
     * shell's message (`sh: 3: Syntax error: …`), or null when it parses.
     */
    public suspend fun checkScript(workspaceId: String, content: String): String? = runner.read(workspaceId) {
        val result = tryRun("sh", "-c", "printf '%s' \"\$1\" | sh -n", "sh", content)
        if (result.exitCode == 0) null else result.stderr.ifBlank { result.stdout }.trim().ifEmpty { "sh -n failed" }
    }

    /**
     * Checks the packages of [scope] in work/ ([file] for [DeodexScope.FILE]): a package whose dex is inside it is
     * already deodexed, and its precompiled files (oat/, odex, vdex, art) are stale; they are removed with a restore
     * point unless [keepPrecompiled]. A package whose code is only in a VDEX cannot be converted here (that needs
     * vdexExtractor) and is reported with the VDEX version. A [dryRun] changes nothing and says what it would do.
     * Every finding and a summary go to the Output panel.
     */
    public suspend fun deodex(
        workspaceId: String,
        scope: DeodexScope = DeodexScope.APPS,
        file: String? = null,
        dryRun: Boolean = false,
        keepPrecompiled: Boolean = false,
    ): List<DeodexAppResult> = runner.job(workspaceId, if (dryRun) "Deodex (dry run)" else "Deodex") {
        if (scope == DeodexScope.FILE) {
            requireInWork(checkNotNull(file) { "Pick the file to deodex" })
            if (!exists(file)) {
                throw RefusalException(Refusal("There is no such file.", file, "Pick a jar or apk inside work/."))
            }
        }
        val target = file?.removePrefix("work/").orEmpty()
        val results = sh(DEODEX_SCRIPT, abs("work"), scope.name, target).lines().mapNotNull { line ->
            val f = line.split('\t').takeIf { it.size == DEODEX_FIELDS } ?: return@mapNotNull null
            val result = if (f[0] == STALE) {
                staleResult(f[1], f[2], f[STALE_FILES].split(' ').filter(String::isNotEmpty), dryRun, keepPrecompiled)
            } else {
                DeodexAppResult(f[1], DeodexStatus.valueOf(f[0]), f[2])
            }
            report(
                if (result.status == DeodexStatus.Failed) LogLevel.WRN else LogLevel.INF,
                "${result.app}: ${result.reason}",
            )
            result
        }
        val counts = results.groupingBy { it.status }.eachCount()
        report(
            LogLevel.OK,
            "Deodex ${scope.words}: ${results.size} checked, " +
                "${counts[DeodexStatus.AlreadyDone] ?: 0} already deodexed, " +
                "${counts[DeodexStatus.Failed] ?: 0} only in a VDEX, " +
                "${counts[DeodexStatus.NoCompanion] ?: 0} with no code" +
                if (dryRun) " (dry run: nothing was changed)" else "",
        )
        results
    }

    /** A package with its dex inside and precompiled files beside it: removed, backed up, unless kept. */
    private suspend fun WorkbenchRunner.Job.staleResult(
        pkg: String,
        reason: String,
        stale: List<String>,
        dryRun: Boolean,
        keep: Boolean,
    ): DeodexAppResult = DeodexAppResult(
        pkg,
        DeodexStatus.AlreadyDone,
        when {
            keep -> "$reason; kept its ${stale.size} precompiled file(s)"

            dryRun -> "$reason; would remove ${stale.size} stale precompiled file(s): ${stale.joinToString()}"

            else -> {
                // An app's oat/ folder is its own: it goes whole; a framework jar's files sit among the others'.
                val app = !pkg.contains("/framework/")
                val items = if (app) listOf("work/${pkg.substringBeforeLast('/')}/oat") else stale.map { "work/$it" }
                val points = items.map { item -> backup(TOOL, item).also { run("rm", "-rf", "--", abs(item)) } }
                "$reason; removed ${stale.size} stale precompiled file(s) (restore point ${points.joinToString {
                    it.id
                }})"
            }
        },
    )

    /** Decodes [path] (an APK or JAR in the work tree) with apktool into `workbench/apk/`; returns that folder. */
    public suspend fun decode(workspaceId: String, path: String): String = runner.job(workspaceId, "Decode") {
        requireInWork(path)
        val out = decodedDir(path)
        val framework = "work/system/system/framework/framework-res.apk"
        if (exists(framework)) tryRun("apktool", "if", "-p", abs(FRAMEWORK_DIR), abs(framework))
        val result = tryRun("apktool", "d", "-f", "-p", abs(FRAMEWORK_DIR), "-o", abs(out), abs(path))
        if (result.exitCode != 0) {
            throw RefusalException(
                Refusal(
                    cause = "apktool cannot decode this package.",
                    item = path,
                    nextStep = "Check the details: newer dex container versions are not supported by apktool yet.",
                    details = result.stderr.ifBlank { result.stdout }.trim().takeLast(DETAILS),
                ),
            )
        }
        hostPath(out) ?: abs(out)
    }

    /**
     * Rebuilds [path] from its decoded folder, aligns and signs an APK with the workspace platform key, tests the
     * archive, then replaces the original (backed up first) (FR-029).
     */
    public suspend fun rebuild(workspaceId: String, path: String, platformKeyRef: String): ApkPatchResult =
        runner.job(workspaceId, "Rebuild") {
            requireInWork(path)
            val decoded = decodedDir(path)
            if (!exists(decoded)) {
                throw RefusalException(Refusal("The package is not decoded.", path, "Decode it first."))
            }
            val built = "$decoded.built"
            run("apktool", "b", "-f", "-p", abs(FRAMEWORK_DIR), "-o", abs(built), abs(decoded))
            val output = if (path.endsWith(".apk")) {
                run("zipalign", "-p", "-f", "4", abs(built), abs("$built.aligned"))
                run(
                    "signapk",
                    abs("$platformKeyRef.x509.pem"),
                    abs("$platformKeyRef.pk8"),
                    abs("$built.aligned"),
                    abs("$built.signed"),
                )
                "$built.signed"
            } else {
                built
            }
            run("unzip", "-tq", abs(output))
            // Code kept only in oat/vdex (no classes.dex) would be lost: oat is removed below, as it no longer fits.
            val hasDex = tryRun("sh", "-c", "unzip -Z1 \"$1\" | grep -qE '^classes[0-9]*\\.dex$'", "sh", abs(output))
            if (hasDex.exitCode != 0 && tryRun("sh", "-c", "ls \"$(dirname \"$1\")\"/oat/*/*.*dex", "sh", abs(path))
                    .exitCode == 0
            ) {
                throw RefusalException(
                    Refusal(
                        "Its code is only in the oat folder next to it; the rebuilt package has none.",
                        path,
                        "Deodex it first (its dex must be in the package), then rebuild.",
                    ),
                )
            }
            requireResolvedInWork(this, path)
            val point = backup(TOOL, path)
            // A new file of its own next to the original (mktemp never opens an existing name, so no link there is
            // followed), then renamed over it: a cut-off copy never leaves the package half written.
            // mktemp makes it 0600: it takes the original's mode before the rename.
            sh(
                "t=$(mktemp \"$(dirname \"$1\")/.rebuild.XXXXXX\") && " +
                    "{ cp -- \"$2\" \"\$t\" && chmod --reference=\"$1\" \"\$t\" && mv -f -T -- \"\$t\" \"$1\"; } " +
                    "|| { rm -f -- \"\$t\"; exit 1; }",
                abs(path),
                abs(output),
            )
            sh(
                "d=$(dirname \"$1\"); rm -rf \"${'$'}d/oat\"; " +
                    "find \"${'$'}d\" -maxdepth 1 \\( -name '*.prof' -o -name '*.bprof' \\) -delete",
                abs(path),
            )
            ApkPatchResult(file = path, status = "Rebuilt", restorePointId = point.id)
        }

    /**
     * Replaces [findHex] with [replaceHex] in [library] taken from [apex]'s payload, and puts the patched copy at
     * [systemDest] in the work tree with sidecar entries (as a module overlay does). A library that already holds
     * [replaceHex] is reported, not patched twice (FR-030).
     */
    public suspend fun patchApexLibrary(
        workspaceId: String,
        apex: String,
        library: String,
        findHex: String,
        replaceHex: String,
        systemDest: String,
    ): ApexPatchResult = runner.job(workspaceId, "Patch library") {
        val find = findHex.lowercase().filter { !it.isWhitespace() }
        val replace = replaceHex.lowercase().filter { !it.isWhitespace() }
        require(
            find.isNotEmpty() && find.length % 2 == 0 && find.length == replace.length &&
                find.all { it in HEX_DIGITS || it == '.' },
        ) {
            "The find and replace hex must be whole bytes (an even number of digits) of the same length " +
                "(\".\" matches any digit)."
        }
        require(replace.all { it in HEX_DIGITS }) { "The replace hex must be plain hex digits." }
        requireInWork(apex)
        requireInWork(systemDest)
        require(!library.startsWith("/") && ".." !in library.split('/')) { "The library is a path inside the APEX." }
        val tmp = "workbench/apex/${apex.substringAfterLast('/')}"
        sh("rm -rf \"$1\" && mkdir -p \"$1\"", abs(tmp))
        run("unzip", "-o", abs(apex), "apex_payload.img", "-d", abs(tmp))
        val extract = tryRun("7zz", "x", "-y", "-o${abs("$tmp/payload")}", abs("$tmp/apex_payload.img"), library)
        if (extract.exitCode != 0) {
            run("fsck.erofs", "--extract=${abs("$tmp/payload")}", abs("$tmp/apex_payload.img"))
        }
        val lib = "$tmp/payload/$library"
        if (!exists(lib) || !folderResolvesInside(lib, "$tmp/payload")) {
            throw RefusalException(Refusal("The library is not in this APEX.", library, "Check the library path."))
        }
        val state = sh(HEXPATCH_SCRIPT, abs(lib), find, replace, abs("$lib.patched")).trim()
        if (state == "notfound") {
            throw RefusalException(
                Refusal(
                    "The find hex is not in the library.",
                    library,
                    "Use the pattern for this Android version (a library patched before no longer holds it).",
                ),
            )
        }
        // A new destination gets a point too: restoring it removes the added library.
        requireResolvedInWork(this, systemDest)
        val point = backup(TOOL, systemDest)
        // Moved in whole from the same disk: a stopped job leaves the old library, never part of a new one.
        sh(
            "mkdir -p \"$(dirname \"$2\")\" && { [ ! -f \"$2\" ] || chmod --reference=\"$2\" \"$1\"; } && " +
                "mv -f -T \"$1\" \"$2\"",
            abs("$lib.patched"),
            abs(systemDest),
        )
        addSidecars(this, systemDest)
        ApexPatchResult(
            apex,
            alreadyChanged = false,
            success = true,
            message = "Patched $library into $systemDest.",
            restorePointId = point.id,
        )
    }

    /** The text of a configuration file in the work tree (FR-031). */
    public suspend fun readConfig(workspaceId: String, path: String): String = runner.read(workspaceId) {
        requireInWork(path)
        readText(path) ?: throw RefusalException(Refusal("The file does not exist.", path, "Pick another file."))
    }

    /** Validates [text] for [kind] and saves it over [path], backing the old text up first (FR-031). */
    public suspend fun saveConfig(workspaceId: String, kind: ConfigKind, path: String, text: String): ConfigEditDiff =
        runner.job(workspaceId, "Edit ${path.substringAfterLast('/')}") {
            requireInWork(path)
            val errors = when (kind) {
                ConfigKind.FSTAB -> ConfigValidators.validateFstab(text)
                ConfigKind.BUILD_PROP -> ConfigValidators.validateBuildProp(text)
                ConfigKind.FS_CONFIG -> ConfigValidators.validateFsConfig(text)
                ConfigKind.FILE_CONTEXTS -> ConfigValidators.validateFileContexts(text)
            }
            if (errors.isNotEmpty()) {
                throw RefusalException(
                    Refusal(errors.first(), path, "Correct the line and save again.", errors.joinToString("\n")),
                )
            }
            val old = readText(path).orEmpty()
            requireResolvedInWork(this, path)
            val point = backup(TOOL, path)
            writeText(path, text)
            ConfigEditDiff(old, text, lineChanges(old, text), point.id)
        }

    /** Samsung region/carrier data of the work tree in readable form (FR-032); encrypted CSC files are refused. */
    public suspend fun samsungCsc(workspaceId: String): OmcInfo = runner.read(workspaceId) {
        suspend fun find(name: String) = run(
            "find",
            abs("work"),
            "-name",
            name,
            "-type",
            "f",
            "-print",
            "-quit",
        ).trim().ifEmpty { null }
        val salesCode = find("sales_code.dat")
        val customer = find("customer.xml")
        val feature = find("cscfeature.xml")
        if (salesCode == null && customer == null && feature == null) {
            throw RefusalException(
                Refusal("No Samsung CSC data in the work tree.", "work/", "This is for Samsung firmware."),
            )
        }
        val texts = listOf(salesCode, customer, feature).map { p -> p?.let { readText(it.removePrefix("$root/")) } }
        if (texts.drop(1).any { it != null && !it.trimStart().startsWith("<") }) {
            throw RefusalException(
                Refusal(
                    "The CSC files are encrypted.",
                    "cscfeature.xml / customer.xml",
                    "Only plain-text CSC can be read.",
                ),
            )
        }
        OmcDecode.decode(texts[0], texts[1], texts[2])
    }

    /** Files of [partitions] in the work tree with no fs_config or file_contexts entry (FR-015). */
    public suspend fun coverage(workspaceId: String, partitions: List<String>): CoverageResult = runner.read(
        workspaceId,
    ) {
        partitions.filter { exists("work/$it") }.map { p ->
            val paths = run(
                "find",
                abs("work/$p"),
                "-mindepth",
                "1",
                "-printf",
                "%P\\n",
            ).lines().filter { it.isNotBlank() }
            CoverageGate.check(
                p,
                paths,
                readText("work/configs/fs_config-$p").orEmpty(),
                readText("work/configs/file_context-$p").orEmpty(),
            )
        }.fold(CoverageResult()) { all, r ->
            CoverageResult(
                all.uncoveredFsConfig + r.uncoveredFsConfig,
                all.uncoveredContexts + r.uncoveredContexts,
                all.unbuildableNames + r.unbuildableNames,
            )
        }
    }

    private suspend fun addSidecars(job: WorkbenchRunner.Job, workPath: String) = with(job) {
        val rel = workPath.removePrefix("work/")
        val partition = rel.substringBefore('/')
        val path = rel.substringAfter('/')
        val fsName = "work/configs/fs_config-$partition"
        val ctxName = "work/configs/file_context-$partition"
        // Labelled as Android's build would: the ROM's own rules, then its folder.
        val platform = platformMetadata()
        readText(fsName)?.let {
            writeText(fsName, SidecarEditor.withFsConfig(it, partition, path, isDirectory = false, platform = platform))
        }
        readText(ctxName)?.let {
            writeText(ctxName, SidecarEditor.withFileContext(it, partition, path, platform = platform))
        }
    }

    private fun requireInWork(path: String) {
        require(path.startsWith("work/") && ".." !in path.split('/')) { "Pick a file inside the work tree (work/…)." }
    }

    /** Before a write: [path] with every link resolved (a link in work/ to a file outside) must stay in work/. */
    private suspend fun requireResolvedInWork(job: WorkbenchRunner.Job, path: String) {
        val inside = job.tryRun(
            "sh",
            "-c",
            "r=$(realpath -m -- \"$1\") && w=$(realpath -- \"$2\") && case \"\$r\" in \"\$w\"/*) ;; *) exit 1;; esac",
            "sh",
            job.abs(path),
            job.abs("work"),
        )
        if (inside.exitCode != 0) {
            throw RefusalException(
                Refusal("It is a link to a file outside the work tree.", path, "Pick the file the link points to."),
            )
        }
    }

    /** `a/b_c.apk` and `a_b/c.apk` both flatten to `a_b_c.apk`: the path's hash keeps their folders apart. */
    private fun decodedDir(path: String): String {
        val rel = path.removePrefix("work/")
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(rel.encodeToByteArray())
            .take(DECODED_HASH_BYTES).joinToString("") { "%02x".format(it) }
        return "workbench/apk/${rel.substringAfterLast('/')}-$hash"
    }

    public companion object {
        public const val TOOL: String = "content"
        private const val FRAMEWORK_DIR = "workbench/apk/framework"
        private const val DETAILS = 4_000
        private const val HEX_DIGITS = "0123456789abcdef"
        private const val DECODED_HASH_BYTES = 6
        private const val STALE = "Stale"

        /**
         * `$1` work/, `$2` the scope, `$3` the file for FILE. One line per package: status, its path under work/,
         * reason, and its precompiled files (oat/<arch>/<name>.odex|vdex|art, and a framework jar's boot image
         * files) space-separated. A VDEX's version is its bytes 4 to 7.
         */
        private const val DEODEX_SCRIPT = """
cd "$1" || exit 1
case "$2" in
  SERVICES) find . -type f -path '*/framework/services.jar' ;;
  FRAMEWORK) find . -type f -path '*/framework/*' ! -path '*/framework/*/*' \( -name '*.jar' -o -name '*.apk' \) ;;
  APPS) find . -mindepth 3 -maxdepth 5 -type f -name '*.apk' -path '*app/*' ;;
  FILE) echo "./$3" ;;
esac | sort | while read -r f; do
  f=${'$'}{f#./}; dir=${'$'}{f%/*}; base=${'$'}{f##*/}; name=${'$'}{base%.*}
  pre=${'$'}(ls -d "${'$'}dir"/oat/*/"${'$'}name".odex "${'$'}dir"/oat/*/"${'$'}name".vdex "${'$'}dir"/oat/*/"${'$'}name".art "${'$'}dir"/boot-"${'$'}name".vdex "${'$'}dir"/*/boot-"${'$'}name".vdex 2>/dev/null | tr '\n' ' ')
  vdex=${'$'}(ls "${'$'}dir"/oat/*/"${'$'}name".vdex "${'$'}dir"/boot-"${'$'}name".vdex "${'$'}dir"/*/boot-"${'$'}name".vdex 2>/dev/null | head -n1)
  if unzip -l "${'$'}f" 2>/dev/null | grep -q ' classes[0-9]*\.dex${'$'}'; then
    if [ -n "${'$'}pre" ]; then printf 'Stale\t%s\tdex is in the package\t%s\n' "${'$'}f" "${'$'}pre"
    else printf 'AlreadyDone\t%s\tdex is in the package\t\n' "${'$'}f"; fi
  elif [ -n "${'$'}vdex" ]; then
    v=${'$'}(head -c 8 "${'$'}vdex" | tail -c 4 | tr -d '\000')
    printf 'Failed\t%s\tthe code is only in %s (VDEX %s); extracting it needs vdexExtractor, which is not installed\t\n' "${'$'}f" "${'$'}vdex" "${'$'}v"
  else
    printf 'NoCompanion\t%s\tno dex in the package and no VDEX beside it (resources only)\t\n' "${'$'}f"
  fi
done"""
        private const val DEODEX_FIELDS = 4
        private const val STALE_FILES = 3

        /**
         * `$1` library, `$2` find hex (`.` = any digit), `$3` replace hex, `$4` output; the match must start on a
         * byte. Prints notfound or patched: without the pattern there is no location to prove an earlier patch at
         * (replacement bytes elsewhere prove nothing), so it is never reported as already patched.
         */
        private const val HEXPATCH_SCRIPT = """
hex=${'$'}(xxd -p "$1" | tr -d '\n')
# A space after each byte: a match can only start on a byte, and one at an odd digit cannot hide a later true one.
m=${'$'}(printf '%s' "${'$'}hex" | sed 's/../& /g' | grep -ob "${'$'}(printf '%s' "$2" | sed 's/../& /g')" | head -n1)
if [ -z "${'$'}m" ]; then
  echo notfound
  exit 0
fi
printf '%s' "${'$'}hex" | awk -v o="${'$'}((${'$'}{m%%:*} / 3 * 2))" -v r="$3" '{ printf "%s%s%s", substr(${'$'}0, 1, o), r, substr(${'$'}0, o + length(r) + 1) }' | xxd -r -p > "$4"
echo patched"""

        /** Changed lines at the same position, as `L<n>: - old | + new`. */
        internal fun lineChanges(old: String, new: String): List<String> {
            val a = old.lines()
            val b = new.lines()
            return (0 until maxOf(a.size, b.size)).mapNotNull { i ->
                val was = a.getOrNull(i)
                val now = b.getOrNull(i)
                when {
                    was == now -> null
                    was == null -> "L${i + 1}: + $now"
                    now == null -> "L${i + 1}: - $was"
                    else -> "L${i + 1}: - $was | + $now"
                }
            }
        }
    }
}
