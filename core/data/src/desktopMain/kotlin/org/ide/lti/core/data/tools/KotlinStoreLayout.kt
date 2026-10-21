/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.tools

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.ide.lti.core.domain.setup.RecipeConfig
import org.ide.lti.core.domain.setup.ToolGroup
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.setup.ToolOutput
import org.ide.lti.core.domain.setup.ToolOutputKind
import org.ide.lti.core.domain.setup.ToolProbe
import org.ide.lti.core.domain.setup.ToolSource
import org.ide.lti.core.domain.tools.InstalledOutput
import org.ide.lti.core.domain.tools.InstalledVersion
import org.ide.lti.core.domain.tools.RecipeSummary
import org.ide.lti.core.domain.tools.ResolvedSource
import org.ide.lti.core.domain.tools.ToolError
import org.ide.lti.core.model.setup.ToolRef

@Suppress("TooManyFunctions")
public object KotlinStoreLayout {
    private val SANITIZE_REGEX = Regex("""[^a-zA-Z0-9._-]+""")
    private val VALID_ID_REGEX = Regex("^[a-zA-Z0-9._-]+$")
    private val RESERVED_NAMES = setOf(".", "..", "current", "previous")

    public fun sanitizeId(raw: String): String {
        var sanitized = raw.replace(SANITIZE_REGEX, "_").trim('_', '.')
        while (sanitized.contains("..")) {
            sanitized = sanitized.replace("..", "_")
        }
        return when {
            sanitized.isBlank() || sanitized == "." || sanitized == ".." -> "ver"
            sanitized == "current" || sanitized == "previous" -> "${sanitized}_ver"
            else -> sanitized
        }
    }

    public fun validateVersionId(versionId: String) {
        if (versionId.isBlank() ||
            versionId in RESERVED_NAMES ||
            versionId.contains("..") ||
            !VALID_ID_REGEX.matches(versionId)
        ) {
            val err = ToolError.UnsupportedLayout(listOf("Invalid or reserved version ID: $versionId"))
            throw ToolErrorException(err)
        }
    }

    public fun validateGroupId(group: ToolGroupId) {
        if (group.value.isBlank() ||
            group.value in RESERVED_NAMES ||
            group.value.contains("..") ||
            !VALID_ID_REGEX.matches(group.value)
        ) {
            val err = ToolError.UnsupportedLayout(listOf("Invalid group ID: ${group.value}"))
            throw ToolErrorException(err)
        }
    }

    public fun validateOpId(opId: String) {
        if (opId.isBlank() ||
            opId in RESERVED_NAMES ||
            opId.contains("..") ||
            !VALID_ID_REGEX.matches(opId)
        ) {
            val err = ToolError.UnsupportedLayout(listOf("Invalid op ID: $opId"))
            throw ToolErrorException(err)
        }
    }

    public fun validateRelativePath(path: String) {
        if (path.isBlank() || path.startsWith("/") || path.startsWith("\\") || path.contains("..")) {
            val err = ToolError.UnsupportedLayout(listOf("Invalid or escaping relative path: $path"))
            throw ToolErrorException(err)
        }
    }

    public fun shellQuote(arg: String): String = "'" + arg.replace("'", "'\\''") + "'"

    public fun computeVersionId(source: ResolvedSource, @Suppress("UnusedParameter") label: String = ""): String =
        when (source) {
            is ResolvedSource.Git -> {
                when (val ref = source.ref) {
                    is ToolRef.Tag -> sanitizeId(ref.name)
                    is ToolRef.Branch -> {
                        val shortCommit = source.commit.take(7)
                        sanitizeId("${ref.name}-$shortCommit")
                    }
                    is ToolRef.Commit -> sanitizeId(source.commit.take(7))
                    is ToolRef.ReleaseVersion -> sanitizeId(ref.version)
                }
            }
            is ResolvedSource.Release -> sanitizeId(source.version)
        }

    public fun ResolvedSource.toData(): SourceData = when (this) {
        is ResolvedSource.Git -> {
            val refPayload =
                when (val r = ref) {
                    is ToolRef.Tag -> RefPayload("tag", r.name)
                    is ToolRef.Branch -> RefPayload("branch", r.name)
                    is ToolRef.Commit -> RefPayload("commit", r.sha)
                    is ToolRef.ReleaseVersion -> RefPayload("release", r.version)
                }
            SourceData(
                kind = "git",
                repo = repoUrl,
                commit = commit,
                submodules = submodules,
                ref = refPayload,
            )
        }
        is ResolvedSource.Release ->
            SourceData(
                kind = "release",
                version = version,
                url = url,
                sha256 = sha256,
                member = member,
            )
    }

