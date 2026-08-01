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

VOICE_ID=""
VOICE_NAME=""
TEMPERATURE=0.7
THREADS=2
case "$LANGUAGE" in
  english|english_24l) VOICE_ID=alba; VOICE_NAME=Alba; TEMPERATURE=0.3 ;;
  german|german_24l) VOICE_ID=juergen; VOICE_NAME=Jürgen ;;
  italian|italian_24l) VOICE_ID=giovanni; VOICE_NAME=Giovanni ;;
  portuguese|portuguese_24l) VOICE_ID=rafael; VOICE_NAME=Rafael ;;
  spanish|spanish_24l) VOICE_ID=lola; VOICE_NAME=Lola ;;
esac

# FP32 defaults measured on an ARM64 Android 16 device. Keep the established
# conservative defaults for unbenchmarked INT8 variants.
if [ "$PRECISION" = "fp32" ]; then
  case "$LANGUAGE" in
    german) TEMPERATURE=0.5; THREADS=4 ;;
    german_24l) TEMPERATURE=0.3; THREADS=3 ;;
  esac
fi

BUILD_ARGS=(
  --models-dir "$OUT" \
  --output "$PACK" \
  --id "$PACK_ID" \
  --name "$DISPLAY_NAME" \
  --language "$LANGUAGE_TAG" \
  --precision "$PRECISION" \
  --temperature "$TEMPERATURE" \
  --lsd-steps 1 \
  --threads "$THREADS" \
  --license-file "$ROOT/MODEL_LICENSE.md" \
  --source-url "https://huggingface.co/kyutai/pocket-tts"
)

if [ -n "$VOICE_ID" ]; then
  VOICE_SOURCE=$(python -c "from pocket_tts.utils.utils import download_if_necessary, _ORIGINS_OF_PREDEFINED_VOICES; print(download_if_necessary(_ORIGINS_OF_PREDEFINED_VOICES['$VOICE_ID']))")
  BUILD_ARGS+=(
    --voice-attribution-file "$ROOT/VOICE_ATTRIBUTION.md"
    --voice "$VOICE_ID=$VOICE_NAME=$VOICE_SOURCE"
  )
fi

python "$ROOT/scripts/build_model_pack.py" "${BUILD_ARGS[@]}"

echo "Model pack: $PACK"
