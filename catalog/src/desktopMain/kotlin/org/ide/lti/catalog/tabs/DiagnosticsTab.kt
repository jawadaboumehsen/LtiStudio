/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.catalog.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.ide.lti.core.designsystem.component.actions.GlassPrimaryButton
import org.ide.lti.core.designsystem.component.actions.GlassSecondaryButton
import org.ide.lti.core.designsystem.component.display.GlassDiagnosticItem
import org.ide.lti.core.designsystem.component.display.GlassDiagnosticList
import org.ide.lti.core.designsystem.component.feedback.GlassBanner
import org.ide.lti.core.designsystem.component.feedback.GlassBannerAction
import org.ide.lti.core.designsystem.component.feedback.GlassBannerSeverity
import org.ide.lti.core.designsystem.component.navigation.GlassFilterChipItem
import org.ide.lti.core.designsystem.component.navigation.GlassFilterChipRow
import org.ide.lti.core.designsystem.component.progress.GlassStepInfo
import org.ide.lti.core.designsystem.component.progress.GlassStepStatus
import org.ide.lti.core.designsystem.component.progress.GlassStepper
import org.ide.lti.core.designsystem.component.progress.GlassStepperOrientation
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing

/**
 * Catalog demonstration tab for diagnostic, pipeline progress, and feedback components.
 */