    /**
     * A short name part for the code a version was built from, derived from the same facts [matches] compares
     * (repository, commit, submodules - not the ref name). Two forks at one commit get different parts; the same
     * source always gets the same one.
     */
    public fun disambiguator(source: ResolvedSource): String {
        val data = source.toData()
        val identity =
            listOf(
                data.kind,
                data.repo,
                data.commit,
                data.submodules.toSortedMap().entries.joinToString(","),
                data.version,
                data.url,
                data.sha256,
                data.member,
            ).joinToString("\u0000") { it?.toString().orEmpty() }
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
        val short = (data.commit ?: data.sha256).orEmpty().take(SHORT_ID_LENGTH)
        return short + "-" + digest.take(DIGEST_BYTES).joinToString("") { "%02x".format(it) }
    }

    private const val SHORT_ID_LENGTH = 7
    private const val DIGEST_BYTES = 4

    public fun SourceData.matches(other: SourceData): Boolean {
        if (kind != other.kind) return false
        return when (kind) {
            "git" ->
                repo == other.repo && commit == other.commit && submodules == other.submodules &&
                    (ref == null || other.ref == null || ref == other.ref)
            "release" ->
                version == other.version && url == other.url && sha256 == other.sha256 &&
                    member == other.member
            else -> this == other
        }
    }

    public fun VersionData.toDomain(groupId: ToolGroupId, catGroup: ToolGroup? = null): InstalledVersion {
        val src: ResolvedSource =
            if (source.kind == "release") {
                ResolvedSource.Release(
                    version = source.version.orEmpty(),
                    url = source.url.orEmpty(),
                    sha256 = source.sha256.orEmpty(),
                    member = source.member.orEmpty(),
                )
            } else {
                val catGit = catGroup?.source as? ToolSource.Git
                val ref =
                    when {
                        source.ref != null ->
                            when (source.ref.type) {
                                "tag" -> ToolRef.Tag(source.ref.name)
                                "branch" -> ToolRef.Branch(source.ref.name)
                                "commit" -> ToolRef.Commit(source.ref.name)
                                else -> ToolRef.Commit(source.commit.orEmpty())
                            }
                        catGit != null && catGit.pinnedCommit == source.commit -> catGit.pinnedRef
                        label.isNotBlank() && id == sanitizeId(label) -> ToolRef.Tag(label)
                        label.isNotBlank() && label.contains("-") -> ToolRef.Branch(label.substringBeforeLast("-"))
                        id.contains("-") -> ToolRef.Branch(id.substringBeforeLast("-"))
                        else -> ToolRef.Commit(source.commit.orEmpty())
                    }
                ResolvedSource.Git(
                    repoUrl = source.repo.orEmpty(),
                    ref = ref,
                    commit = source.commit.orEmpty(),
                    submodules = source.submodules,
                )
            }
        return InstalledVersion(
            group = groupId,
            id = id,
            label = label,
            source = src,
            recipe = RecipeSummary(recipe.type, recipe.revision),
            outputs = outputs.map { InstalledOutput(it.file, it.size, it.mode) },
            builtAt = builtAt,
            intact = intact,
        )
    }

    public fun RecipeConfig.typeName(): String = when (this) {
        is RecipeConfig.AndroidTools -> "android_tools"
        is RecipeConfig.CMake -> "cmake"
        is RecipeConfig.GradleJar -> "gradle_jar"
        is RecipeConfig.ScriptCopy -> "script_copy"
        is RecipeConfig.Release -> "release"
    }

