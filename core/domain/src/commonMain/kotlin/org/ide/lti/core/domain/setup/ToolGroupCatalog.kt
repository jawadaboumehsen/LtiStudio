/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.setup

private fun nativeOutput(
    toolId: String,
    probeArgs: List<String> = listOf("--help"),
    acceptedExitCodes: Set<Int> = setOf(0),
    requiredForProduct: Boolean = true,
    aliases: Map<String, String> = emptyMap(),
    supportFiles: List<String> = emptyList(),
): ToolOutput = ToolOutput(
    toolId = toolId,
    file = toolId,
    kind = ToolOutputKind.NATIVE,
    probe = ToolProbe(probeArgs, acceptedExitCodes),
    requiredForProduct = requiredForProduct,
    aliases = aliases,
    supportFiles = supportFiles,
)

/**
 * Authoritative, immutable catalog defining the seven tool build groups, their sources,
 * recipes, layouts, required packages, and outputs (FR-001).
 *
 * Catalog Rule: A tool id's installed file name is immutable across versions and runs.
 */
public object ToolGroupCatalog {

    public val ANDROID_TOOLS: ToolGroup = ToolGroup(
        id = ToolGroupId("android-tools"),
        source = ToolSource.Git(
            recommendedUrl = "https://github.com/UN1CA/external_android-tools.git",
            recommendedCommit = "f9bef94306f93be88b0cf98f9eeb0e24fc7b6aa8",
            recommendedLabel = "sixteen",
        ),
        recipe = RecipeConfig.AndroidTools(
            cmakeArgs = emptyList(),
            gitIdentity = true,
            patchVendor = true,
            revision = 1,
        ),
        layout = listOf("CMakeLists.txt", "vendor"),
        submodulePaths = emptyList(),
        outputs = listOf(
            nativeOutput("adb", probeArgs = listOf("version"), requiredForProduct = false),
            nativeOutput("fastboot", probeArgs = listOf("--version"), requiredForProduct = false),
            nativeOutput(
                toolId = "mke2fs",
                probeArgs = listOf("-V"),
                requiredForProduct = false,
                aliases = mapOf("mke2fs.android" to "mke2fs"),
            ),
            nativeOutput("e2fsdroid", acceptedExitCodes = setOf(0, 1), requiredForProduct = false),
            nativeOutput("lpmake", acceptedExitCodes = setOf(0, 64)),
            nativeOutput("lpunpack", acceptedExitCodes = setOf(0, 64)),
            nativeOutput("lpdump", requiredForProduct = false),
            nativeOutput("lpadd", acceptedExitCodes = setOf(0, 64), requiredForProduct = false),
            nativeOutput("lpflash", acceptedExitCodes = setOf(0, 64), requiredForProduct = false),
            nativeOutput("simg2img", probeArgs = emptyList(), acceptedExitCodes = setOf(1, 255)),
            nativeOutput(
                toolId = "img2simg",
                probeArgs = emptyList(),
                acceptedExitCodes = setOf(1, 255),
                requiredForProduct = false,
            ),
            nativeOutput("ext2simg", acceptedExitCodes = setOf(0, 1), requiredForProduct = false),
            nativeOutput("append2simg", acceptedExitCodes = setOf(0, 1), requiredForProduct = false),
            nativeOutput("mkbootimg", requiredForProduct = false, supportFiles = listOf("gki/")),
            nativeOutput("unpack_bootimg"),
            nativeOutput("repack_bootimg", requiredForProduct = false),
            nativeOutput("mkbootfs", requiredForProduct = false),
            nativeOutput("mkdtboimg", requiredForProduct = false),
            nativeOutput("avbtool"),
            nativeOutput("fec", acceptedExitCodes = setOf(0, 1), requiredForProduct = false),
            nativeOutput("mkuserimg_mke2fs", requiredForProduct = false),
            nativeOutput("make_f2fs", probeArgs = listOf("-V"), requiredForProduct = false),
            nativeOutput("sload_f2fs", probeArgs = listOf("-V"), requiredForProduct = false),
            nativeOutput("mkf2fsuserimg", acceptedExitCodes = setOf(1), requiredForProduct = false),
            nativeOutput("aapt2", probeArgs = listOf("version"), requiredForProduct = false),
            nativeOutput("zipalign", acceptedExitCodes = setOf(2), requiredForProduct = false),
        ),
        buildPackages = listOf(
            "build-essential", "cmake", "make", "clang", "git", "pkg-config", "protobuf-compiler",
            "libprotobuf-dev", "libbrotli-dev", "libbz2-dev", "libgtest-dev", "liblz4-dev",
            "libpcre2-dev", "libzstd-dev", "zlib1g-dev", "libusb-1.0-0-dev", "uuid-dev",
        ),
    )

