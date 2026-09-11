package io.ltirom.tooling.client.wsl

import org.ide.lti.core.domain.ports.WslPresencePort

/**
 * Adapter implementing [WslPresencePort] by delegating to [WslEnvironmentDetector].
 */
public class WslPresenceAdapter(
    private val detector: WslEnvironmentDetector = WslEnvironmentDetector()
) : WslPresencePort {
    override suspend fun isWslInstalled(): Boolean = detector.isWslInstalled()
    override suspend fun hasDistro(): Boolean = detector.listDistros().isNotEmpty()
    override suspend fun getDefaultDistro(): String? = detector.getDefaultDistro()
}
