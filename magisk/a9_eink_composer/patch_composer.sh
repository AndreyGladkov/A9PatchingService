# Sourced by customize.sh (Magisk busybox ash) and by the test (POSIX sh).

COMPOSER_ORIGINAL_SHA256=8c89557bf47b57b1687b9fbcc3e55bedbee175d20f80d14bc5317fcf893df5ed
COMPOSER_PATCHED_SHA256=36abf91f8263eb0660e1582cb3824db58a8a94e24cd57bfe68fbba0a055ec542

composer_sha256() {
    sha256sum "$1" | cut -d ' ' -f 1
}

write_bytes() {
    printf "$2" | dd of="$1" bs=1 seek="$3" conv=notrunc 2>/dev/null
}

# The composer runs a mode-transition wave whenever the low byte of epd_display_mode changes.
# 0x1a6620 is the b.ne that enters it; it now goes to 0x1e7e00 in the zero tail after .plt (mapped
# RX), which skips the transition when bit 0x800 is set and otherwise rejoins the original path:
#   1a6620: b.ne 1e7e00
#   1e7e00: tbnz w25, #11, 1e7e08
#   1e7e04: b    1a662c
#   1e7e08: b    1a6624
# Bytes are octal escapes and the zero byte at 0x1e7e01 is left as it is, because not every
# printf emits NUL.
apply_composer_patch() {
    write_bytes "$1" '\001\277\040\124' $((0x1a6620))
    write_bytes "$1" '\131' $((0x1e7e00))
    write_bytes "$1" '\130\067\012\372\376\027\007\372\376\027' $((0x1e7e02))
}

# Copies the composer from $1 to $2, patching it unless it is already patched (an earlier version
# of this module still mounted over /vendor). Fails on any other build of the composer.
patch_composer() {
    case "$(composer_sha256 "$1")" in
        "$COMPOSER_PATCHED_SHA256")
            cp "$1" "$2" || return 1
            ;;
        "$COMPOSER_ORIGINAL_SHA256")
            cp "$1" "$2" || return 1
            apply_composer_patch "$2"
            ;;
        *)
            return 2
            ;;
    esac
    [ "$(composer_sha256 "$2")" = "$COMPOSER_PATCHED_SHA256" ] || return 3
}
