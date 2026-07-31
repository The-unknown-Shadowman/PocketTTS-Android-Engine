#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"

if find . \
  -path ./.git -prune -o \
  -path ./.gradle -prune -o \
  -path ./app/build -prune -o \
  -path ./app/.cxx -prune -o \
  -path ./app/src/main/jniLibs -prune -o \
  -path ./app/src/main/third_party -prune -o \
  -path ./release-assets -prune -o \
  -type f \( \
  -iname '*.wav' -o -iname '*.wave' -o -iname '*.mp3' -o -iname '*.flac' \
  -o -iname '*.onnx' -o -iname '*.safetensors' -o -iname '*.apk' \
  \) -print | grep -q .; then
  echo "Refusing publication: generated model, APK, or audio files are present." >&2
  exit 1
fi

if [ -f local.properties ]; then
  echo "Refusing publication: local.properties is present." >&2
  exit 1
fi

if grep -RIl --exclude-dir=.git --exclude-dir=.gradle --exclude-dir=build \
  --exclude-dir=.cxx --exclude-dir=jniLibs --exclude-dir=third_party \
  --exclude-dir=release-assets --exclude='check_publishable.sh' \
  -E '(/mnt/[a-z]/|/home/[^/]+/|[A-Za-z]:[/\\]Users[/\\][^/\\]+|sdk\.dir=|ndk\.dir=)' . | grep -q .; then
  echo "Refusing publication: a local computer path is present." >&2
  exit 1
fi

if grep -RIl --exclude-dir=.git --exclude-dir=.gradle --exclude-dir=build \
  --exclude-dir=.cxx --exclude-dir=jniLibs --exclude-dir=third_party \
  --exclude-dir=release-assets --exclude='check_publishable.sh' \
  -E '(BEGIN (RSA|OPENSSH|EC|DSA) PRIVATE KEY|gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|AIza[A-Za-z0-9_-]{30,}|sk-[A-Za-z0-9_-]{20,})' . | grep -q .; then
  echo "Refusing publication: a possible credential is present." >&2
  exit 1
fi

echo "Publishability checks passed. Review git diff and licenses before pushing."