    public fun buildPublishScript(
        groupDir: String,
        targetDir: String,
        outDir: String,
        workDir: String? = null,
    ): String = buildString {
        val qGroup = shellQuote(groupDir)
        val qTarget = shellQuote(targetDir)
        val qOut = shellQuote(outDir)
        appendLine("set -e")
        appendLine("mkdir -p $qGroup")
        if (workDir != null) {
            val qOld = shellQuote("$workDir/old")
            val targetTmp = "$targetDir.tmp"
            val qTargetTmp = shellQuote(targetTmp)
            appendLine("ver_name=\$(basename $qTarget)")
            appendLine("is_current=0")
            appendLine("cur_link=\$(readlink $qGroup/current 2>/dev/null || echo '')")
            appendLine(
                "if [ \"\$cur_link\" = \"\$ver_name\" ] || [ \"\$cur_link\" = $qTarget ] || [ \"\$(realpath " +
                    "$qGroup/current 2>/dev/null)\" = \"\$(realpath $qTarget 2>/dev/null)\" ]; then",
            )
            appendLine("  is_current=1")
            appendLine("fi")
            appendLine(
                "trap 'status=\$?; if [ \$status -ne 0 ]; then if [ ! -d $qTarget ] && [ -d $qOld ]; then mv -T " +
                    "$qOld $qTarget 2>/dev/null || true; fi; if [ -d $qTarget ] && " +
                    "[ \"\$is_current\" = \"1\" ]; then " +
                    "ln -sfn \"\$ver_name\" $qGroup/current.tmp && mv -T $qGroup/current.tmp $qGroup/current " +
                    "2>/dev/null || true; fi; fi' EXIT",
            )
            appendLine("mv -T $qOut $qTargetTmp")
            appendLine("if [ \"\$is_current\" = \"1\" ]; then")
            appendLine("  ln -sfn \"\$(basename $qTargetTmp)\" $qGroup/current.tmp")
            appendLine("  mv -T $qGroup/current.tmp $qGroup/current")
            appendLine("fi")
            appendLine("if [ -e $qTarget ] || [ -L $qTarget ]; then")
            appendLine("  mv -T $qTarget $qOld")
            appendLine("fi")
            appendLine("mv -T $qTargetTmp $qTarget")
            appendLine("if [ \"\$is_current\" = \"1\" ]; then")
            appendLine("  ln -sfn \"\$ver_name\" $qGroup/current.tmp")
            appendLine("  mv -T $qGroup/current.tmp $qGroup/current")
            appendLine("fi")
            appendLine("trap - EXIT")
        } else {
            appendLine("if [ -e $qTarget ] || [ -L $qTarget ]; then exit 3; fi")
            // -n prevents replacing an immutable version if another publication wins the race.
            appendLine("mv -T -n $qOut $qTarget")
            appendLine("if [ -e $qOut ]; then exit 3; fi")
        }
    }

    public fun buildSwitchScript(
        qTargetVerDir: String,
        groupDir: String,
        qVersionId: String,
        checkOuts: String,
        groupDef: ToolGroup?,
        toolsDDir: String = "~/.ltirom/tools.d",
    ): String = buildString {
        val qGroupDir = shellQuote(groupDir)
        appendLine("set -e")
        appendLine("if [ ! -d $qTargetVerDir ] || [ -L $qTargetVerDir ]; then exit 1; fi")
        appendLine("real=\$(realpath -m $qTargetVerDir 2>/dev/null || echo '')")
        appendLine("case \"\$real\" in")
        appendLine("  $qGroupDir/*) ;;")
        appendLine("  *) exit 3 ;;")
        appendLine("esac")
        appendLine("if ! ($checkOuts); then exit 4; fi")
        appendLine("mkdir -p $toolsDDir")
        if (groupDef != null) {
            for (out in groupDef.outputs) {
                val toolPath = "$groupDir/current/${out.file}"
                val manifestJson =
                    buildJsonObject {
                        put("name", out.toolId)
                        put("executable", toolPath)
                        put("owner", "ltirom-tools")
                    }.toString()
                val qOutJson = shellQuote(out.toolId + ".json")
                appendLine("cat << 'EOF' > $toolsDDir/$qOutJson.tmp.\$\$")
                appendLine(manifestJson)
                appendLine("EOF")
            }
        }
        // The link is the one thing that makes a version active, and the manifests point through it. If anything
        // after the swap fails, the previous target is put back: a failed switch leaves the old version active.
        appendLine("prev=\$(readlink $qGroupDir/current 2>/dev/null || echo '')")
        appendLine("swapped=")
        appendLine(
            "restore() { status=\$?; if [ \$status -ne 0 ] && [ -n \"\$swapped\" ]; then " +
                "if [ -n \"\$prev\" ]; then ln -sfn \"\$prev\" $qGroupDir/current.tmp && " +
                "mv -T $qGroupDir/current.tmp $qGroupDir/current; else rm -f $qGroupDir/current; fi; fi; " +
                // Only this run's own staging files: other groups switch concurrently under their own locks.
                "rm -f $toolsDDir/*.tmp.\$\$ 2>/dev/null; exit \$status; }",
        )
        appendLine("trap restore EXIT")
        appendLine("ln -sfn $qVersionId $qGroupDir/current.tmp")
        appendLine("mv -T $qGroupDir/current.tmp $qGroupDir/current")
        appendLine("swapped=1")
        if (groupDef != null) {
            for (out in groupDef.outputs) {
                val qOutJson = shellQuote(out.toolId + ".json")
                appendLine("mv $toolsDDir/$qOutJson.tmp.\$\$ $toolsDDir/$qOutJson")
                appendLine("if [ \"\$HOME/.ltirom/tools.d\" != \"\$(realpath -m $toolsDDir 2>/dev/null)\" ]; then")
                appendLine("  mkdir -p \"\$HOME/.ltirom/tools.d\" 2>/dev/null || true")
                appendLine("  cp $toolsDDir/$qOutJson \$HOME/.ltirom/tools.d/$qOutJson 2>/dev/null || true")
                appendLine("fi")
            }
        }
    }

