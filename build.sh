#!/usr/bin/env bash
# Builds Nightfall.apk with the Android tools from Ubuntu's packages
# (apt install aapt apksigner zipalign dalvik-exchange android-sdk-platform-23).
set -euo pipefail
cd "$(dirname "$0")"

SDK=/usr/lib/android-sdk
ANDROID_JAR=$SDK/platforms/android-23/android.jar
BT=$SDK/build-tools/debian
VERSION_CODE=${VERSION_CODE:-3}
VERSION_NAME=${VERSION_NAME:-2.1}
KEYSTORE=${KEYSTORE:-$HOME/.nightfall/release.keystore}

rm -rf build && mkdir -p build/gen build/classes

"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -I "$ANDROID_JAR" --manifest AndroidManifest.xml -A assets \
  --min-sdk-version 22 --target-sdk-version 28 \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
  --java build/gen -o build/unsigned.apk build/res.zip

javac -source 8 -target 8 -bootclasspath "$ANDROID_JAR" -Xlint:-options -encoding UTF-8 \
  -d build/classes $(find src build/gen -name '*.java')

"$BT/dx" --dex --min-sdk-version=22 --output=build/classes.dex build/classes
(cd build && zip -q unsigned.apk classes.dex)

zipalign -f 4 build/unsigned.apk build/aligned.apk

if [ ! -f "$KEYSTORE" ]; then
  mkdir -p "$(dirname "$KEYSTORE")"
  keytool -genkeypair -keystore "$KEYSTORE" -storepass nightfall -keypass nightfall \
    -alias nightfall -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Nightfall Launcher" >/dev/null 2>&1
fi
apksigner sign --ks "$KEYSTORE" --ks-pass pass:nightfall --key-pass pass:nightfall \
  --out build/Nightfall.apk build/aligned.apk
apksigner verify build/Nightfall.apk
echo "Built build/Nightfall.apk"
