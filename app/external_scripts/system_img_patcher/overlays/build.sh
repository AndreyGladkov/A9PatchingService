#!/bin/bash
# Usage: build.sh <platform.pk8> <platform.x509.pem>
# Builds the overlays next to patch_system_img.py, where the patcher picks them up.
set -euo pipefail
KEY=$1 CERT=$2
SDK=${ANDROID_HOME:-$HOME/Library/Android/sdk}
TOOLS=$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)
ANDROID_JAR=$(ls -d "$SDK"/platforms/android-36/android.jar)
HERE=$(cd "$(dirname "$0")" && pwd)
OUT=$(dirname "$HERE")
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
for name in eink-statusbar-icons eink-battery-text; do
    "$TOOLS/aapt2" compile --dir "$HERE/$name/res" -o "$WORK/$name.zip"
    "$TOOLS/aapt2" link -I "$ANDROID_JAR" --manifest "$HERE/$name/AndroidManifest.xml" --min-sdk-version 30 \
        -o "$WORK/$name-unsigned.apk" "$WORK/$name.zip"
    "$TOOLS/zipalign" -f 4 "$WORK/$name-unsigned.apk" "$WORK/$name-aligned.apk"
    "$TOOLS/apksigner" sign --v4-signing-enabled false --key "$KEY" --cert "$CERT" --out "$OUT/a9-$name.apk" "$WORK/$name-aligned.apk"
    echo "$OUT/a9-$name.apk"
done
