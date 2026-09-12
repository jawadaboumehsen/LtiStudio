package org.ide.lti.core.model.user

/**
 * Aggregate root for persisted user preferences.
 */
public data class UserData(
    val theme: String = DEFAULT_THEME,
    val effectsEnabled: Boolean = true,
    val reducedMotion: Boolean = false,
    val editorSettings: EditorSettings = EditorSettings(),
    val notificationSettings: NotificationSettings = NotificationSettings(),
) {
    public companion object {
        public const val DEFAULT_THEME: String = "blue"
    }
}
