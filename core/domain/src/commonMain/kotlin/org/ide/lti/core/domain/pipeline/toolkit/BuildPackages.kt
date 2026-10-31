/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.toolkit

/**
 * Packages the Build stage makes besides the flashable zip (FR-013): a fastboot images zip and a Samsung Odin AP.
 * Their names are what `BuildSettings.extraPackages` stores.
 */
public enum class PackageKind {
    FullPackage,
    Odin,
}

/** Build warnings the user accepts by name (`BuildSettings.acknowledgedWarnings`) (FR-017). */
public enum class BuildWarningKind {
    /** A dynamic partition chosen as a standalone image is also inside super.img. */
    SuperOverlap,

    /** Symbolic links the build left pointing at nothing (they work in the stock firmware). */
    BrokenLinks,

    /** APKs in the work tree with no signature at all (Android will not install them). */
    UnsignedApks,
}
