#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -lt 3 ] || [ "$#" -gt 4 ]; then
  echo "Usage: $0 LANGUAGE BCP47 DISPLAY_NAME [fp32|int8]" >&2
  echo "Example: $0 german de-DE 'German (FP32)' fp32" >&2
  exit 2
fi

ROOT=$(cd "$(dirname "$0")/.." && pwd)
LANGUAGE=$1
LANGUAGE_TAG=$2
DISPLAY_NAME=$3
PRECISION=${4:-fp32}

if [ "$PRECISION" != "fp32" ] && [ "$PRECISION" != "int8" ]; then
  echo "Precision must be fp32 or int8." >&2
  exit 2
fi

command -v uv >/dev/null || { echo "uv is required." >&2; exit 1; }
command -v ffmpeg >/dev/null || { echo "ffmpeg is required." >&2; exit 1; }

PACK_ID=$(printf '%s-%s' "$LANGUAGE" "$PRECISION" | tr '[:upper:]_' '[:lower:]-' | tr -cd 'a-z0-9-')
VENV="$ROOT/.venv-model-export"
OUT="$ROOT/build/onnx/$LANGUAGE"
PACK="$ROOT/build/model-packs/pockettts-${PACK_ID}.zip"

if [ ! -x "$VENV/bin/python" ]; then
  uv venv "$VENV" --python 3.12
fi

# shellcheck disable=SC1091
source "$VENV/bin/activate"
uv pip install --index-url https://download.pytorch.org/whl/cpu torch
uv pip install -e "$ROOT/vendor/pocket-tts" onnx onnxruntime

EXPORT_ARGS=(--language "$LANGUAGE" --output-dir "$OUT")
if [ "$PRECISION" = "fp32" ]; then
  EXPORT_ARGS+=(--no-quantize)
fi
python "$ROOT/vendor/PocketTTS.cpp/export_onnx.py" "${EXPORT_ARGS[@]}"

python "$ROOT/scripts/build_model_pack.py" \
  --models-dir "$OUT" \
  --output "$PACK" \
  --id "$PACK_ID" \
  --name "$DISPLAY_NAME" \
  --language "$LANGUAGE_TAG" \
  --precision "$PRECISION" \
  --temperature 0.7 \
  --lsd-steps 1 \
  --threads 2 \
  --license-file "$ROOT/MODEL_LICENSE.md" \
  --source-url "https://huggingface.co/kyutai/pocket-tts"

echo "Model pack: $PACK"

