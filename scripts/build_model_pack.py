#!/usr/bin/env python3
"""Build an importable Pocket TTS Android language pack."""

from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import tempfile
import zipfile
from pathlib import Path


SHARED_MODELS = ("mimi_encoder.onnx", "text_conditioner.onnx", "tokenizer.model")
INT8_PROFILES = {
    "all": {"flow_lm_main", "flow_lm_flow", "mimi_decoder"},
    "main-only": {"flow_lm_main"},
    "main-flow": {"flow_lm_main", "flow_lm_flow"},
    "main-decoder": {"flow_lm_main", "mimi_decoder"},
}


def parse_voice(value: str) -> tuple[str, str, Path]:
    parts = value.split("=", 2)
    if len(parts) != 3:
        raise argparse.ArgumentTypeError("voice must use ID=DISPLAY_NAME=PATH")
    voice_id, name, source = parts
    if not voice_id or not name:
        raise argparse.ArgumentTypeError("voice ID and display name must not be empty")
    path = Path(source).expanduser().resolve()
    if not path.is_file():
        raise argparse.ArgumentTypeError(f"voice file does not exist: {path}")
    return voice_id, name, path


def arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--models-dir", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--id", required=True)
    parser.add_argument("--name", required=True)
    parser.add_argument("--language", required=True, help="BCP-47 language tag, e.g. de-DE")
    parser.add_argument("--precision", choices=("fp32", "int8"), default="fp32")
    parser.add_argument("--int8-profile", choices=INT8_PROFILES, default="all",
                        help="INT8 components; other components are copied from FP32 (default: all)")
    parser.add_argument("--temperature", type=float, default=0.7)
    parser.add_argument("--lsd-steps", type=int, default=1)
    parser.add_argument("--threads", type=int, default=2)
    parser.add_argument("--license-file", type=Path)
    parser.add_argument("--voice-attribution-file", type=Path)
    parser.add_argument("--source-url")
    parser.add_argument("--voice", action="append", type=parse_voice, default=[], metavar="ID=NAME=PATH")
    return parser.parse_args()


def main() -> None:
    args = arguments()
    if args.precision != "int8" and args.int8_profile != "all":
        raise SystemExit("An INT8 profile requires --precision int8.")
    source_models = args.models_dir.expanduser().resolve()
    suffix = "_int8" if args.precision == "int8" else ""
    quantized = INT8_PROFILES[args.int8_profile] if args.precision == "int8" else set()
    model_files = {name: name for name in SHARED_MODELS}
    for name in ("flow_lm_flow", "flow_lm_main", "mimi_decoder"):
        source_suffix = "_int8" if name in quantized else ""
        model_files[f"{name}{source_suffix}.onnx"] = f"{name}{suffix}.onnx"
    missing = [name for name in model_files if not (source_models / name).is_file()]
    if missing:
        raise SystemExit("Missing model files: " + ", ".join(missing))

    output = args.output.expanduser().resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="pockettts-pack-") as temporary:
        root = Path(temporary)
        models = root / "models"
        voices = root / "voices"
        models.mkdir()
        voices.mkdir()
        for source_name, target_name in model_files.items():
            shutil.copy2(source_models / source_name, models / target_name)

        manifest_voices = []
        for voice_id, display_name, source in args.voice:
            target_name = f"{voice_id}.wav"
            subprocess.run(
                (
                    "ffmpeg", "-nostdin", "-hide_banner", "-loglevel", "error", "-y",
                    "-i", str(source), "-ac", "1", "-ar", "24000", "-c:a", "pcm_s16le",
                    str(voices / target_name),
                ),
                check=True,
            )
            manifest_voices.append({"id": voice_id, "name": display_name, "file": target_name})

        manifest = {
            "format": 1,
            "id": args.id,
            "name": args.name,
            "languageTag": args.language,
            "precision": args.precision,
            "temperature": args.temperature,
            "lsdSteps": args.lsd_steps,
            "threads": args.threads,
            "voices": manifest_voices,
        }
        if args.precision == "int8":
            manifest["int8Profile"] = args.int8_profile
        if args.source_url:
            manifest["source"] = args.source_url
        (root / "manifest.json").write_text(
            json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        if args.license_file:
            license_path = args.license_file.expanduser().resolve()
            if not license_path.is_file():
                raise SystemExit(f"License file does not exist: {license_path}")
            shutil.copy2(license_path, root / "MODEL_LICENSE.txt")
        if args.voice_attribution_file:
            attribution_path = args.voice_attribution_file.expanduser().resolve()
            if not attribution_path.is_file():
                raise SystemExit(f"Voice attribution file does not exist: {attribution_path}")
            shutil.copy2(attribution_path, root / "VOICE_ATTRIBUTION.md")
        with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6, allowZip64=True) as archive:
            for path in sorted(root.rglob("*")):
                if path.is_file():
                    archive.write(path, path.relative_to(root).as_posix())
    print(output)


if __name__ == "__main__":
    main()
