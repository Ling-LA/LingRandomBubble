#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
: "${ANDROID_HOME:?Set ANDROID_HOME to an Android SDK with API 35 installed.}"
[[ -f "$ANDROID_HOME/platforms/android-35/android.jar" ]] || { echo 'SDK API 35 is missing.' >&2; exit 1; }
bash tools/test-core.sh
bash tools/gradle-bootstrap.sh --no-daemon --console=plain :app:assembleDebug :app:lintDebug
mkdir -p out
cp app/build/outputs/apk/debug/app-debug.apk out/LingRandomBubble-0.1.50-debug.apk
echo 'Created: out/LingRandomBubble-0.1.50-debug.apk (embedded QQ settings; device results: docs/VALIDATION-2026-10-02-0.1.50.md)'
