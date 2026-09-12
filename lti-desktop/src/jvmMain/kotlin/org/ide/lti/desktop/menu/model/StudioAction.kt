/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.ide.lti.desktop.menu.model

/**
 * High-level IDE actions executable via the desktop menu bar, keyboard shortcuts, or command palette.
 * Follows the Single Responsibility Principle by decoupling action identifiers from their execution.
 */
enum class StudioAction {
    // File
    NEW_FILE,
    OPEN_WORKSPACE,
    CLONE_GIT,
    OPEN_STAGING_WORKDIR,
    SAVE,
    SAVE_ALL,
    OPEN_SETTINGS,
    EXIT,

    // Edit
    UNDO,
    REDO,
    CUT,
    COPY,
    PASTE,
    FIND,
    SELECT_ALL,

    // View
    TOGGLE_EXPLORER,
    TOGGLE_TERMINAL,
    TOGGLE_AI_ASSISTANT,
    RESET_LAYOUT,

    // Window
    MINIMIZE_WINDOW,
    ZOOM_WINDOW,
    BRING_ALL_TO_FRONT,

    // Help
    ABOUT,
    DOCUMENTATION,
    CHECK_UPDATES,
}
