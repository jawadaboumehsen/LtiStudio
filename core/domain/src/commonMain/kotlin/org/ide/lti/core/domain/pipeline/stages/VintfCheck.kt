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

import org.ide.lti.core.domain.pipeline.CacheKeys
import org.ide.lti.core.model.workspace.ConfigurationSnapshot

public enum class VintfStatus(public val label: String) {
    COMPATIBLE("Compatible"),
    INCOMPATIBLE("Incompatible"),
    INCOMPLETE("Incomplete"),
    NOT_RUN("Not run"),
}

/** The input to inspect or restore to complete a compatibility check. */
public enum class VintfEvidenceKind { ASSEMBLY, PARTITION, FIRMWARE_METADATA, APEX, KERNEL, TOOL, SCAN }

public data class VintfEvidence(val message: String, val kind: VintfEvidenceKind)

public data class VintfSkuResult(
    val vendorSku: String?,
    val odmSku: String?,
    val status: VintfStatus,
    val provisional: Boolean = false,
)

public data class VintfAssessment(
    val status: VintfStatus,
    val evidence: List<VintfEvidence> = emptyList(),
    val androidRelease: String? = null,
    val firstApiLevel: Int? = null,
    val skuResults: List<VintfSkuResult> = emptyList(),
) {
    public val missingEvidence: List<String> get() = evidence.map { it.message }
}

/**
 * Whether the system side of work/ meets what the vendor side requires (Android's VINTF), judged by AOSP's own
 * `checkvintf --check-compat` (the vintf-tools group), as AOSP's release tools do before an OTA: HAL formats,
 * versions, interfaces and instances both ways, the framework matrix for the vendor's target FCM level, sepolicy,
 * VNDK, system SDK and kernel requirements. SKU declarations from target-files metadata determine the supported
 * combinations. Named variants inferred from manifests provide provisional results. The scan also checks the
 * SELinux mapping file for the vendor's sepolicy
 * version, without which init cannot build the policy and the phone does not boot.
 */
