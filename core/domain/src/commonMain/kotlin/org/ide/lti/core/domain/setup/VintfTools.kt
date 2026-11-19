/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.setup

import org.ide.lti.core.model.setup.ToolRef

/**
 * checkvintf: AOSP's VINTF compatibility checker (the one AOSP's release tools run before an OTA), built from
 * android-16.0.0_r4: system/libvintf plus the AOSP projects it needs, each pinned, and the CMake build the app
 * carries (resources tool-recipes/vintf: AOSP ships no build of it outside its tree).
 */
public val VINTF_TOOLS_GROUP: ToolGroup =
    ToolGroup(
        id = ToolGroupId("vintf-tools"),
        source =
        ToolSource.Git(
            recommendedUrl = "$AOSP/platform/system/libvintf",
            pinnedRef = ToolRef.Tag("android-16.0.0_r4"),
            pinnedCommit = "69c456ea4aa2f503a2904cfbc11f279a3b2efb09",
        ),
        recipe =
        RecipeConfig.AospCMake(
            projects =
            listOf(
                AospProject(
                    "tinyxml2",
                    "$AOSP/platform/external/tinyxml2",
                    "5f2c372c6213bfcb312adcfc9cafbce9f99eee78",
                ),
                AospProject(
                    "gki",
                    "$AOSP/platform/packages/modules/Gki",
                    "0c2d0423105c14eec22adb5417dfcef6c888c5bd",
                ),
                AospProject("libbase", "$AOSP/platform/system/libbase", "bdf0f4db82fae78492c92a55d473aacc4e963e79"),
                AospProject("logging", "$AOSP/platform/system/logging", "58dac317ea08b8f275ebd01ce864acab2de30b93"),
                AospProject("core", "$AOSP/platform/system/core", "e9d1fa3705d7fbd0ac1c942bc4c276161bfbfed1"),
                AospProject("fmtlib", "$AOSP/platform/external/fmtlib", "d50695f04854e474766da7d713bf8242f4ceb4f8"),
            ),
            bundle = "vintf",
            bundledFiles =
            listOf(
                "CMakeLists.txt",
                "stubs/com_android_apex.cpp",
                "stubs/com_android_apex.h",
                "stubs/metadata.cpp",
                "third_party/fcm_exclude/fcm_exclude.cpp",
                "third_party/fcm_exclude/include/vintf/fcm_exclude.h",
                "third_party/metadata/include/aidl/metadata.h",
                "third_party/metadata/include/hidl/metadata.h",
            ),
            revision = 1,
        ),
        layout = listOf("check_vintf.cpp", "Android.bp"),
        submodulePaths = emptyList(),
        outputs =
        listOf(
            // --help prints the usage and exits EX_USAGE.
            nativeOutput(
                "checkvintf",
                probeArgs = listOf("--help"),
                acceptedExitCodes = setOf(64),
                // The VINTF check says it was not run without it; a ROM still builds.
                requiredForProduct = false,
                licence = "Apache-2.0",
            ),
        ),
        buildPackages = listOf("build-essential", "cmake", "make", "clang", "git", "zlib1g-dev"),
    )

/** Where AOSP's projects are served. */
private const val AOSP = "https://android.googlesource.com"
