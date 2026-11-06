#!/usr/bin/env python3
"""Exercise pack CLI selection, archive compatibility and failed-build preservation."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile


ROOT = Path(__file__).resolve().parents[1]
MODELS = ("flow_lm_main", "flow_lm_flow", "mimi_decoder")


class ModelPackProfilesTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.models = self.root / "models"
        self.models.mkdir()
        for name in ("mimi_encoder.onnx", "text_conditioner.onnx", "tokenizer.model"):
            (self.models / name).write_bytes(name.encode())
        for name in MODELS:
            for suffix in ("", "_int8"):
                filename = f"{name}{suffix}.onnx"
                (self.models / filename).write_bytes(filename.encode())
        self.output = self.root / "pack.zip"

    def build(self, precision="int8", profile="all"):
        return subprocess.run(
            [sys.executable, str(ROOT / "scripts/build_model_pack.py"),
             "--models-dir", str(self.models), "--output", str(self.output),
             "--id", "english-test", "--name", "English test", "--language", "en-US",
             "--precision", precision, "--int8-profile", profile],
            capture_output=True, text=True,
        )

    def test_profile_bytes_and_legacy_runtime_filenames(self):
        # Both FP32 and older INT8 files exist. Unselected components must use
        # FP32 bytes even when stale quantized graphs are available beside them.
        for profile, quantized in {
            "all": set(MODELS), "main-only": {"flow_lm_main"},
            "main-flow": {"flow_lm_main", "flow_lm_flow"},
            "main-decoder": {"flow_lm_main", "mimi_decoder"},
        }.items():
            with self.subTest(profile=profile):
                result = self.build(profile=profile)
                self.assertEqual(result.returncode, 0, result.stderr)
                with zipfile.ZipFile(self.output) as archive:
                    manifest = json.loads(archive.read("manifest.json"))
                    self.assertEqual(manifest["format"], 1)
                    self.assertEqual(manifest["precision"], "int8")
                    self.assertEqual(manifest["int8Profile"], profile)
                    for name in MODELS:
                        source = f"{name}{'_int8' if name in quantized else ''}.onnx"
                        self.assertEqual(archive.read(f"models/{name}_int8.onnx"),
                                         (self.models / source).read_bytes())
                    self.assertEqual(len(archive.namelist()), 7)

    def test_fp32_keeps_original_graph_names_and_manifest(self):
        result = self.build(precision="fp32")
        self.assertEqual(result.returncode, 0, result.stderr)
        with zipfile.ZipFile(self.output) as archive:
            self.assertNotIn("int8Profile", json.loads(archive.read("manifest.json")))
            for name in MODELS:
                self.assertEqual(archive.read(f"models/{name}.onnx"),
                                 (self.models / f"{name}.onnx").read_bytes())

    def test_unused_quantized_auxiliary_files_are_not_required(self):
        for name in ("flow_lm_flow", "mimi_decoder"):
            (self.models / f"{name}_int8.onnx").unlink()
        result = self.build(profile="main-only")
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_missing_required_fp32_auxiliary_preserves_previous_pack(self):
        (self.models / "mimi_decoder.onnx").unlink()
        self.output.write_bytes(b"previous valid pack")
        result = self.build(profile="main-only")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("mimi_decoder.onnx", result.stderr)
        self.assertEqual(self.output.read_bytes(), b"previous valid pack")

    def test_rejects_int8_profile_for_fp32(self):
        result = self.build(precision="fp32", profile="main-only")
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(self.output.exists())

    def test_export_cli_rejects_invalid_profiles_before_installing_dependencies(self):
        for precision, profile in (("fp32", "main-only"), ("int8", "invalid")):
            with self.subTest(precision=precision, profile=profile):
                result = subprocess.run(
                    ["bash", str(ROOT / "scripts/export_model_pack.sh"),
                     "english", "en-US", "English test", precision, profile],
                    capture_output=True, text=True,
                )
                self.assertEqual(result.returncode, 2)


if __name__ == "__main__":
    unittest.main()