    public val EROFS_UTILS: ToolGroup = ToolGroup(
        id = ToolGroupId("erofs-utils"),
        source = ToolSource.Git(
            recommendedUrl = "https://github.com/sekaiacg/erofs-tools.git",
            recommendedCommit = "7274417816e3adfe0cd6d4a8ff194ec5f41268f4",
            recommendedLabel = "v1.8.10-251217",
        ),
        recipe = RecipeConfig.CMake(
            sourceSubdir = "build/cmake",
            buildSubdir = "out",
            cmakeArgs = listOf("-DMAX_BLOCK_SIZE=4096"),
            revision = 1,
        ),
        layout = listOf("CMakeLists.txt"),
        submodulePaths = emptyList(),
        outputs = listOf(
            nativeOutput("mkfs.erofs"),
            nativeOutput("dump.erofs", requiredForProduct = false),
            nativeOutput("fsck.erofs", requiredForProduct = false),
            nativeOutput("erofsfuse", acceptedExitCodes = setOf(0, 1)),
        ),
        buildPackages = listOf("build-essential", "cmake", "make", "clang", "libfuse3-dev"),
    )

    public val IMG2SDAT: ToolGroup = ToolGroup(
        id = ToolGroupId("img2sdat"),
        source = ToolSource.Git(
            recommendedUrl = "https://github.com/UN1CA/external_img2sdat.git",
            recommendedCommit = "601317ed0bf502eb8facdd048f2d75dc75ffcd53",
            recommendedLabel = "main",
        ),
        recipe = RecipeConfig.ScriptCopy(
            files = listOf(
                "img2sdat",
                "blockimgdiff.py",
                "common.py",
                "images.py",
                "rangelib.py",
                "sparse_img.py",
            ),
            revision = 1,
        ),
        layout = listOf("img2sdat"),
        submodulePaths = emptyList(),
        outputs = listOf(
            ToolOutput(
                toolId = "img2sdat",
                file = "img2sdat",
                kind = ToolOutputKind.SCRIPT,
                probe = ToolProbe(listOf("--help")),
                requiredForProduct = true,
            ),
        ),
        buildPackages = listOf("python3"),
    )

    public val APKTOOL: ToolGroup = ToolGroup(
        id = ToolGroupId("apktool"),
        source = ToolSource.Git(
            recommendedUrl = "https://github.com/iBotPeaches/Apktool.git",
            recommendedCommit = "1c1d15b070c2138d958fa8b685d430c49c4bb117",
            recommendedLabel = "master",
        ),
        recipe = RecipeConfig.GradleJar(
            task = ":brut.apktool:apktool-cli:shadowJar",
            outputPath = "brut.apktool/apktool-cli/build/libs/apktool-cli.jar",
            revision = 1,
        ),
        layout = listOf("gradlew"),
        submodulePaths = emptyList(),
        outputs = listOf(
            ToolOutput(
                toolId = "apktool",
                file = "apktool.jar",
                kind = ToolOutputKind.JAR,
                probe = ToolProbe(listOf("--version")),
                requiredForProduct = false,
            ),
        ),
        buildPackages = listOf("openjdk-17-jdk"),
    )

    public val SIGNAPK: ToolGroup = ToolGroup(
        id = ToolGroupId("signapk"),
        source = ToolSource.Git(
            recommendedUrl = "https://github.com/UN1CA/external_signapk.git",
            recommendedCommit = "cb88f4b27d8a07ca101eade5d40a6893920cb55e",
            recommendedLabel = "master",
        ),
        recipe = RecipeConfig.GradleJar(
            task = ":signapk:shadowJar",
            outputPath = "signapk/build/libs/signapk-all.jar",
            revision = 1,
        ),
        layout = listOf("gradlew"),
        submodulePaths = emptyList(),
        outputs = listOf(
            ToolOutput(
                toolId = "signapk",
                file = "signapk.jar",
                kind = ToolOutputKind.JAR,
                probe = ToolProbe(listOf("--help"), setOf(0, 2)),
                requiredForProduct = true,
            ),
        ),
        buildPackages = listOf("openjdk-17-jdk"),
    )

