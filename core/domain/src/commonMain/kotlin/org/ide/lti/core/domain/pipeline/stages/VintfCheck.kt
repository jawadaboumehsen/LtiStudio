/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.stages

/**
 * Whether the system side of work/ still meets what the vendor side expects (Android's VINTF), from the files
 * themselves: the SELinux mapping for the vendor's sepolicy version (without it init cannot build the policy and
 * the phone does not boot), the framework matrix for the vendor's target FCM level (without it Android warns of an
 * internal problem), the system SDK versions the vendor matrix asks for, and the non-optional HALs each side
 * requires of the other, by format and name. Kernel configuration and HAL version ranges are not compared (that
 * takes `checkvintf`); missing evidence is said, not passed over. Each finding is `CODE: text`.
 */
public object VintfCheck {
    /** `$1` work/: each VINTF file as `@@@ <path>` and its text, then the matrix levels, mappings and versions. */
    public const val SCRIPT: String = """cd "$1" 2>/dev/null || exit 0
s=system/system/etc
l=${'$'}(cat vendor/etc/vintf/manifest*.xml odm/etc/vintf/manifest*.xml 2>/dev/null |
  grep -o 'target-level="[^"]*"' | head -n 1 | cut -d'"' -f2)
for f in vendor/etc/vintf/manifest*.xml vendor/etc/vintf/manifest/*.xml odm/etc/vintf/manifest*.xml \
  odm/etc/vintf/manifest/*.xml vendor/etc/vintf/compatibility_matrix.xml odm/etc/vintf/compatibility_matrix.xml \
  ${'$'}s/vintf/manifest.xml ${'$'}s/vintf/manifest/*.xml system_ext/etc/vintf/manifest.xml \
  system_ext/etc/vintf/manifest/*.xml product/etc/vintf/manifest.xml product/etc/vintf/manifest/*.xml \
  "${'$'}s/vintf/compatibility_matrix.${'$'}l.xml"; do
  if [ -f "${'$'}f" ] && [ ! -L "${'$'}f" ]; then printf '@@@ %s\n' "${'$'}f"; cat "${'$'}f"; printf '\n'; fi
done
printf '@@@ levels\n'; ls ${'$'}s/vintf 2>/dev/null | sed -n 's/^compatibility_matrix\.\(.*\)\.xml${'$'}/\1/p'
printf '@@@ mapping\n'; ls ${'$'}s/selinux/mapping 2>/dev/null
printf '@@@ vendor-sepolicy\n'; cat vendor/etc/selinux/plat_sepolicy_vers.txt 2>/dev/null
exit 0"""

    /** The codes that only warn: a missing SELinux mapping is the one that stops the phone booting. */
    public val NOTES: Set<String> = setOf("VINTF_LEVEL", "VINTF_SYSTEM_SDK", "VINTF_HAL", "VINTF_NOT_CHECKED")

    /** What [SCRIPT] printed, judged. */
    public fun findings(text: String): List<String> {
        val blocks = text.split("@@@ ").filter { it.isNotBlank() }.associate { block ->
            block.substringBefore('\n').trim() to block.substringAfter('\n', "").replace(COMMENT, "")
        }
        val device = blocks.filterKeys { it.startsWith("vendor/") || it.startsWith("odm/") }
        val deviceManifests = device.filterKeys { "/manifest" in it }.values
        if (deviceManifests.isEmpty()) {
            return listOf("VINTF_NOT_CHECKED: no device manifest under vendor/etc/vintf or odm/etc/vintf")
        }
        val frameworkManifests = blocks.filterKeys { it.isFramework() && "/manifest" in it }.values
        val level = deviceManifests.firstNotNullOfOrNull { TARGET_LEVEL.find(it)?.groupValues?.get(1) }
        val frameworkMatrix = blocks.filterKeys { it.isFramework() && "compatibility_matrix" in it }.values
        val deviceMatrices = device.filterKeys { "compatibility_matrix" in it }.values
        return sepolicy(blocks) + listOfNotNull(level(level, blocks.lines("levels"))) +
            systemSdk(deviceMatrices, frameworkManifests) +
            missingHals("vendor's matrix", deviceMatrices, frameworkManifests, "the system's manifests") +
            missingHals("the system's level-$level matrix", frameworkMatrix, deviceManifests, "vendor's manifests")
    }

