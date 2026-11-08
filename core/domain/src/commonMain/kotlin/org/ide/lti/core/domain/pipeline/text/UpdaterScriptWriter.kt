/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.text

import org.ide.lti.core.model.target.PackagePolicy
import org.ide.lti.core.model.target.TargetDevice

@Suppress("MaxLineLength") // The Edify statements read best whole.
public object UpdaterScriptWriter {

    /** [text] inside an Edify string: a quote in a version or device name must not end the string. */
    private fun edify(text: String): String = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

    public fun generate(target: TargetDevice, policy: PackagePolicy, romVersion: String = "1.0.0"): String {
        val sb = StringBuilder()

        // 1. Device assertions
        if (target.assertModels.isNotEmpty()) {
            val assertions = target.assertModels.flatMap { model ->
                listOf(
                    "getprop(\"ro.product.device\") == \"${edify(model)}\"",
                    "getprop(\"ro.build.product\") == \"${edify(model)}\"",
                    "getprop(\"ro.boot.em.model\") == \"${edify(model)}\"",
                )
            }.joinToString(" ||\n    ")
            sb.append(
                "assert(\n    $assertions ||\n    abort(\"E3004: This package is for \\\"${edify(
                    target.assertModels.joinToString(","),
                )}\\\" devices; this is a \\\"\" + getprop(\"ro.product.device\") + \"\\\".\");\n);\n\n",
            )
        }

        // 2. Banner
        sb.append("ui_print(\"--------------------------------------\");\n")
        sb.append("ui_print(\" Installing LtiRom ${edify(romVersion)}\");\n")
        sb.append("ui_print(\" Device: ${edify(target.name)}\");\n")
        sb.append("ui_print(\"--------------------------------------\");\n\n")

        // 3. Unmount dynamic partitions
        for (part in policy.flashablePartitions) {
            val mp = if (part == "system") policy.recoverySystemMountPoint else "/$part"
            sb.append(
                "run_program(\"/system/bin/sh\", \"-c\", \"if /system/bin/mountpoint -q $mp; then /system/bin/umount $mp; fi\");\n",
            )
        }
        sb.append("\n")

        // 4. Update dynamic partitions
        sb.append("ui_print(\"Updating dynamic partitions...\");\n")
        sb.append(
            "update_dynamic_partitions(package_extract_file(\"dynamic_partitions_op_list\"), package_extract_file(\"unsparse_super_empty.img\"));\n\n",
        )

        // 5. Block image updates for dynamic partitions
        for (part in policy.flashablePartitions) {
            sb.append("ui_print(\"Patching $part image...\");\n")
            sb.append(
                "block_image_update(map_partition(\"$part\"), package_extract_file(\"$part.transfer.list\"), \"$part.new.dat.br\", \"$part.patch.dat\");\n",
            )
        }
        sb.append("\n")

        // 6. Boot partitions flashing
        val bootDev = target.bootDevicePath.removeSuffix("/")
        for (bootPart in policy.flashableBootPartitions) {
            sb.append("ui_print(\"Flashing $bootPart...\");\n")
            for (slot in policy.bootSlots) {
                sb.append("package_extract_file(\"$bootPart.img\", \"$bootDev/${bootPart}_$slot\");\n")
            }
        }
        sb.append("\n")

        sb.append("set_progress(1.000000);\n")
        sb.append("ui_print(\"Install completed successfully.\");\n")

        return sb.toString()
    }
}
