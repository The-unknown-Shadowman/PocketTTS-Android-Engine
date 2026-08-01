#!/usr/bin/env python3
"""Update generation defaults in an existing model-pack ZIP without changing its payload."""

from __future__ import annotations

import argparse
import json
import os
import shutil
import tempfile
import zipfile
from pathlib import Path


def arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("pack", type=Path)
    parser.add_argument("--temperature", type=float, required=True)
    parser.add_argument("--lsd-steps", type=int, required=True)
    parser.add_argument("--threads", type=int, required=True)
    return parser.parse_args()


def main() -> None:
    args = arguments()
    pack = args.pack.expanduser().resolve()
    if not pack.is_file():
        raise SystemExit(f"Model pack does not exist: {pack}")
    if not 0.0 <= args.temperature <= 2.0:
        raise SystemExit("temperature must be between 0.0 and 2.0")
    if not 1 <= args.lsd_steps <= 8 or not 1 <= args.threads <= 8:
        raise SystemExit("lsd-steps and threads must be between 1 and 8")

    with zipfile.ZipFile(pack, "r") as incoming:
        try:
            manifest_info = incoming.getinfo("manifest.json")
        except KeyError as error:
            raise SystemExit("Model pack has no manifest.json") from error
        manifest = json.loads(incoming.read(manifest_info))
        manifest["temperature"] = args.temperature
        manifest["lsdSteps"] = args.lsd_steps
        manifest["threads"] = args.threads
        manifest_bytes = (json.dumps(manifest, ensure_ascii=False, indent=2) + "\n").encode()

        handle, temporary_name = tempfile.mkstemp(prefix=f".{pack.name}.", suffix=".tmp", dir=pack.parent)
        os.close(handle)
        temporary = Path(temporary_name)
        try:
            with zipfile.ZipFile(temporary, "w", allowZip64=True) as outgoing:
                for info in incoming.infolist():
                    if info.filename == "manifest.json":
                        outgoing.writestr(info, manifest_bytes)
                    else:
                        with incoming.open(info, "r") as source, outgoing.open(info, "w") as target:
                            shutil.copyfileobj(source, target, length=1024 * 1024)
            os.replace(temporary, pack)
        finally:
            temporary.unlink(missing_ok=True)

    print(
        f"Updated {pack}: temperature={args.temperature}, "
        f"lsdSteps={args.lsd_steps}, threads={args.threads}"
    )


if __name__ == "__main__":
    main()
