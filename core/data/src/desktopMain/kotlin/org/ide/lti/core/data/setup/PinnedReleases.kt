package org.ide.lti.core.data.setup

/**
 * A prebuilt tool installed from an upstream release archive, pinned by version and by the sha256 of the
 * extracted binary (the same digest `ToolMetadataRegistry` expects). A download that doesn't match is
 * never installed.
 */
internal data class PinnedRelease(
    val toolId: String,
    val version: String,
    val url: String,
    /** Path of the binary inside the archive. */
    val archiveMember: String,
    val sha256: String,
)

internal object PinnedReleases {
    val ALL: List<PinnedRelease> = listOf(
        PinnedRelease(
            toolId = "gh",
            version = "2.97.0",
            url = "https://github.com/cli/cli/releases/download/v2.97.0/gh_2.97.0_linux_amd64.tar.gz",
            archiveMember = "gh_2.97.0_linux_amd64/bin/gh",
            sha256 = "141507c337e8b202ad398550c3b73d72f5af92e86f71665214538a81efd4c409",
        ),
        PinnedRelease(
            toolId = "payload-dumper-go",
            version = "1.3.0",
            url = "https://github.com/ssut/payload-dumper-go/releases/download/1.3.0/" +
                "payload-dumper-go_1.3.0_linux_amd64.tar.gz",
            archiveMember = "payload-dumper-go",
            sha256 = "4abca6f57158f510d15b940e0ac3ead59c9897c7f304bb22c310ad0283d00efd",
        ),
    )
}
