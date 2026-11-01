#!/bin/sh
# Builds kiosk.apk with the plain Android SDK tools (no Gradle).
# Needs ANDROID_HOME (platform 34 + build-tools 34.0.0) and the signing key (see README: the key is NOT in this repo).
set -e
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-/opt/android-sdk}"
BT="$SDK/build-tools/34.0.0"
JAR="$SDK/platforms/android-34/android.jar"
KEYSTORE="${KIOSK_KEYSTORE:-/home/user/life-automations/kiosk/debug.keystore}"
[ -f "$KEYSTORE" ] || { echo "Signing key not found: $KEYSTORE (set KIOSK_KEYSTORE)" >&2; exit 1; }
OUT=build
rm -rf "$OUT" && mkdir -p "$OUT/classes"
javac -g -Xlint:-options --release 8 -classpath "$JAR" -d "$OUT/classes" app/src/dk/mrsilly/kiosk/*.java
"$BT/d8" --release --lib "$JAR" --output "$OUT" "$OUT"/classes/dk/mrsilly/kiosk/*.class
"$BT/aapt2" compile --dir app/res -o "$OUT/res.zip"
"$BT/aapt2" link -o "$OUT/base.apk" -I "$JAR" --manifest app/AndroidManifest.xml --version-code "$(cat VERSION)" --version-name "1.$(cat VERSION)" "$OUT/res.zip"
(cd "$OUT" && zip -q -u base.apk classes.dex)
"$BT/zipalign" -f 4 "$OUT/base.apk" "$OUT/aligned.apk"
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass pass:kiosk123 --out kiosk.apk "$OUT/aligned.apk"
"$BT/apksigner" verify kiosk.apk && ls -l kiosk.apk