@Composable
fun DiagnosticsTab() {
    val scrollState = rememberScrollState()

    // Stepper Demo State
    var stepperOrientation by remember { mutableStateOf(GlassStepperOrientation.Horizontal) }
    var stepperActionMessage by remember { mutableStateOf<String?>(null) }
    var isStepperBusy by remember { mutableStateOf(false) }

    val sampleSteps = remember {
        listOf(
            GlassStepInfo(
                id = "wsl",
                shortTitle = "WSL 2",
                status = GlassStepStatus.SUCCESS,
                statusLabel = "Ready",
            ),
            GlassStepInfo(
                id = "bridge",
                shortTitle = "Bridge",
                status = GlassStepStatus.SUCCESS,
                statusLabel = "Ready",
            ),
            GlassStepInfo(
                id = "doctor",
                shortTitle = "Doctor",
                status = GlassStepStatus.RUNNING,
                statusLabel = "Working...",
            ),
            GlassStepInfo(
                id = "submodules",
                shortTitle = "Submodules",
                status = GlassStepStatus.FAILED,
                statusLabel = "Failed",
            ),
            GlassStepInfo(
                id = "compilers",
                shortTitle = "Compilers",
                status = GlassStepStatus.PENDING,
                statusLabel = "Pending",
            ),
        )
    }

    // Banner Demo State
    var bannerStatusMessage by remember { mutableStateOf<String?>(null) }
    var showDismissableBanner by remember { mutableStateOf(true) }

    // Filter Chip Demo State
    var selectedCategory by remember { mutableStateOf("All") }
    val filterItems = remember {
        listOf(
            GlassFilterChipItem(label = "All", count = 35, value = "All"),
            GlassFilterChipItem(label = "Dynamic Partitions", count = 12, value = "Dynamic Partitions"),
            GlassFilterChipItem(label = "Filesystem & Images", count = 8, value = "Filesystem & Images"),
            GlassFilterChipItem(label = "Boot & Kernel", count = 6, value = "Boot & Kernel"),
            GlassFilterChipItem(label = "Signing & Security", count = 5, value = "Signing & Security"),
            GlassFilterChipItem(label = "Bridge & Flashing", count = 4, value = "Bridge & Flashing"),
        )
    }

    // Diagnostic List Demo State
    val diagnosticItems = remember {
        listOf(
            GlassDiagnosticItem(
                id = "diag_kernel",
                title = "WSL 2 Kernel v5.15+ Loop Mount Support",
                detail = "WSL 2 Linux kernel 5.15.153.1 verified with dynamic loop mounting support for ext4/erofs partitions.",
                status = GlassStepStatus.SUCCESS,
                category = "Kernel",
                copyableCommand = "uname -r",
            ),
            GlassDiagnosticItem(
                id = "diag_brotli",
                title = "Brotli Compression Utility",
                detail = "v1.1.0 installed and staged in ~/LtiRomTools/bin/brotli.",
                status = GlassStepStatus.SUCCESS,
                category = "Dynamic Partitions",
                copyableCommand = "brotli --version",
            ),
            GlassDiagnosticItem(
                id = "diag_signing",
                title = "AVB 2.0 Custom Vendor Signing Key Pair",
                detail = "Custom vendor AVB key pair not found in keys/avb_pk8. Falling back to default AOSP test key.",
                status = GlassStepStatus.WARNING,
                category = "Signing",
            ),
            GlassDiagnosticItem(
                id = "diag_erofs",
                title = "EROFS mkfs.erofs Toolchain Binary",
                detail = "mkfs.erofs binary is missing from PATH. Required for super.img dynamic partition compilation.",
                status = GlassStepStatus.FAILED,
                category = "Filesystem",
                copyableCommand = "sudo apt-get install -y erofs-utils",
            ),
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(Spacing.Large),
            verticalArrangement = Arrangement.spacedBy(Spacing.ExtraLarge),
        ) {
            // Section 1: Glass Stepper
            SectionContainer(
                title = "1. Stage Progress Stepper (GlassStepper)",
                description = "Pipeline stage progression tracker generalizing ToolchainPipelineTracker with live execution states, retry triggers, and directional stage transitions.",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GlassSecondaryButton(
                            onClick = {
                                stepperOrientation = if (stepperOrientation == GlassStepperOrientation.Horizontal) {
                                    GlassStepperOrientation.Vertical
                                } else {
                                    GlassStepperOrientation.Horizontal
                                }
                            },
                        ) {
                            Text(
                                text = "Switch to ${if (stepperOrientation == GlassStepperOrientation.Horizontal) "Vertical" else "Horizontal"}",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }

                        GlassSecondaryButton(
                            onClick = { isStepperBusy = !isStepperBusy },
                        ) {
                            Text(
                                text = if (isStepperBusy) "Pipeline: Busy" else "Pipeline: Idle",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }

                        if (stepperActionMessage != null) {
                            Text(
                                text = stepperActionMessage.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = GlassTheme.colors.glassAccent,
                            )
                        }
                    }

                    GlassStepper(
                        steps = sampleSteps,
                        orientation = stepperOrientation,
                        isBusy = isStepperBusy,
                        onRetryStep = { stepId ->
                            stepperActionMessage = "Retrying step: $stepId"
                        },
                        onStepClick = { stepId ->
                            stepperActionMessage = "Clicked step: $stepId"
                        },
                    )
                }
            }

            // Section 2: Glass Banner
            SectionContainer(
                title = "2. Liquid Glass Alerts & Notices (GlassBanner)",
                description = "Contextual notice banners across 4 severity tiers with primary/secondary actions, dismiss triggers, and tinted frosted styling.",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
                    if (bannerStatusMessage != null) {
                        Text(
                            text = bannerStatusMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = GlassTheme.colors.glassAccent,
                        )
                    }

                    // INFO Banner
                    GlassBanner(
                        severity = GlassBannerSeverity.INFO,
                        title = "Doctor Automated Self-Healing",
                        description = "The doctor will automatically provision missing compilers, packages, and kernel configurations via root elevation.",
                        primaryAction = GlassBannerAction("Run Self-Healing") {
                            bannerStatusMessage = "Action: Run Self-Healing clicked"
                        },
                    )

                    // SUCCESS Banner
                    GlassBanner(
                        severity = GlassBannerSeverity.SUCCESS,
                        title = "Native Toolchain Compilation Verified",
                        description = "All 35 Android ROM engineering binaries successfully compiled and verified in ~/LtiRomTools/bin.",
                    )

                    // WARNING Banner
                    GlassBanner(
                        severity = GlassBannerSeverity.WARNING,
                        title = "Environment Verification Required",
                        description = "WSL 2 distribution requires a kernel restart before loop devices can be dynamically mounted.",
                        secondaryAction = GlassBannerAction("Ignore") {
                            bannerStatusMessage = "Action: Ignore clicked"
                        },
                        primaryAction = GlassBannerAction("Restart Kernel") {
                            bannerStatusMessage = "Action: Restart Kernel clicked"
                        },
                    )

                    // ERROR Banner (Dismissable)
                    if (showDismissableBanner) {
                        GlassBanner(
                            severity = GlassBannerSeverity.ERROR,
                            title = "Missing Compiler: aarch64-linux-android-clang",
                            description = "NDK toolchain compiler was not found in PATH. Build pipeline cannot compile vendor dynamic partitions.",
                            primaryAction = GlassBannerAction("Reinstall") {
                                bannerStatusMessage = "Action: Reinstall compiler clicked"
                            },
                            onDismiss = {
                                showDismissableBanner = false
                                bannerStatusMessage = "Error banner dismissed"
                            },
                        )
                    } else {
                        GlassPrimaryButton(onClick = { showDismissableBanner = true }) {
                            Text("Restore Dismissed Error Banner")
                        }
                    }
                }
            }

            // Section 3: Glass Filter Chip Row
            SectionContainer(
                title = "3. Category Filter Capsule Row (GlassFilterChipRow)",
                description = "Adaptive FlowRow of filter chips displaying category counts with active accent border and tint.",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                    GlassFilterChipRow(
                        items = filterItems,
                        selectedValue = selectedCategory,
                        onSelect = { selectedCategory = it },
                    )

                    Text(
                        text = "Active filter: $selectedCategory",
                        style = MaterialTheme.typography.bodySmall,
                        color = GlassTheme.colors.onGlassSecondary,
                    )
                }
            }

            // Section 4: Glass Diagnostic List
            SectionContainer(
                title = "4. Diagnostic Check List & Tiles (GlassDiagnosticList)",
                description = "Status-badged inspection tiles with category chips, status labels, detail text, and copyable monospace bash command pills.",
            ) {
                GlassDiagnosticList(items = diagnosticItems)
            }
        }
    }
}
