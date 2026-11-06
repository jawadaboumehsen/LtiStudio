#!/bin/sh
# Checks that the workbench commands stay inside the workspace when a link is already in place.
#
#   wsl.exe -d Ubuntu -- dash scripts/probes/workbench-paths.sh     everything (Linux or WSL, the tools' real shell)
#   sh scripts/probes/workbench-paths.sh --source-only              the source check only (pre-push, any shell)
#
# The source check reads the Kotlin, so it fails when a final write loses its protection. The behaviour checks run
# copies of the commands: they prove what each form does on a real filesystem, and must be kept in step with the
# source by hand.
fail=0
ok() { echo "ok   $1"; }
bad() { echo "FAIL $1"; fail=1; }

src=core/domain/src/commonMain/kotlin/org/ide/lti/core/domain
[ -d "$src" ] || src="$(dirname "$0")/../../$src"

# A bare `mv -f` onto a final path moves into a linked folder; the split's move into its own folder is the exception.
found=$(grep -n -e '"mv", "-f",' -e 'mv -f' "$src"/workbench/*.kt "$src"/pipeline/toolkit/ArchiveBrowser.kt \
  "$src"/workspace/FirmwareArchives.kt "$src"/usecase/publish/PublishReleaseUseCase.kt |
  grep -v -e '"-T"' -e 'mv -f -T' -e 'mv -f \\"$1\\"/\* \\"$2\\"')
[ -z "$found" ] && ok "source: every mv -f publishes with -T" || { bad "source: mv without -T"; echo "$found"; }
grep -q 'mv -T "${.\$.}p" "${.\$.}k"' "$src/pipeline/toolkit/ArchiveBrowser.kt" &&
  ok "source: the archive cache publishes with -T" || bad "source: archive cache mv without -T"
grep -q 'job.requireNoLinks()' "$src/workbench/WorkbenchRunner.kt" &&
  ok "source: every job checks for links" || bad "source: the job's link check is gone"
[ "$(grep -c 'requireNoLinkInWorkspace(driver.ws)' "$src/pipeline/PipelineOrchestrator.kt")" = 2 ] &&
  ok "source: fresh and resumed stages both check for links" || bad "source: a stage path lost its link check"
for f in usecase/publish/PublishReleaseUseCase.kt workspace/FirmwareArchives.kt; do
  grep -q '\.requireNoLinkInWorkspace(ws)\|port.requireNoLinkInWorkspace(ws)' "$src/$f" &&
    ok "source: $f checks for links" || bad "source: $f lost its link check"
done
grep -q 'mv -T ' "${src%core/domain/src/*}core/data/src/desktopMain/kotlin/org/ide/lti/core/data/tools/ToolSettingsFile.kt" &&
  ok "source: tool settings publish with -T" || bad "source: tool settings mv without -T"
[ "$(grep -c 'check(scan.exitCode == 0)' "$src/ports/WorkspaceLinks.kt" "$src/workbench/WorkbenchRunner.kt" | awk -F: '{ s += $2 } END { print s + 0 }')" = 2 ] &&
  ok "source: a failed link scan stops the work" || bad "source: a link scan ignores its exit code"
grep -q 'g=$(realpath -m' "$src/workbench/WorkbenchRunner.kt" &&
  ok "source: the permitted base is judged too" || bad "source: the base check is gone"
n=$(grep -c 'checkLinks = false' "$src"/workbench/*.kt | awk -F: '{ s += $2 } END { print s + 0 }')
[ "$n" = 1 ] && ok "source: only Disk usage skips the link check" || bad "source: $n jobs skip the link check"

[ "$1" = --source-only ] && exit $fail

t=$(mktemp -d); trap 'rm -rf "$t"' EXIT
mkdir -p "$t/outside/dir"; echo sentinel > "$t/outside/victim"

# WorkbenchRunner.Job.folderResolvesInside
inside() {
  sh -c 'r=$(realpath -m -- "$(dirname -- "$1")") && w=$(realpath -m -- "$2") && g=$(realpath -m -- "$3") && case "$w/" in "$g"/*) ;; *) exit 1;; esac && case "$r/" in "$w"/*) ;; *) exit 1;; esac' sh "$1" "$2" "$3"
}
yes_() { if inside "$2" "$3" "$4"; then ok "$1"; else bad "$1"; fi; }
no_() { if inside "$2" "$3" "$4"; then bad "$1"; else ok "$1"; fi; }
ws="$t/ws"; mkdir -p "$ws/out/toolkit"
yes_ "folder: a normal export" "$ws/out/toolkit/system/a/b" "$ws/out/toolkit/system" "$ws"
yes_ "folder: nothing made yet" "$ws/out/toolkit/vendor/x" "$ws/out/toolkit/vendor" "$ws"
yes_ "folder: the workspace as base" "$ws/restore/x" "$ws" "$ws"
ln -s "$t/outside" "$ws/out/toolkit/system"
no_ "folder: the base is a link out" "$ws/out/toolkit/system/probe.txt" "$ws/out/toolkit/system" "$ws"
rm "$ws/out/toolkit/system"; mv "$ws/out/toolkit" "$ws/out/tk"; ln -s "$t/outside" "$ws/out/toolkit"
no_ "folder: a link higher up" "$ws/out/toolkit/system/probe.txt" "$ws/out/toolkit/system" "$ws"
rm "$ws/out/toolkit"; mv "$ws/out/tk" "$ws/out/toolkit"
mkdir -p "$ws/real"; ln -s ../../real "$ws/out/toolkit/system"
yes_ "folder: a link that stays inside" "$ws/out/toolkit/system/f" "$ws/out/toolkit/system" "$ws"
rm "$ws/out/toolkit/system"
mkdir -p "$t/wsother"
no_ "folder: a sibling with the same prefix" "$t/wsother/f" "$t/wsother" "$ws"

# WorkbenchRunner.Job.requireNoScratchLink
scan() {
  find "$1/workbench" \( -path '*/workbench/boot/*/ramdisk/*' -o -path '*/workbench/apex/*/*' \) -prune -o -type l -print 2>/dev/null
}
s="$t/scratch"; mkdir -p "$s/workbench/boot/boot/ramdisk/system" "$s/workbench/boot/boot/unpacked" "$s/workbench/apex/x/lib"
ln -s /system/bin/init "$s/workbench/boot/boot/ramdisk/init"
ln -s ../lib "$s/workbench/apex/x/lib/up"
[ -z "$(scan "$s")" ] && ok "scratch: links inside a ramdisk or APEX are fine" || bad "scratch: firmware links refused"
ln -s "$t/outside/victim" "$s/workbench/boot/boot/ramdisk.new.cpio"
[ -n "$(scan "$s")" ] && ok "scratch: a link at a generated file is refused" || bad "scratch: generated link missed"
rm "$s/workbench/boot/boot/ramdisk.new.cpio"
mv "$s/workbench/boot/boot/ramdisk" "$s/rd"; ln -s "$t/outside" "$s/workbench/boot/boot/ramdisk"
[ -n "$(scan "$s")" ] && ok "scratch: the ramdisk folder itself is judged" || bad "scratch: ramdisk link missed"
rm "$s/workbench/boot/boot/ramdisk"
mv "$s/workbench/boot" "$s/boot-real"; mkdir -p "$t/outside/boot"; echo keep > "$t/outside/boot/keep.txt"
ln -s "$t/outside" "$s/workbench/boot"
[ -n "$(scan "$s")" ] && ok "scratch: a linked workbench/boot is refused before any cleanup" || bad "scratch: ancestor missed"
rm -rf "$s/workbench"
[ -f "$t/outside/boot/keep.txt" ] && ok "scratch: Disk usage frees it without following the link" || bad "scratch: rm followed the link"
mkdir -p "$s/w2"; ln -s "$t/outside" "$s/w2/workbench"
[ -n "$(scan "$s/w2")" ] && ok "scratch: workbench itself as a link is refused" || bad "scratch: root link missed"

# ports/WorkspaceLinks.kt, linkScanArgs
wsscan() {
  r="$1"
  find "$r" -maxdepth 3 \( \( -path "$r/work/*/*" ! -path "$r/work/configs/*" ! -path "$r/work/kernel/*" ! -path "$r/work/package/*" \) \
    -o -path "$r/workbench/*/*" -o -path "$r/out/vendor-ramdisk-by-name/*" -o -path "$r/out/verify/*" \) -prune -o -type l -print
}
w="$t/work/fixture"  # the workspace's own path may itself hold "work": the patterns must not match it
mkdir -p "$w/work/system/app" "$w/work/configs" "$w/work/kernel" "$w/work/package/ab" "$w/restore/debloat" \
  "$w/out/images" "$w/out/vendor-ramdisk-by-name" "$w/firmware/extracted/system" "$w/workbench/boot/boot/ramdisk"
ln -s /system/bin "$w/work/system/bin"; ln -s /x "$w/out/vendor-ramdisk-by-name/ramdisk_"
ln -s /system/bin/sh "$w/firmware/extracted/system/sh"; ln -s /init "$w/workbench/boot/boot/ramdisk/init"
[ -z "$(wsscan "$w")" ] && ok "workspace: firmware links are left alone" || { bad "workspace: firmware links refused"; wsscan "$w"; }
refused() {  # name, path (made a link out), then put back
  mv "$w/$2" "$w/$2.real" 2>/dev/null; ln -s "$t/outside" "$w/$2"
  [ -n "$(wsscan "$w")" ] && ok "workspace: $1" || bad "workspace: $1"
  rm "$w/$2"; mv "$w/$2.real" "$w/$2" 2>/dev/null
}
refused "a linked work root" work
refused "a linked restore root" restore
refused "a linked restore/debloat" restore/debloat
refused "a linked partition root" work/system
refused "a linked work/configs" work/configs
refused "a linked out/images" out/images
refused "a linked firmware/extracted" firmware/extracted
refused "a linked workbench/boot" workbench/boot
refused "a generated image that is a link" out/images/system.img
refused "a sidecar that is a link" work/configs/fs_config-system
refused "a kernel image that is a link" work/kernel/boot.img
refused "a cache record that is a link" .cache

# BootWorkbench.writeVbmetaFlags
tk="$ws/out/toolkit"
echo stock > "$t/vbmeta.img"; ln -s "$t/outside/victim" "$tk/vbmeta.img"
out="$tk/vbmeta.img"; partial="$out.partial"
vbmeta() {
  sh -c 'mkdir -p "$(dirname "$2")" && rm -f "$2" && cp -f "$1" "$2"' sh "$1" "$partial"
  printf "$2" | dd of="$partial" bs=1 seek=0 conv=notrunc status=none
  mv -f -T "$partial" "$out"
}
vbmeta "$t/vbmeta.img" X
[ "$(cat "$t/outside/victim")" = sentinel ] && ok "vbmeta: the file outside is untouched" || bad "vbmeta: wrote through the link"
[ ! -L "$out" ] && [ "$(head -c1 "$out")" = X ] && ok "vbmeta: the link was replaced" || bad "vbmeta: result"
vbmeta "$out" Y
[ "$(head -c1 "$out")" = Y ] && ok "vbmeta: an output can be edited again" || bad "vbmeta: re-edit"

# ImageWorkbench.convert, importReplacement; BootWorkbench.saveOverride
ln -s "$t/outside/dir" "$tk/system.raw.img"; echo image > "$tk/system.raw.img.partial"
mv -f -T "$tk/system.raw.img.partial" "$tk/system.raw.img"
[ -z "$(ls -A "$t/outside/dir")" ] && [ ! -L "$tk/system.raw.img" ] && ok "publish: a link to a folder is replaced" || bad "publish: moved into the link"
mkdir "$tk/dir.img"; echo x > "$tk/dir.img.partial"
if mv -f -T "$tk/dir.img.partial" "$tk/dir.img" 2>/dev/null; then bad "publish: moved over a folder"; else ok "publish: a real folder is refused"; fi

# ImageWorkbench.reextract
echo own > "$t/own.img"; echo sentinel > "$t/outside/victim2"; ln -s "$t/outside/victim2" "$tk/p.img"
cp -f --remove-destination "$t/own.img" "$tk/p.img"
[ "$(cat "$t/outside/victim2")" = sentinel ] && [ "$(cat "$tk/p.img")" = own ] && ok "reextract: the link was replaced" || bad "reextract: wrote through"

# ImageWorkbench.split
mkdir -p "$t/tk" "$t/partial"
for f in system.img.part00 system.img.part01 system.img.part01.backup; do : > "$t/tk/$f"; done
: > "$t/partial/system.img.part00"
sh -c 'for f in "$2"/"$3"[0-9]*; do case "${f##*.part}" in *[!0-9]*) ;; *) rm -f "$f" ;; esac; done; mv -f "$1"/* "$2" && rmdir "$1"' sh "$t/partial" "$t/tk" system.img.part
[ ! -e "$t/tk/system.img.part01" ] && [ -e "$t/tk/system.img.part01.backup" ] && ok "split: only numbered parts go" || bad "split"

exit $fail
