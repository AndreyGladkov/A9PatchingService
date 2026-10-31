#!/bin/sh
set -eu

adb shell "pm uninstall-system-updates com.lmqr.ha9_comp_service" || true
adb shell "su -c 'rm -r /data/adb/modules/a9service_override'"
echo "Module removed. Reboot the phone to bring back the original service."
