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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfigValidatorsTest {

    @Test
    fun validatesValidAndInvalidFstab() {
        val validFstab = """
            # Android fstab file
            /dev/block/by-name/system /system ext4 ro,barrier=1 wait,logical,first_stage_mount
            /dev/block/by-name/vendor /vendor erofs ro wait,logical
            /dev/block/by-name/userdata /data f2fs noatime,nosuid,nodev wait,check,quota
        """.trimIndent()

        val errors = ConfigValidators.validateFstab(validFstab)
        assertTrue(errors.isEmpty(), "Valid fstab should have no errors: $errors")

        val invalidFstab = """
            # Invalid lines
            /dev/block/by-name/system
            /dev/block/by-name/vendor /vendor
        """.trimIndent()

        val invalidErrors = ConfigValidators.validateFstab(invalidFstab)
        assertEquals(2, invalidErrors.size)
    }

    @Test
    fun validatesValidAndInvalidBuildProp() {
        val validProp = """
            # Build properties
            ro.build.version.release=14
            ro.product.model=SM-S918B
            ro.debuggable=0
        """.trimIndent()

        val errors = ConfigValidators.validateBuildProp(validProp)
        assertTrue(errors.isEmpty())

        val invalidProp = """
            ro.build.version.release=14
            invalid_line_without_equals_sign
            ro product model=bad_key_with_spaces
        """.trimIndent()

        val invalidErrors = ConfigValidators.validateBuildProp(invalidProp)
        assertEquals(2, invalidErrors.size)
    }

    @Test
    fun validatesValidAndInvalidFsConfig() {
        val validFsConfig = """
            system 0 0 755 capabilities=0x0
            system/bin/sh 0 2000 755 capabilities=0x0
            system/build.prop 0 0 644 capabilities=0x0
        """.trimIndent()

        val errors = ConfigValidators.validateFsConfig(validFsConfig)
        assertTrue(errors.isEmpty())

        val invalidFsConfig = """
            system 0 0
            system/bin/sh zero 2000 755
        """.trimIndent()

        val invalidErrors = ConfigValidators.validateFsConfig(invalidFsConfig)
        assertEquals(2, invalidErrors.size)
    }

    @Test
    fun validatesValidAndInvalidFileContexts() {
        val validContexts = """
            /system(/.*)? u:object_r:system_file:s0
            /system/bin/sh u:object_r:shell_exec:s0
            /vendor(/.*)? u:object_r:vendor_file:s0
        """.trimIndent()

        val errors = ConfigValidators.validateFileContexts(validContexts)
        assertTrue(errors.isEmpty())

        val invalidContexts = """
            /system(/.*)?
            /vendor(/.*)? invalid_selinux_context
        """.trimIndent()

        val invalidErrors = ConfigValidators.validateFileContexts(invalidContexts)
        assertEquals(2, invalidErrors.size)
    }
}
