package org.ide.lti.core.model.target

/**
 * Request to pair with a device over wireless ADB (Android 11+ pairing code flow).
 */
public data class WirelessPairingRequest(
    val host: String,
    val port: Int,
    val pairingCode: String,
)
