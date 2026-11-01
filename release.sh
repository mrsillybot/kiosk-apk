#!/bin/sh
# Builds and publishes: kiosk.apk + version.json in this repo's root are what the tablet's Updater downloads.
# Usage: ./release.sh bump   -> raise VERSION by one, build, commit, push (do this for every change the tablet should get)
#        ./release.sh        -> build and publish the current VERSION
set -e
cd "$(dirname "$0")"
[ "$1" = "bump" ] && echo $(( $(cat VERSION) + 1 )) > VERSION
./build.sh
V=$(cat VERSION)
printf '{"versionCode": %s, "apk": "kiosk.apk"}\n' "$V" > version.json
git add -A && git commit -qm "Kiosk v$V" && git pull --rebase -q && git push -q origin HEAD:main
echo "published v$V"
