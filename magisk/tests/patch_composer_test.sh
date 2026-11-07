#!/bin/sh
# Usage: A9_COMPOSER_ORIGINAL=/path/to/vendor.qti.hardware.display.composer-service sh patch_composer_test.sh
# The Hisense binary is not in the repository: pull it from a phone with the stock vendor.
set -u

HERE=$(cd "$(dirname "$0")" && pwd)
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

if ! command -v sha256sum >/dev/null 2>&1; then
    mkdir "$WORK/bin"
    printf '#!/bin/sh\nexec shasum -a 256 "$@"\n' > "$WORK/bin/sha256sum"
    chmod +x "$WORK/bin/sha256sum"
    PATH="$WORK/bin:$PATH"
fi

. "$HERE/../a9_eink_composer/patch_composer.sh"

failures=0
check() {
    if [ "$2" = "$3" ]; then
        echo "ok   $1"
    else
        echo "FAIL $1: expected $3, got $2"
        failures=$((failures + 1))
    fi
}

ORIGINAL=${A9_COMPOSER_ORIGINAL:?set A9_COMPOSER_ORIGINAL to the stock composer}
check "input is the stock composer" "$(composer_sha256 "$ORIGINAL")" "$COMPOSER_ORIGINAL_SHA256"

patch_composer "$ORIGINAL" "$WORK/patched"
check "stock composer is patched" "$?" 0
check "patched composer has the known hash" "$(composer_sha256 "$WORK/patched")" "$COMPOSER_PATCHED_SHA256"
check "source is left untouched" "$(composer_sha256 "$ORIGINAL")" "$COMPOSER_ORIGINAL_SHA256"

patch_composer "$WORK/patched" "$WORK/again"
check "already patched composer is copied" "$?" 0
check "copy keeps the patched hash" "$(composer_sha256 "$WORK/again")" "$COMPOSER_PATCHED_SHA256"

head -c 4096 "$ORIGINAL" > "$WORK/other"
patch_composer "$WORK/other" "$WORK/rejected"
check "unknown composer is rejected" "$?" 2

[ "$failures" -eq 0 ] && echo "patch_composer: all tests passed"
exit "$failures"
