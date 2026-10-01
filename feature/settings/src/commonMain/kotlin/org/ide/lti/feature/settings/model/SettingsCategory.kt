/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.settings.model

/**
 * Categorical destinations available within the LtiRom Studio Settings workspace.
 */
enum class SettingsCategory(val title: String, val subtitle: String) {
    EditorPreferences(
        title = "Editor preferences",
        subtitle = "Customize the editing experience",
    ),
    Appearance(
        title = "Appearance",
        subtitle = "Make the studio comfortable for long sessions",
    ),
    Workspace(
        title = "Workspace",
        subtitle = "Project directories, indexing, and multi-window layout",
    ),
    BuildAndRun(
        title = "Build & run",
        subtitle = "JDK toolchains, Android SDK, ADB runtime, and compilation targets",
    ),
    Network(
        title = "Network",
        subtitle = "Proxy settings, marketplace connection, and offline mode",
    ),
    PrivacyAndData(
        title = "Privacy & data",
        subtitle = "Telemetry, crash reports, and local data retention",
    ),
    Updates(
        title = "Updates",
        subtitle = "Studio releases, component channels, and auto-update checks",
    ),
    About(
        title = "About",
        subtitle = "Distribution release, framework runtime, architecture, and diagnostics",
    ),

    // Legacy and secondary categories
    General(
        title = "General",
        subtitle = "Basic workspace behavior, startup preferences, and language",
    ),
    Editor(
        title = "Editor",
        subtitle = "Typography, syntax highlighting, and editor ergonomics",
    ),
    TargetProfiles(
        title = "Target profiles",
        subtitle = "Active target configuration profiles and hardware variants",
    ),
    BuildEnvironment(
        title = "Build environment",
        subtitle = "JDK toolchains, Android SDK, ADB runtime, and compilation targets",
    ),
    PluginSources(
        title = "Plugin sources",
        subtitle = "Marketplace registry, custom plugin repositories, and mirror paths",
    ),
    Credentials(
        title = "Credentials",
        subtitle = "Git credentials, SSH keys, signing certificates, and API tokens",
    ),
    Notifications(
        title = "Notifications",
        subtitle = "System alerts, auditory cues, and background job notifications",
    ),
    Paths(
        title = "Paths",
        subtitle = "ROM storage locations, cache directories, and scratch output",
    ),
    Diagnostics(
        title = "Diagnostics",
        subtitle = "System health, event logging, crash telemetry, and diagnostic bundles",
    ),
    ;

    companion object {
        val sidebarCategories: List<SettingsCategory> = listOf(
            EditorPreferences,
            Appearance,
            Workspace,
            BuildAndRun,
            Network,
            PrivacyAndData,
            Updates,
            About,
        )
    }
}
