package org.ide.lti.core.model.user

/**
 * User-configurable notification preferences.
 */
public data class NotificationSettings(
    val pushEnabled: Boolean = true,
    val soundEnabled: Boolean = false,
)