public object VintfCheck {
    /**
     * Args are work/, checkvintf, unpack_bootimg, fsck.erofs, SKU source options, and selected partitions.
     * Prints section headers for mapping, vendor-sepolicy, a missing tool, kernel status and one result per
     * SKU pair followed by checkvintf's warnings and errors. The APEXes' etc/vintf (from their ext4 or erofs
     * payload) and the boot kernel's release and IKCONFIG are put in a temporary folder. Missing inputs are listed
     * in `missing` blocks and `done` records the number of SKU results written.
     */
    public const val SCRIPT: String = """w="${'$'}1"; cv=${'$'}(command -v "${'$'}2" 2>/dev/null); unpack="${'$'}3"; fsck="${'$'}4"
shift 4
archive=""
sku_source_changed=false
while [ "${'$'}#" -gt 0 ]; do
  case "${'$'}1" in
    --firmware-archive) [ "${'$'}#" -ge 2 ] || exit 1; archive="${'$'}2"; shift 2 ;;
    --sku-source-changed) sku_source_changed=true; shift ;;
    *) break ;;
  esac
done
cd "${'$'}w" 2>/dev/null || { printf '@@@ not-run Assemble must create work/ first.\n'; exit 0; }
w=${'$'}(pwd -P) || exit 1
# Use the same layout for manifests, SKU discovery, APEXes and checker arguments.
partition() {
  fallback=""
  for candidate; do
    [ -d "${'$'}candidate" ] && [ ! -L "${'$'}candidate" ] || continue
    resolved=${'$'}(realpath -e -- "${'$'}candidate") || continue
    [ "${'$'}resolved" = "${'$'}w/${'$'}candidate" ] || continue
    [ -n "${'$'}fallback" ] || fallback="${'$'}candidate"
    # An empty mount point must not hide a populated folded tree.
    if [ ! -r "${'$'}candidate" ] || [ ! -x "${'$'}candidate" ] ||
      [ -n "${'$'}(find "${'$'}candidate" -mindepth 1 -maxdepth 1 -print -quit)" ]; then
      printf '%s' "${'$'}candidate"
      return
    fi
  done
  printf '%s' "${'$'}fallback"
}
system=${'$'}(partition system/system system)
vendor=${'$'}(partition vendor "${'$'}system/vendor")
odm=${'$'}(partition odm "${'$'}vendor/odm" "${'$'}system/vendor/odm")
product=${'$'}(partition product "${'$'}system/product")
system_ext=${'$'}(partition system_ext "${'$'}system/system_ext")
need() { printf '@@@ missing %s|%s\n' "$1" "$2"; }
if [ -n "${'$'}system" ]; then
  for prop in "${'$'}system/build.prop" "${'$'}system/etc/build.prop"; do
    [ -f "${'$'}prop" ] && [ "${'$'}(realpath -e -- "${'$'}prop")" = "${'$'}w/${'$'}prop" ] || continue
    android=${'$'}(sed -n 's/^ro\.build\.version\.release=\([0-9][0-9A-Za-z._-]*\)[[:space:]]*${'$'}/\1/p' "${'$'}prop" | head -n 1)
    [ -n "${'$'}android" ] || continue
    printf '@@@ android %s\n' "${'$'}android"
    break
  done
fi
if [ -z "${'$'}system" ] || [ -z "${'$'}vendor" ]; then
  [ -n "${'$'}system" ] || need assembly 'The assembled /system partition is missing. Run Assemble.'
  [ -n "${'$'}vendor" ] || need assembly 'The assembled /vendor partition is missing. Run Assemble.'
  printf '@@@ not-run System and vendor are required to start checkvintf.\n'
  exit 0
fi
printf '@@@ mapping\n'; ls "${'$'}system/etc/selinux/mapping" 2>/dev/null
printf '@@@ vendor-sepolicy\n'; cat "${'$'}vendor/etc/selinux/plat_sepolicy_vers.txt" 2>/dev/null
partitions_missing=false
for expected; do
  case "${'$'}expected" in
    product) actual="${'$'}product" ;; system_ext) actual="${'$'}system_ext" ;; odm) actual="${'$'}odm" ;;
    *) continue ;;
  esac
  if [ -z "${'$'}actual" ] || [ ! -r "${'$'}actual" ] || [ ! -x "${'$'}actual" ] ||
    [ -z "${'$'}(find "${'$'}actual" -mindepth 1 -maxdepth 1 -print -quit)" ]; then
    need partition "The selected /${'$'}expected partition has no readable VINTF input directory."
    partitions_missing=true
  fi
done
if [ -z "${'$'}cv" ] || [ ! -x "${'$'}cv" ]; then printf '@@@ tool-missing\n'; exit 0; fi
api=${'$'}(sed -n 's/^ro\.product\.first_api_level=\([0-9][0-9]*\)[[:space:]]*${'$'}/\1/p' "${'$'}vendor/build.prop" 2>/dev/null |
  awk '${'$'}1 > 0 && ${'$'}1 <= 2147483647 { print; exit }')
[ -n "${'$'}api" ] || need firmware 'ro.product.first_api_level is missing or invalid in the vendor build.prop.'
[ -z "${'$'}api" ] || printf '@@@ first-api %s\n' "${'$'}api"
t=${'$'}(mktemp -d) || exit 1; trap 'rm -rf "${'$'}t"' EXIT
a="${'$'}t/apex"; mkdir "${'$'}a" "${'$'}t/empty" || exit 1
printf '<?xml version="1.0" encoding="utf-8"?>\n<apex-info-list>\n' > "${'$'}a/apex-info-list.xml" || exit 1
apex_name() {
  manifest="${'$'}1"
  case "${'$'}manifest" in
    *.json)
      python3 -c '
import json, sys
with open(sys.argv[1], "rb") as source:
    data = source.read(65537)
if len(data) > 65536:
    sys.exit(1)
name = json.loads(data)["name"]
if not isinstance(name, str):
    sys.exit(1)
print(name)
' "${'$'}manifest" ;;
    *)
      [ "${'$'}(od -An -tx1 -N1 "${'$'}manifest" | tr -d ' ')" = 0a ] || return 1
      n=${'$'}(od -An -tu1 -j1 -N1 "${'$'}manifest" | tr -d ' ')
      [ -n "${'$'}n" ] && [ "${'$'}n" -lt 128 ] || return 1
      dd if="${'$'}manifest" bs=1 skip=2 count="${'$'}n" 2>/dev/null ;;
  esac
}
register_apex() {
  printf '  <apex-info moduleName="%s" partition="%s" isActive="true"/>\n' "${'$'}name" "${'$'}part" >> "${'$'}a/apex-info-list.xml"
}
valid_apex_name() {
  case "${'$'}1" in ''|.*|*[!a-zA-Z0-9_.-]*) return 1 ;; esac
}
flattened_apex() {
  f="${'$'}1"; part="${'$'}2"
  manifest="${'$'}f/apex_manifest.pb"
  if [ ! -e "${'$'}manifest" ]; then manifest="${'$'}f/apex_manifest.json"; fi
  [ -f "${'$'}manifest" ] && [ ! -L "${'$'}manifest" ] || return 1
  name=${'$'}(apex_name "${'$'}manifest" 2>/dev/null) && valid_apex_name "${'$'}name" || return 1
  if [ -d "${'$'}a/${'$'}name" ]; then
    need apex "APEX ${'$'}f duplicates module ${'$'}name. The active copy is unknown."
    return 0
  fi
  if [ -d "${'$'}f/etc" ] && { [ ! -r "${'$'}f/etc" ] || [ ! -x "${'$'}f/etc" ]; }; then return 1; fi
  if [ -e "${'$'}f/etc/vintf" ] || [ -L "${'$'}f/etc/vintf" ]; then
    [ "${'$'}(realpath -e -- "${'$'}f/etc/vintf")" = "${'$'}w/${'$'}f/etc/vintf" ] &&
      [ -d "${'$'}f/etc/vintf" ] && [ -r "${'$'}f/etc/vintf" ] && [ -x "${'$'}f/etc/vintf" ] &&
      [ -z "${'$'}(find "${'$'}f/etc/vintf" -type l -print -quit)" ] &&
      mkdir -p "${'$'}a/${'$'}name/etc" && cp -r "${'$'}f/etc/vintf" "${'$'}a/${'$'}name/etc/" || return 1
  else
    mkdir -p "${'$'}a/${'$'}name/etc" || return 1
  fi
  register_apex
}
apex() {
  f="${'$'}1"; part="${'$'}2"
  rm -rf "${'$'}t/x" && mkdir "${'$'}t/x" || return 1
  p="${'$'}f"
  entries=${'$'}(unzip -Z1 "${'$'}f" 2>/dev/null) || return 1
  if printf '%s\n' "${'$'}entries" | grep -Fqx original_apex; then
    unzip -q "${'$'}f" original_apex -d "${'$'}t/x" 2>/dev/null || return 1
    p="${'$'}t/x/original_apex"
  fi
  unzip -q "${'$'}p" apex_manifest.pb apex_payload.img -d "${'$'}t/x" 2>/dev/null || return 1
  name=${'$'}(apex_name "${'$'}t/x/apex_manifest.pb") && valid_apex_name "${'$'}name" || return 1
  [ ! -d "${'$'}a/${'$'}name" ] || { need apex "APEX ${'$'}f duplicates module ${'$'}name. The active copy is unknown."; return 0; }
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
  register_apex
}
for part in SYSTEM SYSTEM_EXT PRODUCT VENDOR ODM; do
  case "${'$'}part" in
    SYSTEM) dir="${'$'}system" ;; SYSTEM_EXT) dir="${'$'}system_ext" ;; PRODUCT) dir="${'$'}product" ;;
    VENDOR) dir="${'$'}vendor" ;; ODM) dir="${'$'}odm" ;;
  esac
  [ -n "${'$'}dir" ] || continue
  if [ -L "${'$'}dir/apex" ]; then
    need apex "APEX directory ${'$'}dir/apex is a link and its VINTF fragments could not be checked."
    continue
  fi
  [ -e "${'$'}dir/apex" ] || continue
  if [ ! -d "${'$'}dir/apex" ] || [ ! -r "${'$'}dir/apex" ] || [ ! -x "${'$'}dir/apex" ]; then
    need apex "Cannot read APEX directory ${'$'}dir/apex."
    continue
  fi
  for f in "${'$'}dir/apex/"*; do
    [ -e "${'$'}f" ] || [ -L "${'$'}f" ] || continue
    if [ -L "${'$'}f" ]; then
      need apex "APEX ${'$'}f is a link and its VINTF fragments could not be checked."
    elif [ -d "${'$'}f" ]; then
      if [ ! -r "${'$'}f" ] || [ ! -x "${'$'}f" ] || [ -e "${'$'}f/apex_manifest.pb" ] ||
        [ -e "${'$'}f/apex_manifest.json" ] || [ -e "${'$'}f/etc/vintf" ] || [ -L "${'$'}f/etc/vintf" ]; then
        flattened_apex "${'$'}f" "${'$'}part" || need apex "Could not read VINTF fragments from flattened APEX ${'$'}f."
      fi
    elif [ -f "${'$'}f" ]; then
      case "${'$'}f" in
        *.apex|*.capex)
          apex "${'$'}f" "${'$'}part" || need apex "Could not read VINTF fragments from APEX ${'$'}f. Check its archive and payload." ;;
      esac
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
sku_directory() {
  for path; do
    if [ -e "${'$'}path" ] || [ -L "${'$'}path" ]; then
      if [ ! -d "${'$'}path" ] || [ ! -r "${'$'}path" ] || [ ! -x "${'$'}path" ]; then
        need firmware "Cannot read SKU manifest directory ${'$'}path." >&3
        touch "${'$'}t/sku-scan-incomplete"
        return 1
      fi
    fi
  done
}
sku_file() {
  if [ -f "${'$'}1" ] && [ -r "${'$'}1" ]; then return 0; fi
  if [ -e "${'$'}1" ] || [ -L "${'$'}1" ]; then
    need firmware "Cannot read SKU manifest ${'$'}1." >&3
    touch "${'$'}t/sku-scan-incomplete"
  fi
  return 1
}
named_skus() {
  for manifest in "${'$'}1/"manifest_*.xml; do
    sku_file "${'$'}manifest" || continue
    sku=${'$'}{manifest##*/manifest_}
    sku=${'$'}{sku%.xml}
    case "${'$'}sku" in
      ''|*[!a-zA-Z0-9_.-]*)
        need firmware "Cannot use SKU name from ${'$'}manifest." >&3
        touch "${'$'}t/sku-scan-incomplete" ;;
      *) printf '%s\n' "${'$'}sku" ;;
    esac
  done
}
vendor_skus() {
  sku_directory "${'$'}vendor" "${'$'}vendor/etc" "${'$'}vendor/etc/vintf" || return
  if sku_file "${'$'}vendor/etc/vintf/manifest.xml" || sku_file "${'$'}vendor/manifest.xml"; then printf '~\n'; fi
  named_skus "${'$'}vendor/etc/vintf"
}
odm_skus() {
  [ -n "${'$'}odm" ] || return 0
  sku_directory "${'$'}odm" "${'$'}odm/etc" "${'$'}odm/etc/vintf" || return
  named_skus "${'$'}odm/etc/vintf"
  # libvintf tries the modern default before any legacy name or legacy default.
  if sku_file "${'$'}odm/etc/vintf/manifest.xml"; then
    printf '~\n'
  else
    if sku_file "${'$'}odm/etc/manifest.xml"; then printf '~\n'; fi
    named_skus "${'$'}odm/etc"
  fi
}
# AOSP releasetools uses the target-files declarations when available.
sku_metadata() {
  LC_ALL=C awk '
    {
      sub(/\r${'$'}/, "")
      eq = index(${'$'}0, "=")
      if (!eq) next
      key = substr(${'$'}0, 1, eq - 1)
      if (key !~ /^vintf_(vendor_manifest_skus|odm_manifest_skus|include_empty_vendor_sku|include_empty_odm_sku)${'$'}/) next
      value = substr(${'$'}0, eq + 1)
      if (key in values) bad = 1
      values[key] = value
      found = 1
    }
    function sku_list(axis,   list, empty, key) {
      list = values["vintf_" axis "_manifest_skus"]
      empty = values["vintf_include_empty_" axis "_sku"]
      if (list ~ /[^a-zA-Z0-9_. \t-]/ || (empty != "" && empty != "true" && empty != "false")) bad = 1
      gsub(/^[ \t]+|[ \t]+${'$'}/, "", list)
      if (list == "") return "~"
      if (empty == "true") return list " ~"
      return list
    }
    END {
      if (!found) exit
      vendor = sku_list("vendor")
      odm = sku_list("odm")
      if (bad) exit 1
      print vendor
      print odm
    }
  ' "${'$'}1"
}
exec 3>&1
vskus=${'$'}(vendor_skus | LC_ALL=C sort -u)
oskus=${'$'}(odm_skus | LC_ALL=C sort -u)
exec 3>&-
sku_list_has() {
  printf '%s\n' "${'$'}1" | grep -Fxq -e "${'$'}2"
}
archive_metadata_problem() {
  need firmware "Cannot read VINTF SKU declarations from META/misc_info.txt in the firmware archive."
  touch "${'$'}t/sku-scan-incomplete"
}
declared=""
if [ "${'$'}sku_source_changed" = true ]; then
  need firmware 'The vendor or ODM image was replaced. SKU declarations from the original archive do not apply.'
  touch "${'$'}t/sku-scan-incomplete"
elif [ -n "${'$'}archive" ] && { [ -e "${'$'}archive" ] || [ -L "${'$'}archive" ]; }; then
  if [ ! -f "${'$'}archive" ] || [ ! -r "${'$'}archive" ]; then
    archive_metadata_problem
  else
    listing=${'$'}(LC_ALL=C unzip -l "${'$'}archive" META/misc_info.txt 2>/dev/null)
    case "${'$'}?" in
      0)
        metadata_size=${'$'}(printf '%s\n' "${'$'}listing" |
          awk '${'$'}NF == "META/misc_info.txt" && ${'$'}1 ~ /^[0-9]+${'$'}/ { print ${'$'}1 }')
        if ! [ "${'$'}metadata_size" -le 65536 ] 2>/dev/null ||
          ! (ulimit -f 128 && unzip -p "${'$'}archive" META/misc_info.txt > "${'$'}t/misc_info") 2>/dev/null ||
          ! declared=${'$'}(sku_metadata "${'$'}t/misc_info"); then
          archive_metadata_problem
        fi ;;
      11) : ;; # A readable ZIP with no matching member.
      *) archive_metadata_problem ;;
    esac
  fi
fi
if [ -n "${'$'}declared" ]; then
  declared_vendor=${'$'}(printf '%s\n' "${'$'}declared" | sed -n '1p' | tr ' \t' '\n' | LC_ALL=C sort -u)
  declared_odm=${'$'}(printf '%s\n' "${'$'}declared" | sed -n '2p' | tr ' \t' '\n' | LC_ALL=C sort -u)
  # Keep manifest variants absent from the archive visible, without treating them as declared combinations.
  vskus=${'$'}(printf '%s\n' "${'$'}declared_vendor" "${'$'}(printf '%s\n' "${'$'}vskus" | grep -vx '~')" | LC_ALL=C sort -u)
  oskus=${'$'}(printf '%s\n' "${'$'}declared_odm" "${'$'}(printf '%s\n' "${'$'}oskus" | grep -vx '~')" | LC_ALL=C sort -u)
else
  declared_vendor='~'
  declared_odm='~'
  # When vendor has no default, the ODM default can supply the device manifest.
  if [ -n "${'$'}vskus" ] && sku_list_has "${'$'}oskus" '~' && ! sku_list_has "${'$'}vskus" '~'; then
    vskus=${'$'}(printf '%s\n' "${'$'}vskus" '~')
    declared_vendor=""
  fi
fi
if [ -f "${'$'}t/sku-scan-incomplete" ]; then printf '@@@ sku-inferred\n'; fi
sku_label() {
  case "${'$'}1" in '~') printf '-' ;; -) printf '%%2D' ;; *) printf '%s' "${'$'}1" ;; esac
}
count=0
mapped() {
  if [ -n "$1" ]; then printf '%s/%s' "${'$'}w" "$1"; else printf '%s' "${'$'}t/empty"; fi
}
for v in ${'$'}{vskus:-'~'}; do for o in ${'$'}{oskus:-'~'}; do
  set -- --check-compat --dirmap "/system:${'$'}w/${'$'}system" --dirmap "/vendor:${'$'}w/${'$'}vendor" --dirmap "/apex:${'$'}a"
  # checkvintf needs a mapping to distinguish an absent optional file from an unresolved path.
  set -- "${'$'}@" --dirmap "/system_ext:${'$'}(mapped "${'$'}system_ext")"
  set -- "${'$'}@" --dirmap "/product:${'$'}(mapped "${'$'}product")"
  set -- "${'$'}@" --dirmap "/odm:${'$'}(mapped "${'$'}odm")"
  [ -z "${'$'}api" ] || set -- "${'$'}@" --property "ro.product.first_api_level=${'$'}api"
  [ "${'$'}v" != '~' ] && set -- "${'$'}@" --property ro.boot.product.vendor.sku="${'$'}v"
  [ "${'$'}o" != '~' ] && set -- "${'$'}@" --property ro.boot.product.hardware.sku="${'$'}o"
  [ -n "${'$'}kernel" ] && set -- "${'$'}@" --kernel "${'$'}kernel"
  "${'$'}cv" "${'$'}@" > "${'$'}t/out" 2>&1; r=${'$'}?
  confidence=confirmed
  if [ "${'$'}partitions_missing" = true ] ||
    ! sku_list_has "${'$'}declared_vendor" "${'$'}v" || ! sku_list_has "${'$'}declared_odm" "${'$'}o"; then
    confidence=provisional
  fi
  printf '@@@ result %s|%s %s %s\n' "${'$'}(sku_label "${'$'}v")" "${'$'}(sku_label "${'$'}o")" "${'$'}r" "${'$'}confidence"
  grep -v '\[INFO\]' "${'$'}t/out" | sed 's/\x1b\[[0-9;]*m//g; s/^@@@ /checker: /' | grep -v '^COMPATIBLE${'$'}\|^INCOMPATIBLE${'$'}' | head -n 40
  count=${'$'}((count + 1))
done; done
printf '@@@ done %s\n' "${'$'}count"
exit 0"""

