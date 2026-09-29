#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
mkdir -p .tools
ZIP=.tools/gradle-8.9-bin.zip
SHA=d725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab
if [[ ! -x .tools/gradle-8.9/bin/gradle ]]; then
  if [[ ! -f "$ZIP" ]]; then
    curl --fail --location --proto '=https' --tlsv1.2 --retry 2 'https://services.gradle.org/distributions/gradle-8.9-bin.zip' -o "$ZIP.part"
    mv "$ZIP.part" "$ZIP"
  fi
  if command -v sha256sum >/dev/null; then ACTUAL="$(sha256sum "$ZIP" | awk '{print $1}')"
  else ACTUAL="$(shasum -a 256 "$ZIP" | awk '{print $1}')"; fi
  [[ "$ACTUAL" == "$SHA" ]] || { echo 'Gradle SHA-256 mismatch; aborting.' >&2; exit 1; }
  unzip -q -o "$ZIP" -d .tools
fi
exec .tools/gradle-8.9/bin/gradle "$@"