    public fun buildRemoveScript(qGroup: String, qTarget: String, qVersion: String): String = buildString {
        appendLine("curr=\$(readlink $qGroup/current 2>/dev/null || echo '')")
        appendLine("if [ \"\$curr\" = $qVersion ]; then exit 2; fi")
        appendLine("if [ -L $qTarget ]; then exit 3; fi")
        appendLine("real=\$(realpath -m $qTarget 2>/dev/null || echo '')")
        appendLine("case \"\$real\" in")
        appendLine("  $qGroup/*)")
        appendLine("    if [ -d $qTarget ]; then")
        appendLine("      rm -rf $qTarget")
        appendLine("    fi")
        appendLine("    ;;")
        appendLine("  *) exit 3 ;;")
        appendLine("esac")
    }

    public fun buildContainmentScript(qTarget: String, qGroup: String): String = buildString {
        appendLine("if [ ! -d $qTarget ] || [ -L $qTarget ]; then exit 1; fi")
        appendLine("real=\$(realpath -m $qTarget 2>/dev/null || echo '')")
        appendLine("case \"\$real\" in $qGroup/*) ;; *) exit 2 ;; esac")
    }

    public fun buildResultScript(base: String, opId: String, jsonPayload: String): String = buildString {
        appendLine("set -e")
        appendLine("mkdir -p " + shellQuote("$base/results"))
        val qTmp = shellQuote("$base/results/$opId.json.tmp")
        val qDest = shellQuote("$base/results/$opId.json")
        appendLine("cat << 'EOF' > $qTmp")
        appendLine(jsonPayload)
        appendLine("EOF")
        appendLine("mv $qTmp $qDest")
    }

    /**
     * Where the mirror of [repoUrl] lives. One folder per repository: a fork shares its upstream's history, and
     * in one shared mirror a tag or commit that exists only upstream would resolve in the fork too.
     */
    public fun mirrorPath(base: String, group: String, repoUrl: String): String =
        "$base/src/$group-${repoIdentity(repoUrl)}"

    /** Submodule clones are cached per repository too: a fork may point its submodules somewhere else. */
    public fun submoduleCachePath(base: String, group: String, repoUrl: String): String =
        "${mirrorPath(base, group, repoUrl)}-modules"

