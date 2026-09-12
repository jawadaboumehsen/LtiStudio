package org.ide.lti.core.common.notification

/**
 * Platform-level sink for system notifications (e.g. OS tray/toast).
 */
public interface SystemNotifier {
    public val isSupported: Boolean
    public fun notify(notification: SystemNotification)
}

/**
 * Default fallback implementation for environments without notification capabilities or during tests.
 */
public object NoOpSystemNotifier : SystemNotifier {
    override val isSupported: Boolean = false

    override fun notify(notification: SystemNotification) {
        // Safe no-op
    }
}

/**
 * Thread-safe delegating [SystemNotifier] that allows platform implementations (such as Compose Desktop
 * tray state) to attach and detach dynamically across application lifecycle boundaries.
 */
public class DelegatingSystemNotifier : SystemNotifier {
    @kotlin.jvm.Volatile
    private var delegate: SystemNotifier = NoOpSystemNotifier

    public fun attach(notifier: SystemNotifier) {
        delegate = notifier
    }

    public fun detach() {
        delegate = NoOpSystemNotifier
    }

    override val isSupported: Boolean
        get() = delegate.isSupported

    override fun notify(notification: SystemNotification) {
        delegate.notify(notification)
    }
}