    /** Not a refusal: what could not be judged. */
    public const val NOT_CHECKED: String = "VINTF_NOT_CHECKED"

    /** Build refusals. Missing selected partitions and SELinux mappings cannot be acknowledged. */
    public fun refusals(text: String, acknowledgements: Set<String> = emptySet(), scope: String = ""): List<String> {
        val s = sections(text)
        val sepolicyEvidence = listOfNotNull(sepolicy(s))
        val missingPartitions = s.missing
            .filter { it.kind == VintfEvidenceKind.PARTITION }
            .map { "VINTF_PARTITION_MISSING: ${it.message}" }
        val incompatible = mismatches(s, scope)
            .filterNot { it.id in acknowledgements }
            .map { "VINTF_INCOMPATIBLE: [${it.id}] ${it.message}" }
        return sepolicyEvidence + missingPartitions + incompatible
    }

    internal fun acknowledgedMismatches(text: String, acknowledgements: Set<String>, scope: String): List<String> =
        mismatches(sections(text), scope).filter { it.id in acknowledgements }.map { it.message }

    internal fun splitMismatchMessage(text: String): Pair<String?, String> {
        val match = MISMATCH_ID.find(text) ?: return null to text
        return match.groupValues[1] to text.substring(match.range.last + 1)
    }

