#!/usr/bin/env bash
# Renders Nightfall's real screens with Robolectric (no device needed) into tools/screenshots/out/.
# Needs Maven and the APK resources from ../../build.sh.
set -euo pipefail
cd "$(dirname "$0")"
JAR=android-all-instrumented-9-robolectric-4913185-2-i7.jar
mkdir -p deps out
if [ ! -f "deps/$JAR" ]; then
  curl -fL --retry 4 -o "deps/$JAR" \
    "https://repo1.maven.org/maven2/org/robolectric/android-all-instrumented/9-robolectric-4913185-2-i7/$JAR"
fi
[ -f ../../build/unsigned.apk ] || ../../build.sh
mvn -q -B test
ls out
