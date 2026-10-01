/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.model.setup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Reference specification for a tool version: Git tag, branch, full 40-hex commit, or release version string.
 */
@Serializable
sealed interface ToolRef {
    @Serializable
    @SerialName("Tag")
    data class Tag(val name: String) : ToolRef {
        init {
            require(name.isNotBlank()) { "Tag name must not be blank" }
        }
    }

    @Serializable
    @SerialName("Branch")
    data class Branch(val name: String) : ToolRef {
        init {
            require(name.isNotBlank()) { "Branch name must not be blank" }
        }
    }

    @Serializable
    @SerialName("Commit")
    data class Commit(val sha: String) : ToolRef {
        init {
            require(sha.matches(HEX_40_REGEX)) {
                "Commit SHA must be exactly 40 lowercase hex characters: $sha"
            }
        }

        private companion object {
            private val HEX_40_REGEX = Regex("^[0-9a-f]{40}$")
        }
    }

    @Serializable
    @SerialName("ReleaseVersion")
    data class ReleaseVersion(val version: String) : ToolRef {
        init {
            require(version.isNotBlank()) { "Release version must not be blank" }
        }
    }
}

/**
 * Persisted selection of a tool group's repository source and target ref.
 */
@Serializable
data class ToolSelection(val group: String, val repoUrl: String? = null, val ref: ToolRef) {
    init {
        require(group.isNotBlank()) { "Group must not be blank" }
        repoUrl?.let { url ->
            require(url.isNotBlank()) { "repoUrl must not be blank if provided" }
            val isHttps = url.startsWith("https://")
            val isSsh = url.startsWith("ssh://") || url.startsWith("git@")
            require(isHttps || isSsh) { "repoUrl must be an HTTPS or SSH URL: $url" }

            if (isHttps) {
                val hostPart = url.removePrefix("https://").substringBefore('/')
                require(!hostPart.contains('@')) {
                    "repoUrl must not contain user credentials/user-info: $url"
                }
            } else if (url.startsWith("ssh://")) {
                val hostPart = url.removePrefix("ssh://").substringBefore('/')
                require(!hostPart.contains(':') && !hostPart.contains('@')) {
                    "repoUrl must not contain user credentials/user-info: $url"
                }
            } else if (url.startsWith("git@")) {
                val hostAndPath = url.removePrefix("git@")
                require(!hostAndPath.contains(':') || !hostAndPath.substringBefore(':').contains(':')) {
                    "repoUrl must not contain password credentials"
                }
            }
        }
    }

    companion object {
        fun normalizeRepoUrl(repoUrl: String): String {
            val trimmed = repoUrl.trim()
            if (trimmed.isEmpty()) return ""
            val schemeEnd = trimmed.indexOf("://")
            val (schemeAndHost, path) = if (schemeEnd != -1) {
                val hostEnd = trimmed.indexOf('/', schemeEnd + 3)
                if (hostEnd != -1) {
                    trimmed.substring(0, hostEnd).lowercase() to trimmed.substring(hostEnd)
                } else {
                    trimmed.lowercase() to ""
                }
            } else {
                "" to trimmed
            }
            val full = schemeAndHost + path
            return full.trimEnd('/').removeSuffix(".git")
        }
    }
}

/**
 * Tool selections configured for a specific distribution, tracked with a monotonic CAS revision.
 */
@Serializable
data class DistroToolSelections(val desired: Map<String, ToolSelection> = emptyMap(), val revision: Long = 0L)