    internal fun acknowledgementScope(snapshot: ConfigurationSnapshot): String = digestFields(
        snapshot.workspaceId,
        snapshot.profileRevision.toString(),
        snapshot.acquisition.toString(),
        snapshot.extraction.toString(),
        snapshot.assembly.toString(),
        snapshot.customization.toString(),
    )

    private fun digestFields(vararg fields: String): String = CacheKeys.sha256(
        fields.joinToString("") { "${it.length}:$it" },
    )

    private data class Mismatch(val id: String, val message: String)

    private fun mismatches(s: Sections, scope: String): List<Mismatch> =
        s.results.filter { s.confirmsMismatch(it) }.map { result ->
            val reason = result.reason()
            Mismatch(
                digestFields(scope, result.vendorSku.orEmpty(), result.odmSku.orEmpty(), reason),
                "the system does not meet the vendor's VINTF requirements${result.skuLabel()}: $reason. " +
                    "Review the compatibility evidence before building.",
            )
        }

    public fun assess(text: String?, exitCode: Int = 0): VintfAssessment {
        if (text == null) {
            return VintfAssessment(
                VintfStatus.NOT_RUN,
                listOf(VintfEvidence("No VINTF checker output was recorded.", VintfEvidenceKind.SCAN)),
            )
        }
        val s = sections(text)
        val missing = missingEvidence(s) + listOfNotNull(
            VintfEvidence("The VINTF input scan stopped (exit $exitCode).", VintfEvidenceKind.SCAN)
                .takeIf { exitCode != 0 },
        )
        val status = when {
            sepolicy(s) != null || s.results.any(s::confirmsMismatch) -> VintfStatus.INCOMPATIBLE
            s.toolMissing || s.notRun != null -> VintfStatus.NOT_RUN
            missing.isNotEmpty() -> VintfStatus.INCOMPLETE
            else -> VintfStatus.COMPATIBLE
        }
        val skuResults = s.results.map { it.assessment(provisional = it.provisional || s.hasMissingPartition) }
        return VintfAssessment(status, missing, s.androidRelease, s.firstApiLevel, skuResults)
    }

