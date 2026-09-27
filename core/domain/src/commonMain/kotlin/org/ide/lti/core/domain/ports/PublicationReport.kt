package org.ide.lti.core.domain.ports

public data class PublicationReport(
    val requested: Set<String>,
    val registered: Set<String>,
    val failed: Map<String, String>,
    val pruned: Set<String>
) {
    val isComplete: Boolean
        get() = registered == requested
}
