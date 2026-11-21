"""Run VintfCheck's actual shell script and pinned checker against a local stock extraction in WSL."""

import argparse
from pathlib import Path
import os
import re
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET


REPO = Path(__file__).resolve().parents[2]
SOURCE = (
    REPO / "core/domain/src/commonMain/kotlin/org/ide/lti/core/domain/pipeline/stages/VintfCheck.kt"
)
PARTITIONS = ("system/system", "vendor", "odm", "product", "system_ext")
UNKNOWN_HAL = (
    '<hal format="aidl"><name>android.hardware.ltiromtest</name><version>1</version>'
    '<fqname>ITest/default</fqname></hal>'
)


def link_or_copy(source, dest):
    try:
        os.link(source, dest)
    except OSError:
        shutil.copy2(source, dest)


def stock_inputs(stock, tree):
    """Copy mutable metadata and reuse large binary inputs read-only."""
    for partition in PARTITIONS:
        source = stock / partition
        if not source.is_dir():
            continue
        for path in ("etc/vintf", "etc/selinux/mapping", "etc/selinux/plat_sepolicy_vers.txt", "build.prop", "apex"):
            entry = source / path
            dest = tree / partition / path
            if entry.is_dir():
                shutil.copytree(
                    entry, dest, symlinks=True,
                    copy_function=link_or_copy if path == "apex" else shutil.copy2,
                )
            elif entry.is_file():
                dest.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(entry, dest)
    (tree / "kernel").mkdir()
    link_or_copy(stock / "boot.img", tree / "kernel/boot.img")


def run_check(script, tree, tools, expected=("product", "system_ext", "odm"), archive=None, sku_source_changed=False):
    archive_args = ["--firmware-archive", str(archive)] if archive else []
    if sku_source_changed:
        archive_args.append("--sku-source-changed")
    run = subprocess.run(
        ["dash", "-c", script, "sh", str(tree), *map(str, tools), *archive_args, *expected],
        capture_output=True, text=True, timeout=120, check=True,
    )
    if run.stderr:
        print(run.stderr, end="")
    return run.stdout


def clean_result(output):
    results = re.findall(r"^@@@ result (\S+) (\d+) (confirmed|provisional)$", output, re.M)
    return (
        any(code == "0" for _, code, _ in results)
        and all(code == "0" or confidence == "provisional" for _, code, confidence in results)
        and f"@@@ done {len(results)}\n" in output
        and "@@@ kernel ok " in output
        and "@@@ missing " not in output
    )


def probe_sku_matrix(script, tree, tools):
    """Exercise one stock SKU and test-only variants of the stock vendor and ODM manifests."""
    vendor = tree / "vendor/etc/vintf"
    odm = tree / "odm/etc/vintf"
    original_vendor = vendor / "manifest_sun.xml"
    other_vendor = vendor / "manifest_lti_vendor.xml"
    other_odm = odm / "manifest_lti_odm.xml"
    shutil.copy2(original_vendor, other_vendor)
    odm_manifest = ET.parse(odm / "manifest.xml")
    odm_manifest.getroot().append(ET.fromstring(UNKNOWN_HAL))
    odm_manifest.write(other_odm, encoding="utf-8")

    output = run_check(script, tree, tools)
    results = re.findall(r"^@@@ result ((?:sun|lti_vendor)\|\S+) (\d+) provisional$", output, re.M)
    assert dict(results) == {"sun|-": "0", "lti_vendor|-": "0", "sun|lti_odm": "65", "lti_vendor|lti_odm": "65"}, output
    assert len(results) == 4, output
    assert output.count("android.hardware.ltiromtest.ITest/default") >= 2, output
    other_vendor.unlink()
    other_odm.unlink()
    print("SKU matrix: 2 vendor × 2 ODM selections; only the test ODM variant mismatched")