    /** What could not be judged, for the Preflight (never a refusal). */
    public fun notes(text: String): List<String> = assess(text).missingEvidence.map { "$NOT_CHECKED: $it" }

    private fun missingEvidence(s: Sections): List<VintfEvidence> = buildList {
        addAll(s.missing)
        addAll(skuEvidence(s))
        if (s.notRun == null && s.vendorSepolicy == null) {
            add(
                VintfEvidence(
                    "vendor/etc/selinux/plat_sepolicy_vers.txt is missing or unreadable",
                    VintfEvidenceKind.FIRMWARE_METADATA,
                ),
            )
        }
        if (s.toolMissing) {
            add(VintfEvidence("checkvintf is not installed (Setup > Tools > vintf-tools)", VintfEvidenceKind.TOOL))
        }
        s.notRun?.let { add(VintfEvidence(it, VintfEvidenceKind.ASSEMBLY)) }
        if (!s.toolMissing && s.notRun == null) {
            addAll(kernelAndScanEvidence(s))
        }
    }

    private fun skuEvidence(s: Sections): List<VintfEvidence> = buildList {
        if (s.skuInferred || (!s.hasMissingPartition && s.results.any { it.provisional })) {
            add(
                VintfEvidence(
                    "Some SKU combinations could not be verified from the firmware's META/misc_info.txt. " +
                        "Results for those combinations are provisional.",
                    VintfEvidenceKind.FIRMWARE_METADATA,
                ),
            )
        }
        s.results.filter { it.exit == INCOMPATIBLE_EXIT && !s.confirmsMismatch(it) }.forEach {
            add(
                VintfEvidence(
                    "Provisional SKU mismatch${it.skuLabel()}: ${it.reason()}",
                    if (s.hasMissingPartition) VintfEvidenceKind.PARTITION else VintfEvidenceKind.FIRMWARE_METADATA,
                ),
            )
        }
    }

