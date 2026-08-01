#!/usr/bin/env python3
"""Create a voice-free, deterministic copy of an existing model-pack ZIP."""

from __future__ import annotations

import argparse
import json
import shutil
import zipfile
from pathlib import Path


FIXED_TIME = (1980, 1, 1, 0, 0, 0)
AUDIO_SUFFIXES = {".wav", ".wave", ".mp3", ".flac", ".ogg", ".m4a"}


def arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--license-file", type=Path, required=True)
    return parser.parse_args()


def clean_info(name: str) -> zipfile.ZipInfo:
    info = zipfile.ZipInfo(name, FIXED_TIME)
    info.compress_type = zipfile.ZIP_DEFLATED
    info.create_system = 3
    info.external_attr = 0o100644 << 16
    return info


def main() -> None:
    args = arguments()
    source = args.input.expanduser().resolve()
    output = args.output.expanduser().resolve()
    license_file = args.license_file.expanduser().resolve()
    if not source.is_file():
        raise SystemExit(f"Input pack does not exist: {source}")
    if not license_file.is_file():
        raise SystemExit(f"License file does not exist: {license_file}")
    if output == source:
        raise SystemExit("Input and output must be different files")

    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(source, "r") as incoming:
        manifest = json.loads(incoming.read("manifest.json"))
        manifest["voices"] = []
        manifest.setdefault("source", "https://huggingface.co/kyutai/pocket-tts")

        with zipfile.ZipFile(
            output,
            "w",
            compression=zipfile.ZIP_DEFLATED,
            compresslevel=6,
            allowZip64=True,
        ) as outgoing:
            for member in sorted(incoming.infolist(), key=lambda item: item.filename):
                name = member.filename
                if member.is_dir() or name in {"manifest.json", "MODEL_LICENSE.txt"}:
                    continue
                if name.startswith("voices/") or Path(name).suffix.lower() in AUDIO_SUFFIXES:
                    continue
                with incoming.open(member, "r") as reader:
                    with outgoing.open(clean_info(name), "w", force_zip64=True) as writer:
                        shutil.copyfileobj(reader, writer, length=1024 * 1024)

            manifest_bytes = (json.dumps(manifest, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
            outgoing.writestr(clean_info("manifest.json"), manifest_bytes)
            outgoing.writestr(clean_info("MODEL_LICENSE.txt"), license_file.read_bytes())

    print(output)


if __name__ == "__main__":
    main()