def load_script():
    script = SOURCE.read_text(encoding="utf-8").split('public const val SCRIPT: String = """', 1)[1]
    return script.split('"""', 1)[0].replace("${'$'}", "$")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("stock", type=Path, help="stock firmware/extracted directory in WSL")
    parser.add_argument("--bin", type=Path, default=Path.home() / "LtiRomTools/bin")
    parser.add_argument("--sku-matrix", action="store_true", help="also probe test variants of PQ84P01's stock SKUs")
    args = parser.parse_args()
    tools = [args.bin / tool for tool in (
        "vintf-tools/current/checkvintf", "android-tools/current/unpack_bootimg",
        "erofs-utils/current/fsck.erofs",
    )]
    script = load_script()
    with tempfile.TemporaryDirectory(prefix="lti-vintf-") as temp:
        tree = Path(temp) / "work"
        stock_inputs(args.stock, tree)
        stock = run_check(script, tree, tools)
        assert clean_result(stock), stock
        print("Stock firmware: checkvintf passed the scanned SKU combinations")
        if args.sku_matrix:
            probe_sku_matrix(script, tree, tools)

        # Declare an Android HAL instance outside the corresponding framework matrix.
        fragment = tree / "vendor/etc/vintf/manifest/ltirom_test.xml"
        fragment.write_text(
            f'<manifest version="8.0" type="device">{UNKNOWN_HAL}</manifest>',
            encoding="utf-8",
        )
        mismatch = run_check(script, tree, tools)
        assert re.search(r"^@@@ result .* 65 (?:confirmed|provisional)$", mismatch, re.M), mismatch
        assert "android.hardware.ltiromtest" in mismatch, mismatch
        fragment.unlink()
        print("HAL mismatch: incompatible, unmatched interface named")

        for partition in ("product", "system_ext"):
            (tree / partition).rename(tree / "system/system" / partition)
        (tree / "odm").rename(tree / "vendor/odm")
        folded = run_check(script, tree, tools)
        assert clean_result(folded), folded
        print("Folded product, system_ext, and ODM: compatible")

        broken = tree / "vendor/apex/lti-unreadable.apex"
        broken.parent.mkdir(parents=True, exist_ok=True)
        broken.write_bytes(b"not a readable APEX archive")
        incomplete = run_check(script, tree, tools)
        assert "@@@ missing apex|Could not read VINTF fragments from APEX vendor/apex/lti-unreadable.apex." in incomplete
        assert re.search(r"^@@@ result .* 0 (?:confirmed|provisional)$", incomplete, re.M), incomplete
        assert not clean_result(incomplete), incomplete
        print("Unreadable APEX: missing archive evidence despite a successful checker exit")
        broken.unlink()

        apex_dir = tree / "vendor/odm/apex"
        apex_dir.symlink_to(tree / "missing-apex-target", target_is_directory=True)
        dangling = run_check(script, tree, tools)
        assert "@@@ missing apex|APEX directory vendor/odm/apex is a link" in dangling, dangling
        assert re.search(r"^@@@ result .* 0 (?:confirmed|provisional)$", dangling, re.M), dangling
        assert not clean_result(dangling), dangling
        apex_dir.unlink()
        print("Dangling APEX directory: exact missing evidence despite a successful checker exit")

        (tree / "vendor/odm").rename(Path(temp) / "odm-saved")
        no_odm = run_check(script, tree, tools, expected=("product", "system_ext"))
        assert "@@@ result " in no_odm and "Cannot resolve path /odm" not in no_odm, no_odm
        assert "@@@ missing partition|The selected /odm partition" not in no_odm, no_odm
        print("No optional ODM partition: checkvintf resolved its optional input paths")

        (tree / "system").rename(Path(temp) / "system-saved")
        no_system = run_check(script, tree, tools)
        assert "@@@ missing assembly|The assembled /system partition is missing." in no_system, no_system
        assert "@@@ not-run System and vendor are required to start checkvintf." in no_system, no_system
        assert "@@@ vendor-sepolicy" not in no_system and "@@@ result" not in no_system, no_system
        print("Missing /system with /vendor present: checker and policy comparison did not run")

        unassembled = run_check(script, Path(temp) / "unassembled", tools)
        assert "@@@ not-run Assemble must create work/ first." in unassembled, unassembled
        print("Unassembled workspace: checker did not run")


if __name__ == "__main__":
    main()
