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

public enum class VintfStatus(public val label: String) {
    COMPATIBLE("Compatible"),
    INCOMPATIBLE("Incompatible"),
    INCOMPLETE("Incomplete"),
    NOT_RUN("Not run"),
}

public data class VintfAssessment(val status: VintfStatus, val missingEvidence: List<String> = emptyList())

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
     * Args are work/, checkvintf, unpack_bootimg, fsck.erofs and any selected partitions.
     * Prints section headers for mapping, vendor-sepolicy, a missing tool, kernel status and one result per
     * SKU pair followed by checkvintf's warnings and errors. The APEXes' etc/vintf (from their ext4 or erofs
     * payload) and the boot kernel's release and IKCONFIG are put in a temporary folder. Missing inputs are listed
     * in `missing` blocks and `done` records the number of SKU results written.
     */
    public const val SCRIPT: String = """w="$1"; cv=${'$'}(command -v "$2" 2>/dev/null); unpack="$3"; fsck="$4"
cd "${'$'}w" 2>/dev/null || { printf '@@@ not-run Assemble must create work/ first.\n'; exit 0; }
w=${'$'}(pwd -P) || exit 1
# Use the same layout for manifests, SKU discovery, APEXes and checker arguments.
partition() {
  for candidate; do
    [ -d "${'$'}candidate" ] && [ ! -L "${'$'}candidate" ] || continue
    resolved=${'$'}(realpath -e -- "${'$'}candidate") || continue
    [ "${'$'}resolved" = "${'$'}w/${'$'}candidate" ] || continue
    printf '%s' "${'$'}candidate"
    return
  done
}
system=${'$'}(partition system/system system)
vendor=${'$'}(partition vendor "${'$'}system/vendor")
odm=${'$'}(partition odm "${'$'}vendor/odm" "${'$'}system/vendor/odm")
product=${'$'}(partition product "${'$'}system/product")
system_ext=${'$'}(partition system_ext "${'$'}system/system_ext")
need() { printf '@@@ missing %s\n' "${'$'}*"; }
if [ -z "${'$'}system" ] || [ -z "${'$'}vendor" ]; then
  [ -n "${'$'}system" ] || need 'The assembled /system partition is missing. Run Assemble.'
  [ -n "${'$'}vendor" ] || need 'The assembled /vendor partition is missing. Run Assemble.'
  printf '@@@ not-run System and vendor are required to start checkvintf.\n'
  exit 0
fi
printf '@@@ mapping\n'; ls "${'$'}system/etc/selinux/mapping" 2>/dev/null
printf '@@@ vendor-sepolicy\n'; cat "${'$'}vendor/etc/selinux/plat_sepolicy_vers.txt" 2>/dev/null
if [ -z "${'$'}cv" ] || [ ! -x "${'$'}cv" ]; then printf '@@@ tool-missing\n'; exit 0; fi
shift 4
for expected; do
  case "${'$'}expected" in
    product) actual="${'$'}product" ;; system_ext) actual="${'$'}system_ext" ;; odm) actual="${'$'}odm" ;;
    *) continue ;;
  esac
  [ -n "${'$'}actual" ] || need "The selected /${'$'}expected partition has no readable VINTF input directory."
done
api=${'$'}(sed -n 's/^ro\.product\.first_api_level=\([0-9][0-9]*\)[[:space:]]*${'$'}/\1/p' "${'$'}vendor/build.prop" 2>/dev/null | head -n 1)
[ -n "${'$'}api" ] || need 'ro.product.first_api_level is missing or invalid in the vendor build.prop.'
t=${'$'}(mktemp -d) || exit 1; trap 'rm -rf "${'$'}t"' EXIT
a="${'$'}t/apex"; mkdir "${'$'}a" "${'$'}t/empty" || exit 1
printf '<?xml version="1.0" encoding="utf-8"?>\n<apex-info-list>\n' > "${'$'}a/apex-info-list.xml" || exit 1
apex() {
  f="$1"; part="$2"
  rm -rf "${'$'}t/x" && mkdir "${'$'}t/x" || return 1
  p="${'$'}f"
  entries=${'$'}(unzip -Z1 "${'$'}f" 2>/dev/null) || return 1
  if printf '%s\n' "${'$'}entries" | grep -Fqx original_apex; then
    unzip -q "${'$'}f" original_apex -d "${'$'}t/x" 2>/dev/null || return 1
    p="${'$'}t/x/original_apex"
  fi
  unzip -q "${'$'}p" apex_manifest.pb apex_payload.img -d "${'$'}t/x" 2>/dev/null || return 1
  [ "${'$'}(od -An -tx1 -N1 "${'$'}t/x/apex_manifest.pb" | tr -d ' ')" = 0a ] || return 1
  n=${'$'}(od -An -tu1 -j1 -N1 "${'$'}t/x/apex_manifest.pb" | tr -d ' ')
  [ -n "${'$'}n" ] && [ "${'$'}n" -lt 128 ] || return 1
  name=${'$'}(dd if="${'$'}t/x/apex_manifest.pb" bs=1 skip=2 count="${'$'}n" 2>/dev/null)
  case "${'$'}name" in ''|.*|*[!a-zA-Z0-9_.-]*) return 1 ;; esac
  [ ! -d "${'$'}a/${'$'}name" ] || { need "APEX ${'$'}f duplicates module ${'$'}name. The active copy is unknown."; return 0; }
  img="${'$'}t/x/apex_payload.img"
  mkdir -p "${'$'}a/${'$'}name/etc" || return 1
  if [ "${'$'}(od -An -tx1 -j1080 -N2 "${'$'}img" | tr -d ' ')" = 53ef ]; then
    command -v debugfs >/dev/null 2>&1 || return 1
    probe=${'$'}(LC_ALL=C debugfs -R 'stat /etc/vintf' "${'$'}img" 2>&1)
    case "${'$'}probe" in
      *'Inode:'*) debugfs -R "rdump /etc/vintf ${'$'}a/${'$'}name/etc" "${'$'}img" >/dev/null 2>&1 &&
        [ -d "${'$'}a/${'$'}name/etc/vintf" ] || return 1 ;;
      *'/etc/vintf: File not found'*) : ;;
      *) return 1 ;;
    esac
  elif [ "${'$'}(od -An -tx1 -j1024 -N4 "${'$'}img" | tr -d ' ')" = e2e1f5e0 ]; then
    "${'$'}fsck" --extract="${'$'}t/x/root" "${'$'}img" >/dev/null 2>&1 || return 1
    if [ -d "${'$'}t/x/root/etc/vintf" ]; then
      cp -r "${'$'}t/x/root/etc/vintf" "${'$'}a/${'$'}name/etc/" || return 1
    fi
  else
    return 1
  fi
  printf '  <apex-info moduleName="%s" partition="%s" isActive="true"/>\n' "${'$'}name" "${'$'}part" >> "${'$'}a/apex-info-list.xml"
}
for part in SYSTEM SYSTEM_EXT PRODUCT VENDOR ODM; do
  case "${'$'}part" in
    SYSTEM) dir="${'$'}system" ;; SYSTEM_EXT) dir="${'$'}system_ext" ;; PRODUCT) dir="${'$'}product" ;;
    VENDOR) dir="${'$'}vendor" ;; ODM) dir="${'$'}odm" ;;
  esac
  [ -n "${'$'}dir" ] || continue
  if [ -L "${'$'}dir/apex" ]; then
    need "APEX directory ${'$'}dir/apex is a link and its VINTF fragments could not be checked."
    continue
  fi
  [ -e "${'$'}dir/apex" ] || continue
  if [ ! -d "${'$'}dir/apex" ] || [ ! -r "${'$'}dir/apex" ] || [ ! -x "${'$'}dir/apex" ]; then
    need "Cannot read APEX directory ${'$'}dir/apex."
    continue
  fi
  for f in "${'$'}dir/apex/"*.apex "${'$'}dir/apex/"*.capex; do
    if [ -L "${'$'}f" ]; then
      need "APEX ${'$'}f is a link and its VINTF fragments could not be checked."
    elif [ -f "${'$'}f" ]; then
      apex "${'$'}f" "${'$'}part" || need "Could not read VINTF fragments from APEX ${'$'}f. Check its archive and payload."
    fi
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
skus() {
  if [ -n "$1" ]; then
    [ ! -f "$1/etc/vintf/manifest.xml" ] || printf '%s\n' -
    for manifest in "$1/etc/vintf/"manifest_*.xml; do
      [ -f "${'$'}manifest" ] || continue
      sku=${'$'}{manifest##*/manifest_}
      sku=${'$'}{sku%.xml}
      case "${'$'}sku" in ''|*[!a-zA-Z0-9_.-]*) continue ;; esac
      printf '%s\n' "${'$'}sku"
    done
  fi
}
vskus=${'$'}(skus "${'$'}vendor")
oskus=${'$'}(skus "${'$'}odm")
count=0
mapped() {
  if [ -n "$1" ]; then printf '%s/%s' "${'$'}w" "$1"; else printf '%s' "${'$'}t/empty"; fi
}
for v in ${'$'}{vskus:--}; do for o in ${'$'}{oskus:--}; do
  set -- --check-compat --dirmap "/system:${'$'}w/${'$'}system" --dirmap "/vendor:${'$'}w/${'$'}vendor" --dirmap "/apex:${'$'}a"
  # checkvintf needs a mapping to distinguish an absent optional file from an unresolved path.
  set -- "${'$'}@" --dirmap "/system_ext:${'$'}(mapped "${'$'}system_ext")"
  set -- "${'$'}@" --dirmap "/product:${'$'}(mapped "${'$'}product")"
  set -- "${'$'}@" --dirmap "/odm:${'$'}(mapped "${'$'}odm")"
  [ -z "${'$'}api" ] || set -- "${'$'}@" --property "ro.product.first_api_level=${'$'}api"
  [ "${'$'}v" != - ] && set -- "${'$'}@" --property ro.boot.product.vendor.sku="${'$'}v"
  [ "${'$'}o" != - ] && set -- "${'$'}@" --property ro.boot.product.hardware.sku="${'$'}o"
  [ -n "${'$'}kernel" ] && set -- "${'$'}@" --kernel "${'$'}kernel"
  "${'$'}cv" "${'$'}@" > "${'$'}t/out" 2>&1; r=${'$'}?
  printf '@@@ result %s|%s %s\n' "${'$'}v" "${'$'}o" "${'$'}r"
  grep -v '\[INFO\]' "${'$'}t/out" | sed 's/\x1b\[[0-9;]*m//g; s/^@@@ /checker: /' | grep -v '^COMPATIBLE${'$'}\|^INCOMPATIBLE${'$'}' | head -n 40
  count=${'$'}((count + 1))
done; done
printf '@@@ done %s\n' "${'$'}count"
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

    public fun assess(text: String?, exitCode: Int = 0): VintfAssessment {
        if (text == null) return VintfAssessment(VintfStatus.NOT_RUN, listOf("Run Preflight to check VINTF."))
        val s = sections(text)
        val missing = missingEvidence(s) + listOfNotNull(
            "The VINTF input scan stopped (exit $exitCode).".takeIf { exitCode != 0 },
        )
        val status = when {
            sepolicy(s) != null || s.results.any { it.exit == INCOMPATIBLE_EXIT } -> VintfStatus.INCOMPATIBLE
            s.toolMissing || s.notRun != null -> VintfStatus.NOT_RUN
            missing.isNotEmpty() -> VintfStatus.INCOMPLETE
            else -> VintfStatus.COMPATIBLE
        }
        return VintfAssessment(status, missing)
    }

    /** What could not be judged, for the Preflight (never a refusal). */
    public fun notes(text: String): List<String> = assess(text).missingEvidence.map { "$NOT_CHECKED: $it" }

    private fun missingEvidence(s: Sections): List<String> = buildList {
        addAll(s.missing)
        if (s.notRun == null && s.vendorSepolicy == null) {
            add("vendor/etc/selinux/plat_sepolicy_vers.txt is missing or unreadable")
        }
        if (s.toolMissing) add("checkvintf is not installed (Setup > Tools > vintf-tools)")
        s.notRun?.let(::add)
        if (!s.toolMissing && s.notRun == null) {
            val kernel = s.kernel
            if (kernel == null) {
                add("No kernel check evidence was recorded.")
            } else if (!kernel.startsWith("ok ")) {
                add("the kernel requirements were not checked: $kernel")
            }
            if (s.results.isEmpty()) add("checkvintf returned no SKU results.")
            if (s.completed != s.results.size) add("The VINTF check did not record a complete set of results.")
            s.results.filter { it.exit != 0 && it.exit != INCOMPATIBLE_EXIT }.forEach {
                add("checkvintf could not finish${it.skuLabel()} (exit ${it.exit}): ${it.reason()}")
            }
        }
    }

    private fun sepolicy(s: Sections): String? {
        val version = s.vendorSepolicy
        if (s.notRun != null || version == null || version in s.mappings) return null
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
        val missing: List<String>,
        val notRun: String?,
        val completed: Int?,
    )

    private fun sections(text: String): Sections {
        val blocks = text.split(Regex("(?m)^@@@ ")).filter { it.isNotBlank() }.map { block ->
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
            missing = blocks.map { it.first }.filter { it.startsWith("missing ") }.map { it.removePrefix("missing ") },
            notRun = blocks.firstOrNull { it.first.startsWith("not-run ") }?.first?.removePrefix("not-run "),
            completed = blocks.lastOrNull { it.first.startsWith("done ") }?.first?.removePrefix("done ")?.toIntOrNull(),
        )
    }

    /** checkvintf's exit when the files are incompatible (EX_DATAERR); 0 is compatible, anything else an error. */
    private const val INCOMPATIBLE_EXIT = 65
    private const val NO_SKU = "-"
}
