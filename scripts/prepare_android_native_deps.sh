#!/usr/bin/env bash
set -euo pipefail

# Downloads the ABI-specific ONNX Runtime binary and the matching public headers.
# It is intentionally explicit: no opaque binary is committed to the repository.
ROOT=$(cd "$(dirname "$0")/.." && pwd)
# Runtime updates are tested explicitly because 1.21.1, 1.22.0, 1.26.0, and
# 1.28.0 caused either SIGILL or severe 24-layer regressions on one tested
# Snapdragon 8 Elite device. Import only model packs from trusted sources.
VERSION=1.20.0
AAR_SHA256=07a8f71ef890afed8c6087a56220e6d558a492804276ee2dd7cb7f6262242027
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
# Retry temporary HTTP/connection failures without accepting unchecked data.
DOWNLOAD_OPTIONS=(--fail --location --retry 4 --retry-all-errors --retry-delay 3
  --retry-max-time 180 --connect-timeout 15 --max-time 180)

mkdir -p "$ROOT/app/src/main/jniLibs/arm64-v8a" "$ROOT/app/src/main/third_party"
curl "${DOWNLOAD_OPTIONS[@]}" "https://repo1.maven.org/maven2/com/microsoft/onnxruntime/onnxruntime-android/${VERSION}/onnxruntime-android-${VERSION}.aar" -o "$TMP/ort.aar"
echo "$AAR_SHA256  $TMP/ort.aar" | sha256sum --check --status
unzip -p "$TMP/ort.aar" jni/arm64-v8a/libonnxruntime.so > "$ROOT/app/src/main/jniLibs/arm64-v8a/libonnxruntime.so"
# The checked AAR includes the public headers matching its native binary.
# Extract both from that one artifact instead of depending on a source archive.
unzip -q "$TMP/ort.aar" 'headers/*.h' -d "$TMP"
rm -rf "$ROOT/app/src/main/third_party/onnxruntime"
HEADER_DIR="$ROOT/app/src/main/third_party/onnxruntime/include/onnxruntime/core/session"
mkdir -p "$HEADER_DIR"
cp "$TMP"/headers/*.h "$HEADER_DIR/"
echo "ONNX Runtime Android ${VERSION} (arm64-v8a) is ready."
