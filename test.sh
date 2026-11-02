#!/bin/sh
# Runs the logic tests (Config) on the plain JVM against android.jar's stubs. Needs ANDROID_HOME like build.sh.
set -e
cd "$(dirname "$0")"
JAR="${ANDROID_HOME:-/opt/android-sdk}/platforms/android-34/android.jar"
OUT=build/test
rm -rf "$OUT" && mkdir -p "$OUT"
javac -Xlint:-options --release 8 -classpath "$JAR" -d "$OUT" app/src/dk/mrsilly/kiosk/Config.java app/src/dk/mrsilly/kiosk/Reporter.java app/src/dk/mrsilly/kiosk/ListsRepo.java tests/dk/mrsilly/kiosk/ConfigTest.java
java -cp "$OUT:$JAR" dk.mrsilly.kiosk.ConfigTest 2>&1 | grep -v JAVA_TOOL_OPTIONS
