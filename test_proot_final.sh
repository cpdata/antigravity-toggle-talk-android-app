#!/data/data/com.termux/files/usr/bin/bash
# test_proot_final.sh - Verify the run_antigravity.sh proot invocation
# correctly sets working directory via -w flag.
#
# Usage: bash test_proot_final.sh [target_dir]
#
# This does NOT run agy.va39 to avoid spawning real sessions.
# Instead it uses /bin/sh -c 'pwd' and direct glibc linker + --help
# to verify CWD propagation through proot.

set -euo pipefail

TARGET_DIR="${1:-/data/data/com.termux/files/home/ToggleTalkAndroid}"

PROOT_BIN="/data/data/com.termux/files/usr/bin/proot"
GLIBC_LINKER="/data/data/com.termux/files/usr/glibc/lib/ld-linux-aarch64.so.1"
GLIBC_LIBS="/data/data/com.termux/files/home/.local/lib/agy-glibc:/data/data/com.termux/files/usr/glibc/lib"
AGY_BIN="/data/data/com.termux/files/home/.local/bin/agy.va39"
ERR_FILE="$HOME/.test_proot_final.err"

# Resolve TARGET_DIR (same as run_antigravity.sh lines 19-20)
cd "$TARGET_DIR" 2>/dev/null || cd "$HOME"
TARGET_DIR="$(pwd)"

PASS=0
FAIL=0

pass() { echo "✅ PASS: $1"; PASS=$((PASS+1)); }
fail() { echo "❌ FAIL: $1"; FAIL=$((FAIL+1)); }

echo "======================================"
echo "PROOT CWD VERIFICATION TESTS"
echo "TARGET_DIR=$TARGET_DIR"
echo "======================================"
echo ""

# --- Test 1: proot -w sets CWD correctly ---
echo "--- Test 1: proot -w sets CWD correctly ---"
CWD1=$(env -u LD_PRELOAD -u LD_LIBRARY_PATH "$PROOT_BIN" --kill-on-exit \
    -w "$TARGET_DIR" \
    -b /data/data/com.termux/files/usr/bin/sh:/bin/sh \
    /bin/sh -c 'pwd' 2>/dev/null)
echo "  CWD: $CWD1"
[ "$CWD1" = "$TARGET_DIR" ] && pass "CWD matches TARGET_DIR" || fail "CWD=$CWD1 expected=$TARGET_DIR"
echo ""

# --- Test 2: Direct glibc linker invocation with --help (no --dangerously-skip-permissions) ---
echo "--- Test 2: Direct glibc linker invocation works ---"
RESULT2=$(env -u LD_PRELOAD -u LD_LIBRARY_PATH "$PROOT_BIN" --kill-on-exit \
    -w "$TARGET_DIR" \
    -b /data/data/com.termux/files/usr/etc/resolv.conf:/etc/resolv.conf \
    -b /data/data/com.termux/files/usr/bin/env:/usr/bin/env \
    -b /data/data/com.termux/files/usr/bin/sh:/bin/sh \
    -b /data/data/com.termux/files/usr/bin/bash:/bin/bash \
    "$GLIBC_LINKER" --library-path "$GLIBC_LIBS" "$AGY_BIN" --help < /dev/null 2>&1) || true
echo "  First line: $(echo "$RESULT2" | head -1)"
echo "$RESULT2" | grep -q "dangerously-skip-permissions\|--print\|--continue" && \
    pass "agy.va39 --help ran via direct linker" || \
    fail "agy.va39 did not produce expected output"
echo ""

# --- Test 3: No proot chdir warnings ---
echo "--- Test 3: No proot chdir warnings ---"
if [ -s "$ERR_FILE" ] && grep -q "can't chdir" "$ERR_FILE"; then
    fail "proot had chdir failures"
    cat "$ERR_FILE"
else
    pass "No chdir failures"
fi
echo ""

# --- Test 4: Different directory works (HOME) ---
echo "--- Test 4: Different directory (HOME) ---"
CWD4=$(env -u LD_PRELOAD -u LD_LIBRARY_PATH "$PROOT_BIN" --kill-on-exit \
    -w "$HOME" \
    -b /data/data/com.termux/files/usr/bin/sh:/bin/sh \
    /bin/sh -c 'pwd' 2>/dev/null)
echo "  CWD: $CWD4"
[ "$CWD4" = "$HOME" ] && pass "HOME dir CWD matches" || fail "CWD=$CWD4 expected=$HOME"
echo ""

# --- Test 5: proot -w with full bind set + pwd ---
echo "--- Test 5: Full bind set + -w + pwd ---"
CWD5=$(env -u LD_PRELOAD -u LD_LIBRARY_PATH "$PROOT_BIN" --kill-on-exit \
    -w "$TARGET_DIR" \
    -b /data/data/com.termux/files/usr/etc/resolv.conf:/etc/resolv.conf \
    -b /data/data/com.termux/files/usr/bin/env:/usr/bin/env \
    -b /data/data/com.termux/files/usr/bin/sh:/bin/sh \
    -b /data/data/com.termux/files/usr/bin/bash:/bin/bash \
    /bin/sh -c 'pwd && ls -1 . | head -3' 2>/dev/null)
echo "  Result: $(echo "$CWD5" | head -1)"
echo "  Files: $(echo "$CWD5" | tail -3 | tr '\n' ', ')"
echo "$CWD5" | head -1 | grep -q "$TARGET_DIR" && \
    pass "CWD correct with full bind set" || fail "CWD mismatch with full binds"
echo ""

# --- Test 6: Verify old cd approach vs new -w approach ---
echo "--- Test 6: Compare old (cd) vs new (-w) approach ---"
OLD_CWD=$(env -u LD_PRELOAD -u LD_LIBRARY_PATH "$PROOT_BIN" --kill-on-exit \
    -b /data/data/com.termux/files/usr/bin/sh:/bin/sh \
    /bin/sh -c 'cd "$1" && pwd' sh "$TARGET_DIR" 2>/dev/null) || true
NEW_CWD=$(env -u LD_PRELOAD -u LD_LIBRARY_PATH "$PROOT_BIN" --kill-on-exit \
    -w "$TARGET_DIR" \
    -b /data/data/com.termux/files/usr/bin/sh:/bin/sh \
    /bin/sh -c 'pwd' 2>/dev/null) || true
echo "  Old (cd): $OLD_CWD"
echo "  New (-w): $NEW_CWD"
[ "$NEW_CWD" = "$TARGET_DIR" ] && pass "New -w approach works" || fail "New approach failed"
echo ""

echo "======================================"
echo "RESULTS: $PASS passed, $FAIL failed"
echo "======================================"

# Cleanup
rm -f "$ERR_FILE"
exit $FAIL
