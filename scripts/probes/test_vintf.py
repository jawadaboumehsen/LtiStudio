"""Exercise the production VINTF shell scanner with temporary inputs and a stub checker."""

from pathlib import Path
import os
import re
import tempfile
import unittest
from zipfile import ZipFile

from vintf import load_script, run_check


CHECKER = """#!/bin/sh
while [ "$#" -gt 0 ]; do
  case "$1" in
    --property) printf 'property=%s\\n' "$2"; shift ;;
    --dirmap)
      case "$2" in
        /vendor:*|/odm:*) printf 'mapping=%s\\n' "$2" ;;
        /apex:*)
          apex_root=${2#/apex:}
          if [ -f "$apex_root/com.vendor.fake/etc/vintf/manifest.xml" ]; then
            grep -o 'moduleName="com.vendor.fake" partition="VENDOR"' "$apex_root/apex-info-list.xml"
          fi ;;
      esac
      shift ;;
  esac
  shift
done
exit 0
"""


class VintfScannerTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory(prefix="lti-vintf-regression-")
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        self.tree = self.root / "work"
        (self.tree / "system/system/etc/vintf").mkdir(parents=True)
        self.put("vendor/build.prop", "ro.product.first_api_level=29\n")
        self.checker = self.root / "checkvintf"
        self.checker.write_text(CHECKER, encoding="utf-8")
        self.checker.chmod(0o700)

    def put(self, path, contents='<manifest version="1.0" type="device"/>'):
        file = self.tree / path
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(contents, encoding="utf-8")
        return file

    def run_scan(self, archive=None, expected=(), sku_source_changed=False):
        return run_check(
            load_script(), self.tree, (self.checker, "false", "false"),
            expected=expected, archive=archive, sku_source_changed=sku_source_changed,
        )

    def results(self, output):
        rows = re.findall(r"^@@@ result (\S+) (\d+) (confirmed|provisional)$", output, re.M)
        self.assertIn(f"@@@ done {len(rows)}\n", output, output)
        return {pair: (exit_code, confidence) for pair, exit_code, confidence in rows}

    def pairs(self, output):
        rows = self.results(output)
        self.assertTrue(all(code == "0" for code, _ in rows.values()), output)
        return set(rows)

    def archive(self, contents):
        path = self.root / "firmware.zip"
        with ZipFile(path, "w") as archive:
            archive.writestr("META/misc_info.txt", contents)
        return path

    def test_empty_standalone_odm_does_not_mask_folded_odm(self):
        (self.tree / "odm").mkdir()
        self.put("vendor/odm/etc/vintf/manifest_folded.xml")
        output = self.run_scan()
        self.assertEqual({"-|folded"}, self.pairs(output))
        self.assertIn(f"mapping=/odm:{self.tree}/vendor/odm", output)

    def test_empty_standalone_vendor_does_not_mask_folded_vendor(self):
        (self.tree / "vendor/build.prop").unlink()
        self.put("system/system/vendor/etc/vintf/manifest_folded.xml")
        output = self.run_scan()
        self.assertEqual({"folded|-"}, self.pairs(output))
        self.assertIn(f"mapping=/vendor:{self.tree}/system/system/vendor", output)

    def test_modern_odm_default_shadows_legacy_names(self):
        self.put("odm/etc/vintf/manifest.xml")
        self.put("odm/etc/vintf/manifest_modern.xml")
        self.put("odm/etc/manifest_legacy.xml")
        self.assertEqual({"-|-", "-|modern"}, self.pairs(self.run_scan()))

    def test_unshadowed_legacy_names_are_checked_and_deduplicated(self):
        self.put("odm/etc/vintf/manifest_modern.xml")
        self.put("odm/etc/manifest_modern.xml")
        self.put("odm/etc/manifest_legacy.xml")
        self.put("odm/etc/manifest.xml")
        self.assertEqual({"-|-", "-|modern", "-|legacy"}, self.pairs(self.run_scan()))

    @unittest.skipIf(os.geteuid() == 0, "permission test needs an unprivileged user")
    def test_unreadable_sku_dir_is_reported(self):
        directory = self.put("odm/etc/vintf/manifest_hidden.xml").parent
        self.addCleanup(directory.chmod, 0o700)
        directory.chmod(0)
        output = self.run_scan()
        self.assertIn("@@@ missing firmware|Cannot read SKU manifest directory odm/etc/vintf.", output)
        self.assertIn("@@@ sku-inferred", output)

    def test_archive_declarations_override_names_and_allow_empty_sku(self):
        self.put("vendor/etc/vintf/manifest_inactive.xml")
        self.put("odm/etc/vintf/manifest.xml")
        self.put("odm/etc/vintf/manifest_inactive.xml")
        output = self.run_scan(self.archive(
            "vintf_vendor_manifest_skus=shipping shipping\r\n"
            "vintf_odm_manifest_skus=global regional\r\n"
            "vintf_include_empty_vendor_sku=true\r\n"
            "vintf_include_empty_odm_sku=false\r\n"
        ))
        rows = self.results(output)
        confirmed = {pair for pair, (_, confidence) in rows.items() if confidence == "confirmed"}
        self.assertEqual({"shipping|global", "shipping|regional", "-|global", "-|regional"}, confirmed)
        self.assertEqual(("0", "provisional"), rows["inactive|inactive"])
        self.assertNotIn("shipping|-", rows)

    def test_bad_archive_declarations_use_inferred_results(self):
        self.put("vendor/etc/vintf/manifest_detected.xml")
        output = self.run_scan(self.archive("vintf_vendor_manifest_skus=bad/name\n"))
        self.assertIn("@@@ missing firmware|Cannot read VINTF SKU declarations", output)
        self.assertIn("@@@ sku-inferred", output)
        self.assertEqual({"detected|-"}, self.pairs(output))

    def test_corrupt_archive_declarations_are_not_trusted(self):
        self.put("vendor/etc/vintf/manifest_detected.xml")
        archive = self.archive("vintf_vendor_manifest_skus=declared\n")
        archive.write_bytes(archive.read_bytes().replace(b"declared", b"replaced"))
        output = self.run_scan(archive)
        self.assertIn("@@@ missing firmware|Cannot read VINTF SKU declarations", output)
        self.assertIn("@@@ sku-inferred", output)
        self.assertEqual({"detected|-"}, self.pairs(output))

    def test_manifest_guesses_are_marked_inferred(self):
        self.put("vendor/etc/vintf/manifest_a.xml")
        self.put("vendor/etc/vintf/manifest_b.xml")
        self.put("odm/etc/vintf/manifest_a.xml")
        self.put("odm/etc/vintf/manifest_b.xml")
        output = self.run_scan()
        rows = self.results(output)
        self.assertEqual({"a|a", "a|b", "b|a", "b|b"}, set(rows))
        self.assertTrue(all(confidence == "provisional" for _, confidence in rows.values()))

    def test_default_mismatch_remains_confirmed_without_sku_metadata(self):
        self.put("vendor/etc/vintf/manifest.xml")
        self.put("vendor/etc/vintf/manifest_carrier.xml")
        self.checker.write_text(CHECKER.replace("exit 0", "exit 65"), encoding="utf-8")

        rows = self.results(self.run_scan())
        self.assertEqual({"-|-": ("65", "confirmed"), "carrier|-": ("65", "provisional")}, rows)

    def test_broken_zip_and_malformed_metadata_keep_the_same_default_blocker(self):
        self.put("vendor/etc/vintf/manifest.xml")
        self.checker.write_text(CHECKER.replace("exit 0", "exit 65"), encoding="utf-8")
        broken_zip = self.root / "broken.zip"
        broken_zip.write_text("broken ZIP", encoding="utf-8")
        malformed_metadata = self.archive("vintf_include_empty_vendor_sku=invalid\n")

        for archive in (broken_zip, malformed_metadata):
            with self.subTest(archive=archive):
                output = self.run_scan(archive)
                self.assertIn("@@@ missing firmware|Cannot read VINTF SKU declarations", output)
                self.assertEqual({"-|-": ("65", "confirmed")}, self.results(output))

    def test_zip_without_metadata_is_not_reported_as_corrupt(self):
        archive = self.root / "valid.zip"
        with ZipFile(archive, "w") as payload:
            payload.writestr("placeholder", "")
        output = self.run_scan(archive)
        self.assertNotIn("Cannot read VINTF SKU declarations", output)
        self.assertEqual({"-|-": ("0", "confirmed")}, self.results(output))

    def test_empty_vendor_is_probed_when_odm_can_supply_a_default(self):
        self.put("vendor/etc/vintf/manifest_carrier.xml")
        self.put("odm/etc/vintf/manifest.xml")
        rows = self.results(self.run_scan())
        self.assertEqual({"-|-": ("0", "provisional"), "carrier|-": ("0", "provisional")}, rows)

    def test_replaced_vendor_or_odm_does_not_use_original_sku_declarations(self):
        archive = self.archive("vintf_vendor_manifest_skus=original\n")
        self.put("vendor/etc/vintf/manifest_replacement.xml")
        output = self.run_scan(archive, sku_source_changed=True)
        self.assertIn("SKU declarations from the original archive do not apply", output)
        self.assertEqual({"replacement|-": ("0", "provisional")}, self.results(output))

    def test_expected_missing_odm_has_distinct_missing_partition_evidence(self):
        self.put("vendor/etc/vintf/manifest.xml")
        self.checker.write_text(CHECKER.replace("exit 0", "exit 65"), encoding="utf-8")
        output = self.run_scan(expected=("odm",))
        self.assertIn("@@@ missing partition|The selected /odm partition", output)
        self.assertEqual({"-|-": ("65", "provisional")}, self.results(output))

    def test_flattened_apex_uses_its_manifest_name_and_provides_fragments(self):
        self.put("vendor/apex/directory-name/etc/vintf/manifest.xml")
        self.put("vendor/apex/directory-name/apex_manifest.json", '{"name":"com.vendor.fake"}')
        output = self.run_scan()
        self.assertIn('moduleName="com.vendor.fake" partition="VENDOR"', output)
        self.assertNotIn("@@@ missing apex", output)

    def test_flattened_apex_can_read_a_protobuf_manifest(self):
        manifest = self.put("vendor/apex/directory-name/apex_manifest.pb")
        name = b"com.vendor.fake"
        manifest.write_bytes(bytes([10, len(name)]) + name)
        self.put("vendor/apex/directory-name/etc/vintf/manifest.xml")

        output = self.run_scan()
        self.assertIn('moduleName="com.vendor.fake" partition="VENDOR"', output)
        self.assertNotIn("@@@ missing apex", output)

    def test_broken_flattened_apex_is_reported(self):
        self.put("vendor/apex/broken/etc/vintf/manifest.xml")
        self.put("vendor/apex/broken/apex_manifest.json", "{")
        self.assertIn(
            "@@@ missing apex|Could not read VINTF fragments from flattened APEX vendor/apex/broken.",
            self.run_scan(),
        )

    def test_dash_is_passed_as_a_sku_and_differs_from_default(self):
        self.put("vendor/etc/vintf/manifest_-.xml")
        self.put("vendor/etc/vintf/manifest.xml")
        output = self.run_scan()
        self.assertEqual({"%2D|-", "-|-"}, self.pairs(output))
        self.assertEqual(1, output.count("property=ro.boot.product.vendor.sku=-\n"))

    def test_zero_first_api_is_not_passed_to_checker(self):
        self.put("vendor/build.prop", "ro.product.first_api_level=0\n")
        output = self.run_scan()
        self.assertIn("@@@ missing firmware|ro.product.first_api_level is missing or invalid", output)
        self.assertNotIn("property=ro.product.first_api_level=", output)


if __name__ == "__main__":
    unittest.main()
