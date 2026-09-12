package org.ide.lti.core.domain.repository.target

import kotlinx.coroutines.flow.Flow
import org.ide.lti.core.model.target.TargetDevice

/**
 * Repository for target hardware device profiles and their active build configuration.
 */
public interface TargetRepository {
    public fun getAvailableTargets(): Flow<List<TargetDevice>>

    public fun getSelectedTarget(): Flow<TargetDevice>

    public suspend fun selectTarget(targetId: String): Result<TargetDevice>

    public suspend fun addTarget(target: TargetDevice): Result<TargetDevice>

    public suspend fun updateTarget(target: TargetDevice): Result<TargetDevice>

    public suspend fun deleteTarget(targetId: String): Result<Unit>
}