    private fun sepolicy(blocks: Map<String, String>): List<String> {
        val version = blocks.lines("vendor-sepolicy").firstOrNull()
        val mappings = blocks.lines("mapping").map { it.removeSuffix(".cil") }.toSet()
        return when {
            version == null -> listOf("VINTF_NOT_CHECKED: vendor/etc/selinux/plat_sepolicy_vers.txt is missing")

            version in mappings -> emptyList()

            else -> listOf(
                "VINTF_SEPOLICY: vendor's sepolicy is $version but system/etc/selinux/mapping has no $version.cil " +
                    "(it has ${mappings.sorted().joinToString().ifEmpty { "none" }}): init cannot build the " +
                    "SELinux policy and the phone does not boot. The system and vendor images must be of one release",
            )
        }
    }

    private fun level(level: String?, levels: List<String>): String? = when {
        level == null -> "VINTF_NOT_CHECKED: the device manifest has no target-level"

        level in levels -> null

        else -> {
            val have = levels.joinToString().ifEmpty { "none" }
            "VINTF_LEVEL: vendor targets FCM level $level but the system has matrices for $have: " +
                "Android warns of an internal problem at every boot"
        }
    }

    private fun systemSdk(deviceMatrices: Collection<String>, frameworkManifests: Collection<String>): List<String> {
        val wanted = deviceMatrices.flatMap { versions(it, "system-sdk") }.toSet()
        val offered = frameworkManifests.flatMap { versions(it, "system-sdk") }.toSet()
        val missing = (wanted - offered).sorted()
        if (missing.isEmpty()) return emptyList()
        return listOf(
            "VINTF_SYSTEM_SDK: vendor's matrix asks for system SDK ${missing.joinToString()}, which the system's " +
                "manifests do not offer (${offered.sorted().joinToString().ifEmpty { "none" }})",
        )
    }

    /** The non-optional HALs of [matrices] that no manifest of [manifests] declares, by format and name. */
    private fun missingHals(
        who: String,
        matrices: Collection<String>,
        manifests: Collection<String>,
        where: String,
    ): List<String> {
        val provided = manifests.flatMap { hals(it) }.map { it.format to it.name }.toSet()
        val missing = matrices.flatMap { hals(it) }.filter { !it.optional && (it.format to it.name) !in provided }
            .map { "${it.name} (${it.format})" }.distinct()
        if (missing.isEmpty()) return emptyList()
        val shown = missing.take(
            SHOWN,
        ).joinToString() + if (missing.size > SHOWN) " and ${missing.size - SHOWN} more" else ""
        return listOf("VINTF_HAL: $who requires $shown, which $where do not declare")
    }

    private data class Hal(val format: String, val name: String, val optional: Boolean)

    private fun hals(xml: String): List<Hal> = HAL.findAll(xml).mapNotNull { m ->
        val attrs = m.groupValues[1]
        val name = NAME.find(m.groupValues[2])?.groupValues?.get(1)?.trim() ?: return@mapNotNull null
        Hal(
            format = FORMAT.find(attrs)?.groupValues?.get(1) ?: "hidl",
            name = name,
            optional = OPTIONAL.find(attrs)?.groupValues?.get(1) == "true",
        )
    }.toList()

    private fun versions(xml: String, section: String): List<String> =
        Regex("<$section>(.*?)</$section>", RegexOption.DOT_MATCHES_ALL).findAll(xml)
            .flatMap { VERSION.findAll(it.groupValues[1]).map { v -> v.groupValues[1].trim() } }.toList()

    private fun Map<String, String>.lines(key: String): List<String> =
        this[key].orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }

    private fun String.isFramework(): Boolean =
        startsWith("system/") || startsWith("system_ext/") || startsWith("product/")

    private val COMMENT = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
    private val TARGET_LEVEL = Regex("""target-level="([^"]+)"""")
    private val HAL = Regex("""<hal\b([^>]*)>(.*?)</hal>""", RegexOption.DOT_MATCHES_ALL)
    private val NAME = Regex("<name>(.*?)</name>", RegexOption.DOT_MATCHES_ALL)
    private val FORMAT = Regex("""format="([^"]+)"""")
    private val OPTIONAL = Regex("""optional="([^"]+)"""")
    private val VERSION = Regex("<version>(.*?)</version>")
    private const val SHOWN = 5
}