    private fun repoIdentity(repoUrl: String): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(repoUrl.trim().toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
        .take(REPO_IDENTITY_CHARS)

    private const val REPO_IDENTITY_CHARS = 12

    /**
     * Brings the group's mirror up to date. The mirror is blobless (commits and trees only): a checkout fetches
     * just the files of the commit it needs, so a repo with a long history (apktool) is not downloaded whole.
     * [lenientFetch] lets an offline machine still build a commit the mirror already has.
     */
    public fun buildMirrorScript(base: String, group: String, repoUrl: String, lenientFetch: Boolean): String {
        val qMirror = shellQuote(mirrorPath(base, group, repoUrl))
        val qRepo = shellQuote(repoUrl)
        val refspecs = "'+refs/heads/*:refs/heads/*' '+refs/tags/*:refs/tags/*'"
        return buildString {
            appendLine("set -e")
            // A stalled transfer (a dropped WSL network link) otherwise hangs until the step's hour is up;
            // this fails it after 2 min below 1 KB/s so the retry resumes instead. Inherited by submodules.
            appendLine("export GIT_HTTP_LOW_SPEED_LIMIT=1000 GIT_HTTP_LOW_SPEED_TIME=120")
            appendLine("mkdir -p " + shellQuote("$base/src"))
            appendLine("if [ ! -d $qMirror ]; then")
            appendLine("  git clone --mirror --filter=blob:none $qRepo $qMirror")
            appendLine("fi")
            appendLine("git --git-dir=$qMirror remote set-url origin $qRepo")
            appendLine(
                "git --git-dir=$qMirror fetch origin $refspecs" + if (lenientFetch) " 2>/dev/null || true" else "",
            )
        }
    }

    public fun buildGitPrepScript(
        base: String,
        group: String,
        repoUrl: String,
        commit: String,
        srcDir: String,
        submodules: Map<String, String> = emptyMap(),
    ): String {
        val qMirror = shellQuote(mirrorPath(base, group, repoUrl))
        val qCommit = shellQuote(commit)
        val qSrcDir = shellQuote(srcDir)
        return buildString {
            append(buildMirrorScript(base, group, repoUrl, lenientFetch = true))
            // A worktree of the mirror: missing files are fetched into the mirror, so later versions reuse them.
            // rm before prune: a retry's old checkout must be unregistered, or `worktree add` refuses the path.
            appendLine("rm -rf $qSrcDir")
            appendLine("git --git-dir=$qMirror worktree prune")
            appendLine("git --git-dir=$qMirror -c advice.detachedHead=false worktree add --detach $qSrcDir $qCommit")
            appendLine("cd $qSrcDir")
            appendLine("if [ -f .gitmodules ]; then")
            // One submodule at a time, each copied to the cache as soon as it is complete: a run that is
            // killed part way (slow link, time limit) resumes from what it already has.
            appendLine("  cache=" + shellQuote(submoduleCachePath(base, group, repoUrl)))
            appendLine("  mods=\"\$(git rev-parse --absolute-git-dir)/modules\"")
            appendLine(
                "  git config -f .gitmodules --get-regexp '^submodule\\..*\\.path\$' | while read -r key path; do",
            )
            appendLine("    name=\${key#submodule.}; name=\${name%.path}")
            appendLine("    if [ -d \"\$cache/\$name\" ] && [ ! -d \"\$mods/\$name\" ]; then")
            appendLine("      mkdir -p \"\$(dirname \"\$mods/\$name\")\"; cp -a \"\$cache/\$name\" \"\$mods/\$name\"")
            appendLine("    fi")
            // sync: a cached submodule keeps the URL it was cloned from until told what .gitmodules says now.
            appendLine("    git submodule init -- \"\$path\"")
            appendLine("    git submodule sync -- \"\$path\"")
            appendLine("    git submodule update --depth 1 -- \"\$path\"")
            appendLine("    mkdir -p \"\$(dirname \"\$cache/\$name\")\"; rm -rf \"\$cache/\$name.tmp\"")
            appendLine("    cp -a \"\$mods/\$name\" \"\$cache/\$name.tmp\"; rm -rf \"\$cache/\$name\"")
            appendLine("    mv \"\$cache/\$name.tmp\" \"\$cache/\$name\"")
            appendLine("  done")
            appendLine("  git submodule sync --recursive")
            appendLine("  git submodule update --init --recursive --depth 1")
            appendLine("fi")
            for ((subPath, expectedSha) in submodules) {
                val qSub = shellQuote(subPath)
                val qExpected = shellQuote(expectedSha)
                // A reviewed submodule that was not checked out fails the build; skipping it would let the
                // build go on with the very input that was meant to be verified missing.
                appendLine("if [ ! -d $qSub ]; then")
                appendLine("  echo \"Submodule $subPath was not checked out\" >&2")
                appendLine("  exit 5")
                appendLine("fi")
                appendLine("actualSha=\$(git -C $qSub rev-parse HEAD)")
                appendLine("if [ \"\$actualSha\" != $qExpected ]; then")
                appendLine("  echo \"Submodule $subPath checkout mismatch: expected $qExpected got \$actualSha\" >&2")
                appendLine("  exit 5")
                appendLine("fi")
            }
        }
    }

    /**
     * Succeeds when [pkg] is a command on the PATH or a package dpkg considers *installed* (any want flag, `hold`
     * and `install` alike). `dpkg -s` alone also succeeds for a package that was removed but keeps its
     * configuration, or whose install did not finish.
     */
    public fun buildPackageCheck(pkg: String): String {
        val q = shellQuote(pkg)
        return "command -v $q >/dev/null 2>&1 || " +
            "dpkg-query -W -f='\${Status}' $q 2>/dev/null | grep -Eq ' ok installed\$'"
    }

    public fun buildPrepWorkspaceScript(
        baseDir: String,
        group: String,
        opId: String,
        workDir: String,
        srcDir: String,
        outDir: String,
        opJsonPayload: String,
    ): String = buildString {
        val qWork = shellQuote("$baseDir/work")
        val qOpId = shellQuote(opId)
        val qGroup = shellQuote(group)
        val qWorkDir = shellQuote(workDir)
        val qSrcDir = shellQuote(srcDir)
        val qOutDir = shellQuote(outDir)
        appendLine("set -e")
        appendLine("if [ -d $qWork ]; then")
        appendLine("  for d in $qWork/*; do")
        appendLine("    if [ -d \"\$d\" ] && [ \"\$(basename \"\$d\")\" != $qOpId ]; then")
        appendLine("      if [ -f \"\$d/op.json\" ]; then")
        appendLine(
            "        grp=\$(grep -o '\"group\":\"[^\"]*\"' \"\$d/op.json\" 2>/dev/null | cut -d'\"' -f4 || echo '')",
        )
        appendLine("        if [ \"\$grp\" = $qGroup ]; then")
        appendLine("          rm -rf \"\$d\"")
        appendLine("        fi")
        appendLine("      fi")
        appendLine("    fi")
        appendLine("  done")
        appendLine("fi")
        appendLine("rm -rf $qWorkDir")
        appendLine("mkdir -p $qWorkDir $qSrcDir $qOutDir")
        appendLine("cat << 'EOF' > " + shellQuote("$workDir/op.json.tmp"))
        appendLine(opJsonPayload)
        appendLine("EOF")
        appendLine("mv " + shellQuote("$workDir/op.json.tmp") + " " + shellQuote("$workDir/op.json"))
    }

    public fun buildInspectScript(qGroupDir: String): String = buildString {
        appendLine("if [ ! -d $qGroupDir ]; then echo 'ABSENT'; exit 0; fi")
        appendLine("curr=\$(readlink $qGroupDir/current 2>/dev/null || echo '')")
        appendLine("echo \"CURRENT=\$curr\"")
        appendLine("for d in $qGroupDir/*; do")
        appendLine("  if [ -d \"\$d\" ] && [ ! -L \"\$d\" ]; then")
        appendLine("    b=\$(basename \"\$d\")")
        appendLine("    if [ \"\$b\" != 'current' ] && [ \"\$b\" != 'previous' ]; then")
        appendLine("      if [ -f \"\$d/version.json\" ]; then")
        appendLine("        echo \"VER_START=\$b\"")
        appendLine("        cat \"\$d/version.json\"")
        appendLine("        echo 'VER_END'")
        appendLine("      fi")
        appendLine("    fi")
        appendLine("  fi")
        appendLine("done")
    }

    public suspend fun parseGroupInstalledVersions(
        groupDir: String,
        lines: List<String>,
        groupId: ToolGroupId,
        catalog: ToolGroupCatalog,
        json: Json,
        exec: suspend (List<String>) -> Result<String>,
        probeVersion: String? = null,
    ): List<InstalledVersion> {
        val blocks = extractRawVersionBlocks(lines)
        val catGroup = catalog.group(groupId)
        val installedList = mutableListOf<InstalledVersion>()
        for ((verId, jsonStr) in blocks) {
            val vData =
                try {
                    json.decodeFromString<VersionData>(jsonStr)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    null
                }
            if (vData != null) {
                val intact = verifyVersionIntact(groupDir, verId, vData, catGroup, exec, probe = verId == probeVersion)
                installedList.add(vData.toDomain(groupId, catGroup).copy(id = verId, intact = intact))
            }
        }
        return installedList
    }

    private fun extractRawVersionBlocks(lines: List<String>): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        var idx = 0
        while (idx < lines.size) {
            val line = lines[idx]
            if (line.startsWith("VER_START=")) {
                val verId = line.substringAfter("VER_START=").trim()
                idx++
                if (verId == "current" || verId == "previous") continue
                val jsonSb = StringBuilder()
                while (idx < lines.size && lines[idx] != "VER_END") {
                    jsonSb.appendLine(lines[idx])
                    idx++
                }
                result.add(verId to jsonSb.toString().trim())
            }
            idx++
        }
        return result
    }

