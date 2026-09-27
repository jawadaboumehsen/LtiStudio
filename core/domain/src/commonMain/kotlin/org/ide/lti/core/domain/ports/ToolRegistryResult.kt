package org.ide.lti.core.domain.ports

public sealed interface ToolRegistryResult {
    public data class Tools(val list: List<ToolStatus>) : ToolRegistryResult
    public data class ServiceError(val reason: String) : ToolRegistryResult
}
