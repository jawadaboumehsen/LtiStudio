package org.ide.lti.core.model.user

/**
 * User-configurable code editor preferences.
 */
public data class EditorSettings(
    val autoSave: Boolean = true,
    val lineNumbers: Boolean = true,
    val bracketMatching: Boolean = true,
    val fontSize: Int = DEFAULT_FONT_SIZE,
    val tabSize: Int = DEFAULT_TAB_SIZE,
) {
    public companion object {
        public const val DEFAULT_FONT_SIZE: Int = 14
        public const val DEFAULT_TAB_SIZE: Int = 4
    }
}