    private fun kernelAndScanEvidence(s: Sections): List<VintfEvidence> = buildList {
        val kernel = s.kernel
        if (kernel == null) {
            add(VintfEvidence("No kernel check evidence was recorded.", VintfEvidenceKind.KERNEL))
        } else if (!kernel.startsWith("ok ")) {
            add(VintfEvidence("the kernel requirements were not checked: $kernel", VintfEvidenceKind.KERNEL))
        }
        if (s.results.isEmpty()) {
            add(VintfEvidence("checkvintf returned no SKU results.", VintfEvidenceKind.SCAN))
        }
        if (s.completed != s.results.size) {
            add(VintfEvidence("The VINTF check did not record a complete set of results.", VintfEvidenceKind.SCAN))
        }
        s.results.filter { it.exit != 0 && it.exit != INCOMPATIBLE_EXIT }.forEach {
            val reason = it.reason()
            val isMeta = reason.contains("manifest", ignoreCase = true) || reason.contains("matrix", ignoreCase = true)
            val kind = if (isMeta) VintfEvidenceKind.FIRMWARE_METADATA else VintfEvidenceKind.SCAN
            add(VintfEvidence("checkvintf could not finish${it.skuLabel()} (exit ${it.exit}): $reason", kind))
        }
    }

    private fun sepolicy(s: Sections): String? {
        val version = s.vendorSepolicy
        if (s.notRun != null || version == null || version in s.mappings) return null
        return "VINTF_SEPOLICY: vendor's sepolicy is $version but system/etc/selinux/mapping has no $version.cil " +
            "(it has ${s.mappings.sorted().joinToString().ifEmpty { "none" }}): init cannot build the SELinux " +
            "policy and the phone does not boot. The system and vendor images must be of one release"
    }

