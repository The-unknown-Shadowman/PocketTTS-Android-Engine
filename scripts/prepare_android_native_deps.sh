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
SOURCE_SHA256=5d0aaced52921b86f3f2b2eaac18cbfc96184d73e83baf2553c94d9d306c4de9
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

mkdir -p "$ROOT/app/src/main/jniLibs/arm64-v8a" "$ROOT/app/src/main/third_party"
curl -fL "https://repo1.maven.org/maven2/com/microsoft/onnxruntime/onnxruntime-android/${VERSION}/onnxruntime-android-${VERSION}.aar" -o "$TMP/ort.aar"
echo "$AAR_SHA256  $TMP/ort.aar" | sha256sum --check --status
unzip -p "$TMP/ort.aar" jni/arm64-v8a/libonnxruntime.so > "$ROOT/app/src/main/jniLibs/arm64-v8a/libonnxruntime.so"
curl -fL "https://github.com/microsoft/onnxruntime/archive/refs/tags/v${VERSION}.tar.gz" -o "$TMP/ort.tar.gz"
echo "$SOURCE_SHA256  $TMP/ort.tar.gz" | sha256sum --check --status
tar -xzf "$TMP/ort.tar.gz" -C "$TMP"
rm -rf "$ROOT/app/src/main/third_party/onnxruntime"
mkdir -p "$ROOT/app/src/main/third_party/onnxruntime"
cp -R "$TMP/onnxruntime-${VERSION}/include" "$ROOT/app/src/main/third_party/onnxruntime/include"
echo "ONNX Runtime Android ${VERSION} (arm64-v8a) is ready."
