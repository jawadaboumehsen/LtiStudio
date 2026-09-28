package org.ide.lti.core.domain.ports

public enum class ToolSource {
    DYNAMIC,
    PINNED,
    SYSTEM_PATH,
}

public data class ToolStatus(
    val tool: String,
    val installed: Boolean,
    val path: String,
    val source: ToolSource?,
    val reason: String?,
)