    public val PAYLOAD_DUMPER_GO: ToolGroup = ToolGroup(
        id = ToolGroupId("payload-dumper-go"),
        source = ToolSource.Release,
        recipe = RecipeConfig.Release(
            versions = listOf(
                ReleaseVersion(
                    version = "1.3.0",
                    url = "https://github.com/ssut/payload-dumper-go/releases/download/1.3.0/" +
                        "payload-dumper-go_1.3.0_linux_amd64.tar.gz",
                    archiveMember = "payload-dumper-go",
                    sha256 = "4abca6f57158f510d15b940e0ac3ead59c9897c7f304bb22c310ad0283d00efd",
                ),
                ReleaseVersion(
                    version = "1.2.2",
                    url = "https://github.com/ssut/payload-dumper-go/releases/download/1.2.2/" +
                        "payload-dumper-go_1.2.2_linux_amd64.tar.gz",
                    archiveMember = "payload-dumper-go",
                    sha256 = "911e3b6eb4cf84f09d85d7742d4a51e6005c86c125603fe3669be2931557d07c",
                ),
            ),
            revision = 1,
        ),
        layout = emptyList(),
        submodulePaths = emptyList(),
        outputs = listOf(
            nativeOutput("payload-dumper-go"),
        ),
        buildPackages = listOf("tar"),
    )

    public val GH: ToolGroup = ToolGroup(
        id = ToolGroupId("gh"),
        source = ToolSource.Release,
        recipe = RecipeConfig.Release(
            versions = listOf(
                ReleaseVersion(
                    version = "2.97.0",
                    url = "https://github.com/cli/cli/releases/download/v2.97.0/gh_2.97.0_linux_amd64.tar.gz",
                    archiveMember = "gh_2.97.0_linux_amd64/bin/gh",
                    sha256 = "141507c337e8b202ad398550c3b73d72f5af92e86f71665214538a81efd4c409",
                ),
                ReleaseVersion(
                    version = "2.96.0",
                    url = "https://github.com/cli/cli/releases/download/v2.96.0/gh_2.96.0_linux_amd64.tar.gz",
                    archiveMember = "gh_2.96.0_linux_amd64/bin/gh",
                    sha256 = "83d5c2ccad5498f58bf6368acb1ab32588cf43ab3a4b1c301bf36328b1c8bd60",
                ),
            ),
            revision = 1,
        ),
        layout = emptyList(),
        submodulePaths = emptyList(),
        outputs = listOf(
            nativeOutput("gh", probeArgs = listOf("version"), requiredForProduct = false),
        ),
        buildPackages = listOf("tar"),
    )

    public val DEFAULT_GROUPS: List<ToolGroup> = listOf(
        ANDROID_TOOLS,
        EROFS_UTILS,
        IMG2SDAT,
        APKTOOL,
        SIGNAPK,
        GH,
        PAYLOAD_DUMPER_GO,
    )

    private val extraGroups = mutableListOf<ToolGroup>()

    public val allGroups: List<ToolGroup>
        get() = DEFAULT_GROUPS + extraGroups

    public val allOutputs: List<ToolOutput>
        get() = allGroups.flatMap { it.outputs }

    public val allToolIds: Set<String>
        get() = allOutputs.map { it.toolId }.toSet()

    public fun group(id: ToolGroupId): ToolGroup? = allGroups.firstOrNull { it.id == id }

    public fun groupByToolId(toolId: String): ToolGroup? =
        allGroups.firstOrNull { g -> g.outputs.any { it.toolId == toolId } }

    public fun outputFor(toolId: String): ToolOutput? = allOutputs.firstOrNull { it.toolId == toolId }

    public fun registerTestGroup(group: ToolGroup) {
        extraGroups += group
    }

    public fun resetTestGroups() {
        extraGroups.clear()
    }

    public inline fun <T> withTestGroup(group: ToolGroup, block: () -> T): T {
        registerTestGroup(group)
        return try {
            block()
        } finally {
            resetTestGroups()
        }
    }
}
