#!/bin/sh
# Builds phone.apk (the phone app "Mr Silly": brief + indkøb/opgaver/tal) with the plain Android SDK tools. Shares ListsRepo/ListsBridge/Updater/ClaudeBar
# and the built-in page app/assets/lister.html with the kiosk app. Same signing key as the kiosk (see README).
set -e
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-/opt/android-sdk}"
BT="$SDK/build-tools/34.0.0"
JAR="$SDK/platforms/android-34/android.jar"
KEYSTORE="${KIOSK_KEYSTORE:-/home/user/life-automations/kiosk/debug.keystore}"
[ -f "$KEYSTORE" ] || { echo "Signing key not found: $KEYSTORE (set KIOSK_KEYSTORE)" >&2; exit 1; }
K=app/src/dk/mrsilly/kiosk
OUT=build-phone
rm -rf "$OUT" && mkdir -p "$OUT/classes"
javac -g -Xlint:-options --release 8 -classpath "$JAR" -d "$OUT/classes" $K/ListsRepo.java $K/ListsBridge.java $K/Updater.java $K/UpdateReceiver.java $K/ClaudeBar.java phone/src/dk/mrsilly/kiosk/*.java
"$BT/d8" --release --lib "$JAR" --output "$OUT" "$OUT"/classes/dk/mrsilly/kiosk/*.class
"$BT/aapt2" compile --dir app/res -o "$OUT/res.zip"
"$BT/aapt2" link -o "$OUT/base.apk" -I "$JAR" -A app/assets --manifest phone/AndroidManifest.xml --version-code "$(cat PHONE_VERSION)" --version-name "1.$(cat PHONE_VERSION)" "$OUT/res.zip"
(cd "$OUT" && zip -q -u base.apk classes.dex)
"$BT/zipalign" -f 4 "$OUT/base.apk" "$OUT/aligned.apk"
"$BT/apksigner" sign --v4-signing-enabled false --ks "$KEYSTORE" --ks-pass pass:kiosk123 --out phone.apk "$OUT/aligned.apk"
"$BT/apksigner" verify phone.apk && ls -l phone.apk
