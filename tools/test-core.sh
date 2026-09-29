#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p out/core-tests
find app/src/main/java/io/github/ling/randombubble/core tests/fixtures -name '*.java' > out/core-tests/sources.txt
printf '%s\n' tests/CoreSelfTest.java >> out/core-tests/sources.txt
javac --release 8 -encoding UTF-8 -d out/core-tests @out/core-tests/sources.txt
java -ea -cp out/core-tests CoreSelfTest
