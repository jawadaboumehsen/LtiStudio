package org.ide.lti.core.common.notification

/**
 * Platform-level sink for system notifications (e.g. OS tray/toast).
 */
public interface SystemNotifier {
    public val isSupported: Boolean
    public fun notify(notification: SystemNotification)
}