    private suspend fun verifyVersionIntact(
        groupDir: String,
        verId: String,
        vData: VersionData,
        catGroup: ToolGroup?,
        exec: suspend (List<String>) -> Result<String>,
        probe: Boolean,
    ): Boolean {
        val outputs =
            catGroup?.outputs ?: vData.outputs.map {
                val kind = if (it.mode == "exec") ToolOutputKind.NATIVE else ToolOutputKind.JAR
                ToolOutput(it.file, it.file, kind, ToolProbe(emptyList()))
            }
        // Probes run only for the version in use (and, before a switch, the one about to be): every installed
        // version would multiply the cost of each status check by the number of folders kept.
        val supportFiles = catGroup?.allSupportFiles().orEmpty()
        val script = versionCheck("$groupDir/$verId", outputs, supportFiles, probe && catGroup != null)
        // A version is broken only when the check fails twice: one slow probe on a busy machine, or one lost
        // connection, must not mark a working tool for repair (and block every build until the next check).
        return exec(listOf("sh", "-c", script)).isSuccess || exec(listOf("sh", "-c", script)).isSuccess
    }

    /**
     * The one definition of "this folder is a usable version", used by the inventory, by reuse of a cached
     * version, by activation and by the check of a fresh build: every output is a non-empty executable (or readable
     * jar), every support file exists and - when [probe] - every tool runs and identifies itself.
     */
    public fun versionCheck(
        versionDir: String,
        outputs: List<ToolOutput>,
        supportFiles: List<String>,
        probe: Boolean,
    ): String {
        val files =
            outputs.joinToString(" && ") {
                validateRelativePath(it.file)
                val flag = if (it.kind == ToolOutputKind.JAR) "-r" else "-x"
                val qFile = shellQuote("$versionDir/${it.file}")
                "[ -s $qFile ] && [ $flag $qFile ]"
            }
        val support = supportFiles.joinToString(" && ") { "[ -e ${shellQuote("$versionDir/$it")} ]" }
        val probes = if (probe) probeChecks(versionDir, outputs) else ""
        return listOf(files, support, probes).filter { it.isNotBlank() }.joinToString(" && ").ifBlank { "true" }
    }

