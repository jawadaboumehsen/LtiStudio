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
 * Whether the system side of work/ meets what the vendor side requires (Android's VINTF), judged by AOSP's own
 * `checkvintf --check-compat` (the vintf-tools group), as AOSP's release tools do before an OTA: HAL formats,
 * versions, interfaces and instances both ways (from the partitions' manifests and matrices, their fragments and
 * the APEXes' ones), the framework matrix for the vendor's target FCM level, sepolicy, VNDK and system SDK
 * versions, and the boot kernel's version and configuration against the kernel requirements. Every vendor and ODM
 * SKU manifest is checked. Plus what checkvintf does not look at: the SELinux mapping file for the vendor's sepolicy
 * version, without which init cannot build the policy and the phone does not boot.
 */
public object VintfCheck {
    /**
     * `$1` work/, `$2` checkvintf, `$3` unpack_bootimg, `$4` fsck.erofs. Prints `@@@ <section>` blocks: mapping,
     * vendor-sepolicy, then `tool-missing`, or `kernel <note>` and one `result <vendor sku>|<odm sku> <exit>` per
     * SKU pair followed by checkvintf's warnings and errors. The APEXes' etc/vintf (from their ext4 or erofs
     * payload) and the boot kernel's release and IKCONFIG are put in a temporary folder; links are not followed.
     */
    public const val SCRIPT: String = """w="$1"; cv=${'$'}(command -v "$2" 2>/dev/null); unpack="$3"; fsck="$4"
cd "${'$'}w" 2>/dev/null || exit 0
printf '@@@ mapping\n'; ls system/system/etc/selinux/mapping 2>/dev/null
printf '@@@ vendor-sepolicy\n'; cat vendor/etc/selinux/plat_sepolicy_vers.txt 2>/dev/null
if [ -z "${'$'}cv" ] || [ ! -x "${'$'}cv" ]; then printf '@@@ tool-missing\n'; exit 0; fi
t=${'$'}(mktemp -d) || exit 1; trap 'rm -rf "${'$'}t"' EXIT
a="${'$'}t/apex"; mkdir "${'$'}a" || exit 1
printf '<?xml version="1.0" encoding="utf-8"?>\n<apex-info-list>\n' > "${'$'}a/apex-info-list.xml"
for pair in system/system/apex:SYSTEM system_ext/apex:SYSTEM_EXT product/apex:PRODUCT vendor/apex:VENDOR odm/apex:ODM; do
  dir="${'$'}{pair%%:*}"; part="${'$'}{pair##*:}"
  [ -d "${'$'}dir" ] && [ ! -L "${'$'}dir" ] || continue
  for f in "${'$'}dir"/*.apex "${'$'}dir"/*.capex; do
    [ -f "${'$'}f" ] && [ ! -L "${'$'}f" ] || continue
    rm -rf "${'$'}t/x" && mkdir "${'$'}t/x" || exit 1
    p="${'$'}f"
    unzip -q -o "${'$'}f" original_apex -d "${'$'}t/x" 2>/dev/null && p="${'$'}t/x/original_apex"
    unzip -q -o "${'$'}p" apex_manifest.pb apex_payload.img -d "${'$'}t/x" 2>/dev/null || continue
    [ "${'$'}(od -An -tx1 -N1 "${'$'}t/x/apex_manifest.pb" | tr -d ' ')" = 0a ] || continue
    n=${'$'}(od -An -tu1 -j1 -N1 "${'$'}t/x/apex_manifest.pb" | tr -d ' ')
    name=${'$'}(dd if="${'$'}t/x/apex_manifest.pb" bs=1 skip=2 count="${'$'}n" 2>/dev/null)
    case "${'$'}name" in ''|*/*|.*) continue ;; esac
    [ -d "${'$'}a/${'$'}name" ] && continue
    img="${'$'}t/x/apex_payload.img"; mkdir -p "${'$'}a/${'$'}name/etc" || exit 1
    if [ "${'$'}(od -An -tx1 -j1080 -N2 "${'$'}img" | tr -d ' ')" = 53ef ]; then
      debugfs -R "rdump /etc/vintf ${'$'}a/${'$'}name/etc" "${'$'}img" >/dev/null 2>&1
    elif [ "${'$'}(od -An -tx1 -j1024 -N4 "${'$'}img" | tr -d ' ')" = e2e1f5e0 ]; then
      "${'$'}fsck" --extract="${'$'}t/x/root" "${'$'}img" >/dev/null 2>&1 && [ -d "${'$'}t/x/root/etc/vintf" ] &&
        cp -r "${'$'}t/x/root/etc/vintf" "${'$'}a/${'$'}name/etc/"
    fi
    printf '  <apex-info moduleName="%s" partition="%s" isActive="true"/>\n' "${'$'}name" "${'$'}part" \
      >> "${'$'}a/apex-info-list.xml"
  done
done
printf '</apex-info-list>\n' >> "${'$'}a/apex-info-list.xml"
kernel=""
if [ ! -f kernel/boot.img ]; then printf '@@@ kernel no boot image in work/kernel\n'
elif ! "${'$'}unpack" --boot_img kernel/boot.img --out "${'$'}t/boot" >/dev/null 2>&1 || [ ! -s "${'$'}t/boot/kernel" ]; then
  printf '@@@ kernel no kernel in boot.img\n'
else
  case "${'$'}(od -An -tx1 -N4 "${'$'}t/boot/kernel" | tr -d ' ')" in
    1f8b*) gzip -dc < "${'$'}t/boot/kernel" > "${'$'}t/Image" ;;
    02214c18) lz4 -dc "${'$'}t/boot/kernel" > "${'$'}t/Image" ;;
    *) cp "${'$'}t/boot/kernel" "${'$'}t/Image" ;;
  esac
  grep -a -o -m1 'Linux version [^ ]*' "${'$'}t/Image" | sed 's/^Linux version //' | tr -d '\n' > "${'$'}t/release"
  off=${'$'}(grep -a -b -o 'IKCFG_ST' "${'$'}t/Image" | head -n 1 | cut -d: -f1)
  [ -n "${'$'}off" ] && tail -c +${'$'}((off + 9)) "${'$'}t/Image" | gzip -dc > "${'$'}t/config" 2>/dev/null
  if [ -s "${'$'}t/release" ] && [ -s "${'$'}t/config" ]; then kernel="${'$'}t/release:${'$'}t/config"
    printf '@@@ kernel ok %s\n' "${'$'}(cat "${'$'}t/release")"
  else printf '@@@ kernel no embedded configuration (CONFIG_IKCONFIG) in the boot kernel\n'; fi
fi
vskus=${'$'}(ls vendor/etc/vintf 2>/dev/null | sed -n 's/^manifest_\(.*\)\.xml${'$'}/\1/p')
oskus=${'$'}(ls odm/etc/vintf 2>/dev/null | sed -n 's/^manifest_\(.*\)\.xml${'$'}/\1/p')
for v in ${'$'}{vskus:--}; do for o in ${'$'}{oskus:--}; do
  set -- --check-compat --dirmap /system:"${'$'}w/system/system" --dirmap /system_ext:"${'$'}w/system_ext" \
    --dirmap /product:"${'$'}w/product" --dirmap /vendor:"${'$'}w/vendor" --dirmap /odm:"${'$'}w/odm" --dirmap /apex:"${'$'}a"
  [ "${'$'}v" != - ] && set -- "${'$'}@" --property ro.boot.product.vendor.sku="${'$'}v"
  [ "${'$'}o" != - ] && set -- "${'$'}@" --property ro.boot.product.hardware.sku="${'$'}o"
  [ -n "${'$'}kernel" ] && set -- "${'$'}@" --kernel "${'$'}kernel"
  "${'$'}cv" "${'$'}@" > "${'$'}t/out" 2>&1; r=${'$'}?
  printf '@@@ result %s|%s %s\n' "${'$'}v" "${'$'}o" "${'$'}r"
  grep -v '\[INFO\]' "${'$'}t/out" | sed 's/\x1b\[[0-9;]*m//g' | grep -v '^COMPATIBLE${'$'}\|^INCOMPATIBLE${'$'}' | head -n 40
done; done
exit 0"""

    /** Not a refusal: what could not be judged. */
    public const val NOT_CHECKED: String = "VINTF_NOT_CHECKED"

    /** The build's refusals ([accepted]: an incompatibility was accepted on purpose; the boot stopper stays). */
    public fun refusals(text: String, accepted: Boolean): List<String> {
        val s = sections(text)
        val incompatible = s.results.filter { it.exit == INCOMPATIBLE_EXIT }.map { result ->
            "VINTF_INCOMPATIBLE: the system does not meet the vendor's VINTF requirements${result.skuLabel()}: " +
                "${result.reason()}. Android warns \"There's an internal problem with your device\" at every boot " +
                "and refuses an OTA. Fix what it names, or accept it"
        }
        return listOfNotNull(sepolicy(s)) + if (accepted) emptyList() else incompatible
    }

    /** What could not be judged, for the Preflight (never a refusal). */
    public fun notes(text: String): List<String> {
        val s = sections(text)
        if (s.toolMissing) {
            return listOf(
                "$NOT_CHECKED: checkvintf is not installed (Setup > Tools > vintf-tools); only the SELinux " +
                    "mapping was checked",
            )
        }
        val kernel = s.kernel?.takeUnless { it.startsWith("ok") }?.let {
            "$NOT_CHECKED: the kernel requirements were not checked: $it"
        }
        val noSepolicy = "$NOT_CHECKED: vendor/etc/selinux/plat_sepolicy_vers.txt is missing"
        val failed = s.results.filter { it.exit != 0 && it.exit != INCOMPATIBLE_EXIT }.map {
            "$NOT_CHECKED: checkvintf could not finish${it.skuLabel()} (exit ${it.exit}): ${it.reason()}"
        }
        return listOfNotNull(kernel, noSepolicy.takeIf { s.vendorSepolicy == null }) + failed
    }

    private fun sepolicy(s: Sections): String? {
        val version = s.vendorSepolicy
        if (version == null || version in s.mappings) return null
        return "VINTF_SEPOLICY: vendor's sepolicy is $version but system/etc/selinux/mapping has no $version.cil " +
            "(it has ${s.mappings.sorted().joinToString().ifEmpty { "none" }}): init cannot build the SELinux " +
            "policy and the phone does not boot. The system and vendor images must be of one release"
    }

    private data class Result(val vendorSku: String, val odmSku: String, val exit: Int, val lines: List<String>) {
        fun skuLabel(): String {
            val skus = listOfNotNull(
                vendorSku.takeIf { it != NO_SKU }?.let { "vendor SKU $it" },
                odmSku.takeIf { it != NO_SKU }?.let { "ODM SKU $it" },
            )
            return if (skus.isEmpty()) "" else skus.joinToString(prefix = " (", postfix = ")")
        }

        /** checkvintf's error, without its framing ("ERROR: files are incompatible:", a trailing ": Success"). */
        fun reason(): String {
            val error = lines.dropWhile { !it.startsWith("ERROR:") }.ifEmpty { lines }
            return error.joinToString(" ").removePrefix("ERROR:").trim()
                .removePrefix("files are incompatible:").trim().removeSuffix(": Success").trim()
                .ifEmpty { "no reason given" }
        }
    }

    private data class Sections(
        val mappings: Set<String>,
        val vendorSepolicy: String?,
        val toolMissing: Boolean,
        val kernel: String?,
        val results: List<Result>,
    )

    private fun sections(text: String): Sections {
        val blocks = text.split("@@@ ").filter { it.isNotBlank() }.map { block ->
            block.substringBefore('\n').trim() to block.substringAfter('\n', "").lines()
                .map { it.trim() }.filter { it.isNotEmpty() }
        }
        val results = blocks.filter { it.first.startsWith("result ") }.mapNotNull { (head, lines) ->
            val parts = head.removePrefix("result ").split(' ')
            val skus = parts.getOrNull(0)?.split('|')
            val exit = parts.getOrNull(1)?.toIntOrNull()
            if (skus?.size == 2 && exit != null) Result(skus[0], skus[1], exit, lines) else null
        }
        val map = blocks.toMap()
        return Sections(
            mappings = map["mapping"].orEmpty().map { it.removeSuffix(".cil") }.toSet(),
            vendorSepolicy = map["vendor-sepolicy"]?.firstOrNull(),
            toolMissing = "tool-missing" in map,
            kernel = blocks.firstOrNull { it.first.startsWith("kernel ") }?.first?.removePrefix("kernel "),
            results = results,
        )
    }

    /** checkvintf's exit when the files are incompatible (EX_DATAERR); 0 is compatible, anything else an error. */
    private const val INCOMPATIBLE_EXIT = 65
    private const val NO_SKU = "-"
}