    private data class Result(
        val vendorSku: String?,
        val odmSku: String?,
        val exit: Int,
        val lines: List<String>,
        val provisional: Boolean,
    ) {
        fun assessment(provisional: Boolean) = VintfSkuResult(
            vendorSku = vendorSku,
            odmSku = odmSku,
            status = when (exit) {
                0 -> VintfStatus.COMPATIBLE
                INCOMPATIBLE_EXIT -> VintfStatus.INCOMPATIBLE
                else -> VintfStatus.INCOMPLETE
            },
            provisional = provisional,
        )

        fun skuLabel(): String {
            val skus = listOfNotNull(
                vendorSku?.let { "vendor SKU $it" },
                odmSku?.let { "ODM SKU $it" },
            )
            return if (skus.isEmpty()) "" else skus.joinToString(prefix = " (", postfix = ")")
        }

        /** checkvintf's error, without its framing ("ERROR: files are incompatible:", a trailing ": Success"). */
        fun reason(): String {
            // On a failed scan the checker often puts the filename in a separate parse diagnostic and ends
            // with a generic "No device manifest" error. Keep that filename so the evidence can be repaired.
            if (exit != INCOMPATIBLE_EXIT) {
                val causes = lines.filter { "VINTF parse error:" in it }
                    .map { it.substringAfter("VINTF parse error:").trim() }
                    .distinct()
                if (causes.isNotEmpty()) return causes.joinToString(" ")
            }
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
        val missing: List<VintfEvidence>,
        val androidRelease: String?,
        val firstApiLevel: Int?,
        val notRun: String?,
        val completed: Int?,
        val skuInferred: Boolean,
    ) {
        val hasMissingPartition: Boolean = missing.any { it.kind == VintfEvidenceKind.PARTITION }

        fun confirmsMismatch(result: Result): Boolean =
            result.exit == INCOMPATIBLE_EXIT && !result.provisional && !hasMissingPartition
    }

    private fun sections(text: String): Sections {
        val blocks = text.split(Regex("(?m)^@@@ ")).filter { it.isNotBlank() }.map { block ->
            block.substringBefore('\n').trim() to block.substringAfter('\n', "").lines()
                .map { it.trim() }.filter { it.isNotEmpty() }
        }
        val map = blocks.toMap()
        val skuInferred = "sku-inferred" in map
        val results = blocks.filter { it.first.startsWith("result ") }.mapNotNull { (head, lines) ->
            val parts = head.removePrefix("result ").split(' ')
            val skus = parts.getOrNull(0)?.split('|')
            val exit = parts.getOrNull(1)?.toIntOrNull()
            if (skus?.size != 2 || exit == null) return@mapNotNull null
            val vendor = decodeSku(skus[0])
            val odm = decodeSku(skus[1])
            val provisional = when (parts.getOrNull(2)) {
                "provisional" -> true
                "confirmed" -> false
                else -> skuInferred && (vendor != null || odm != null)
            }
            Result(vendor, odm, exit, lines, provisional)
        }
        return Sections(
            mappings = map["mapping"].orEmpty().map { it.removeSuffix(".cil") }.toSet(),
            vendorSepolicy = map["vendor-sepolicy"]?.firstOrNull(),
            toolMissing = "tool-missing" in map,
            kernel = blocks.firstOrNull { it.first.startsWith("kernel ") }?.first?.removePrefix("kernel "),
            results = results,
            missing = blocks.mapNotNull { (head, _) ->
                head.takeIf { it.startsWith("missing ") }?.removePrefix("missing ")?.let(::evidence)
            },
            androidRelease = blocks.firstOrNull { it.first.startsWith("android ") }?.first?.removePrefix("android "),
            firstApiLevel = blocks.firstOrNull { it.first.startsWith("first-api ") }
                ?.first?.removePrefix("first-api ")?.toIntOrNull()?.takeIf { it > 0 },
            notRun = blocks.firstOrNull { it.first.startsWith("not-run ") }?.first?.removePrefix("not-run "),
            completed = blocks.lastOrNull { it.first.startsWith("done ") }?.first?.removePrefix("done ")?.toIntOrNull(),
            skuInferred = skuInferred,
        )
    }

    private fun decodeSku(value: String): String? = when (value) {
        "-" -> null
        "%2D" -> "-"
        else -> value
    }

    private fun evidence(line: String): VintfEvidence {
        val kind = when (line.substringBefore('|')) {
            "assembly" -> VintfEvidenceKind.ASSEMBLY
            "partition" -> VintfEvidenceKind.PARTITION
            "firmware" -> VintfEvidenceKind.FIRMWARE_METADATA
            "apex" -> VintfEvidenceKind.APEX
            else -> return VintfEvidence(line, VintfEvidenceKind.SCAN)
        }
        return VintfEvidence(line.substringAfter('|'), kind)
    }

    /** checkvintf's exit when the files are incompatible (EX_DATAERR); 0 is compatible, anything else an error. */
    private const val INCOMPATIBLE_EXIT = 65
    private val MISMATCH_ID = Regex("""^\[([0-9a-f]{64})\] """)
}
