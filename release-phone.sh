#!/bin/sh
# Builds and publishes the phone app: phone.apk + phone-version.json in the repo root are what the phone's Updater downloads.
# Usage: ./release-phone.sh bump   -> raise PHONE_VERSION by one, build, commit, push
set -e
cd "$(dirname "$0")"
[ "$1" = "bump" ] && echo $(( $(cat PHONE_VERSION) + 1 )) > PHONE_VERSION
./test.sh
./build-phone.sh
V=$(cat PHONE_VERSION)
printf '{"versionCode": %s, "apk": "phone.apk"}\n' "$V" > phone-version.json
git add -A && git commit -qm "Phone v$V" && git pull --rebase -q && git push -q origin HEAD:main
echo "published phone v$V"
