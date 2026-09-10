#!/usr/bin/env bash
# Run inside the terminal being tested, not from an unrelated adb shell/PTY.
# Usage: bash kitty-keyboard-check.sh LOG [FLAGS [SECONDS]]
# With FLAGS, record raw keyboard bytes after checking negotiation. Requires coreutils.
set -eu

log=${1:?Usage: bash kitty-keyboard-check.sh LOG [FLAGS [SECONDS]]}
flags=${2:-}
seconds=${3:-20}
saved=$(stty -g)
exec 3>"$log"
cleanup() {
    printf '\033[=0u\033[?1049l\033[=0u'
    stty "$saved"
}
trap cleanup EXIT
trap 'exit 130' INT TERM HUP
stty raw -echo

check() {
    local command=$1 expected=$2 actual
    printf '%b\033[?u' "$command"
    if ! IFS= read -r -d u -t 3 actual; then
        printf 'FAIL timeout querying flags after %q\n' "$command" >&3
        exit 1
    fi
    if [[ "$actual" != $'\033[?'"$expected" ]]; then
        printf 'FAIL expected flags=%s got=%q after %q\n' "$expected" "$actual" "$command" >&3
        exit 1
    fi
    printf 'PASS flags=%s after %q\n' "$expected" "$command" >&3
}

check '\033[=0u' 0
check '\033[=5u' 5
check '\033[=10;2u' 15
check '\033[=6;3u' 9
check '\033[=65537u' 1
check '\033[=31;4u' 1
check '\033[>8u\033[>31u' 31
check '\033[<2u' 1
check '\033[<u' 0
check '\033[>4u\033[?1049h' 0
check '\033[>8u\033[?1049l' 4
check '\033[?1049h' 8
check '\033[!p' 0
check '\033[?1049l' 0
check '\033[=1:2u\033[=999999999999999999999999u' 0
printf 'PASS negotiation, stacks, screen isolation, soft reset, malformed input\n' >&3

if [[ -n "$flags" ]]; then
    [[ "$flags" =~ ^[0-9]+$ && "$seconds" =~ ^[0-9]+$ ]] || exit 2
    check "\033[=$flags"'u' "$flags"
    printf '\r\nKitty recorder: flags=%s, %s seconds. Press keys now.\r\n' "$flags" "$seconds"
    printf 'RECORD flags=%s seconds=%s\n' "$flags" "$seconds" >&3
    # Preserve NUL and arbitrary UTF-8; shell read/string variables cannot do this.
    timeout --foreground "$seconds" dd bs=256 status=none | od -An -v -tx1 >&3
    printf 'END RECORD\n' >&3
fi
printf '\r\nKitty check complete. Results: %s\r\n' "$log"
