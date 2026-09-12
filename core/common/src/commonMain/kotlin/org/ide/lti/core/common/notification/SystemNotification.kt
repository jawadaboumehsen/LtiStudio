package org.ide.lti.core.common.notification

/**
 * A single system-level notification surfaced to the user.
 */
public data class SystemNotification(
    val title: String,
    val message: String,
    val level: NotificationLevel = NotificationLevel.Info,
)