    /**
     * One bounded run of every output's probe. It must exit with a code the install accepted and print something
     * that identifies the tool: a file that is non-empty and executable but not the tool (a replaced script, a
     * stub that only exits 1, a wrong-architecture binary) fails here instead of leaving Setup green until the
     * first ROM job. A probe with no arguments runs the tool bare.
     */
    public fun probeChecks(versionDir: String, outputs: List<ToolOutput>): String {
        if (outputs.isEmpty()) return "true"
        // In parallel: the whole check takes one timeout, however many tools the group has.
        return buildString {
            append("{ pids=''; ")
            for (out in outputs) {
                val file = shellQuote("$versionDir/${out.file}")
                val launcher = if (out.kind == ToolOutputKind.JAR) "java -jar $file" else file
                val args = out.probe.args.joinToString(" ") { shellQuote(it) }
                val accepted = out.probe.acceptedExitCodes.joinToString("|")
                val signature = shellQuote(out.probe.signature ?: defaultSignature(out))
                append("(t=\$(mktemp) || exit 1; ")
                append("timeout $PROBE_SECONDS $launcher $args </dev/null >\"\$t\" 2>&1; rc=\$?; ok=1; ")
                append("case \$rc in $accepted) head -c 16384 \"\$t\" | grep -Eiq $signature && ok=0;; esac; ")
                append("rm -f \"\$t\"; exit \$ok) & ")
                append("pids=\"\$pids \$!\"; ")
            }
            append("ok=0; for pid in \$pids; do wait \$pid || ok=1; done; [ \$ok = 0 ]; }")
        }
    }

    /** Long enough for a JVM tool (apktool, signapk) to start on a busy machine; the probes run in parallel. */
    private const val PROBE_SECONDS = 10

    private fun defaultSignature(out: ToolOutput): String =
        "usage|version|help|options|" + out.toolId.replace(Regex("[^A-Za-z0-9_]"), ".")
}

/** Support files named by the group itself and by any of its outputs, each once. */
internal fun ToolGroup.allSupportFiles(): List<String> = (supportFiles + outputs.flatMap { it.supportFiles }).distinct()
