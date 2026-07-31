#!/usr/bin/env bash
set -euo pipefail

# Downloads the ABI-specific ONNX Runtime binary and the matching public headers.
# It is intentionally explicit: no opaque binary is committed to the repository.
ROOT=$(cd "$(dirname "$0")/.." && pwd)
# 1.23.x selects an unsupported CPU instruction in the voice encoder on the
# Snapdragon 8 Elite (SIGILL). 1.20.0 uses the compatible ARM64 kernel path.
# (1.20.1 did not publish an Android AAR.)
VERSION=1.20.0
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

mkdir -p "$ROOT/app/src/main/jniLibs/arm64-v8a" "$ROOT/app/src/main/third_party"
curl -fL "https://repo1.maven.org/maven2/com/microsoft/onnxruntime/onnxruntime-android/${VERSION}/onnxruntime-android-${VERSION}.aar" -o "$TMP/ort.aar"
unzip -p "$TMP/ort.aar" jni/arm64-v8a/libonnxruntime.so > "$ROOT/app/src/main/jniLibs/arm64-v8a/libonnxruntime.so"
curl -fL "https://github.com/microsoft/onnxruntime/archive/refs/tags/v${VERSION}.tar.gz" -o "$TMP/ort.tar.gz"
tar -xzf "$TMP/ort.tar.gz" -C "$TMP"
rm -rf "$ROOT/app/src/main/third_party/onnxruntime"
mkdir -p "$ROOT/app/src/main/third_party/onnxruntime"
cp -R "$TMP/onnxruntime-${VERSION}/include" "$ROOT/app/src/main/third_party/onnxruntime/include"
echo "ONNX Runtime Android ${VERSION} (arm64-v8a) is ready."
