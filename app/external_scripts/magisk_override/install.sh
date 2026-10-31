#!/bin/sh
set -eu

APK=${1:?usage: install.sh path/to/app-release.apk}
HERE=$(cd "$(dirname "$0")" && pwd)
MODULE=/data/adb/modules/a9service_override
STAGING=/data/local/tmp/a9service_override

adb shell "mkdir -p $STAGING"
adb push "$APK" "$STAGING/a9service.apk"
adb push "$HERE/module.prop" "$STAGING/module.prop"
adb shell "su -c '
    set -e
    mkdir -p $MODULE/system/priv-app
    cp $STAGING/module.prop $MODULE/module.prop
    cp $STAGING/a9service.apk $MODULE/system/priv-app/a9service.apk
    chmod 644 $MODULE/module.prop $MODULE/system/priv-app/a9service.apk
    chcon u:object_r:system_file:s0 $MODULE/system/priv-app/a9service.apk
    rm -r $STAGING
'"
echo "Module installed. Reboot the phone to replace the system copy of the service."
