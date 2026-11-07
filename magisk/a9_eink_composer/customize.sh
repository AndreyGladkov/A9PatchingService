COMPOSER=/vendor/bin/hw/vendor.qti.hardware.display.composer-service
TARGET=$MODPATH/system$COMPOSER

. "$MODPATH/patch_composer.sh"

ui_print "- Patching the composer from this phone"
mkdir -p "${TARGET%/*}"
patch_composer "$COMPOSER" "$TARGET"
case $? in
    0) ;;
    2) abort "! Unknown composer build: only the Hisense A9 (HLTE556N) L2037 vendor is supported" ;;
    *) abort "! Patching failed, nothing was installed" ;;
esac
rm "$MODPATH/patch_composer.sh"

set_perm "$TARGET" 0 2000 0755 u:object_r:hal_graphics_composer_default_exec:s0
ui_print "- Done. Reboot, then turn on Scroll in the A9 Service menu"
