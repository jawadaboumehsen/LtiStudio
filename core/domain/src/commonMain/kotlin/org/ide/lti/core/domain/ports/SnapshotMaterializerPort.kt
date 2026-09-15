package org.ide.lti.core.domain.ports

public data class SnapshotMaterializationIntent(val workspaceId: String, val snapshotId: String, val status: String)

public interface SnapshotMaterializerPort {
    public suspend fun markMaterializing(workspaceId: String, snapshotId: String)
    public suspend fun clearIntent(workspaceId: String, snapshotId: String)
    public suspend fun findIncompleteMaterializations(): List<SnapshotMaterializationIntent>
}
