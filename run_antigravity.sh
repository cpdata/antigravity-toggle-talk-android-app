#!/data/data/com.termux/files/usr/bin/bash
# run_antigravity.sh - Invoke Antigravity CLI and output JSON response for Android

TRANSCRIPT="$1"
TARGET_DIR="${2:-$HOME}"
CONTINUE_SESSION="${3:-false}"
SESSION_ID="$4"

# Find script directory to locate helper scripts
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# PID Process Tracking for active session termination
PID_FILE=""
if [ -n "$SESSION_ID" ]; then
    PID_FILE="$HOME/.gemini/antigravity-cli/brain/$SESSION_ID/.system_generated/logs/run.pid"
    mkdir -p "$(dirname "$PID_FILE")"
    echo "$$" > "$PID_FILE"
else
    # Capture initial directories to detect the new session folder when it is created
    INITIAL_DIRS=$(ls -d "$HOME/.gemini/antigravity-cli/brain"/*/ 2>/dev/null)
    (
        # Wait in background up to 30 seconds for the new session directory to appear
        for i in $(seq 1 150); do
            CURRENT_DIRS=$(ls -d "$HOME/.gemini/antigravity-cli/brain"/*/ 2>/dev/null)
            NEW_DIR=""
            for d in $CURRENT_DIRS; do
                if ! echo "$INITIAL_DIRS" | grep -qF "$d"; then
                    NEW_DIR="$d"
                    break
                fi
            done
            if [ -n "$NEW_DIR" ]; then
                SESS_ID=$(basename "$NEW_DIR")
                export PID_FILE="$HOME/.gemini/antigravity-cli/brain/$SESS_ID/.system_generated/logs/run.pid"
                mkdir -p "$(dirname "$PID_FILE")"
                echo "$PPID" > "$PID_FILE"
                break
            fi
            sleep 0.2
        done
    ) &
fi

trap 'kill "$STREAM_PID" 2>/dev/null; [ -z "$SESSION_ID" ] && SESSION_ID=$(ls -td "$HOME/.gemini/antigravity-cli/brain"/*/ 2>/dev/null | head -n 1 | xargs basename); [ -n "$SESSION_ID" ] && rm -f "$HOME/.gemini/antigravity-cli/brain/$SESSION_ID/.system_generated/logs/run.pid"' EXIT

LOG_FILE="$HOME/.toggle_talk_antigravity.log"
ERR_FILE="$HOME/.toggle_talk_antigravity.err"

# Setup environments (Running via proot --kill-on-exit)
export SSL_CERT_FILE="/data/data/com.termux/files/usr/etc/tls/cert.pem"
PROOT_BIN="/data/data/com.termux/files/usr/bin/proot"
GLIBC_LINKER="/data/data/com.termux/files/usr/glibc/lib/ld-linux-aarch64.so.1"
GLIBC_LIBS="/data/data/com.termux/files/home/.local/lib/agy-glibc:/data/data/com.termux/files/usr/glibc/lib"
AGY_BIN="/data/data/com.termux/files/home/.local/bin/agy.va39"

cd "$TARGET_DIR" 2>/dev/null || cd "$HOME"
TARGET_DIR="$(pwd)"

PROMPT="${TRANSCRIPT}"

# Start streaming updates in the background
STREAM_LOG="$HOME/.gemini/stream_session.log"
python3 "$SCRIPT_DIR/stream_session.py" "$SESSION_ID" "$TRANSCRIPT" > "$STREAM_LOG" 2>&1 &
STREAM_PID=$!

# Build agy argument list
AGY_ARGS=("$GLIBC_LINKER" --library-path "$GLIBC_LIBS" "$AGY_BIN" --dangerously-skip-permissions --add-dir "$TARGET_DIR")

if [ "$CONTINUE_SESSION" = "true" ]; then
    if [ -n "$SESSION_ID" ]; then
        AGY_ARGS+=(--conversation "$SESSION_ID")
    else
        AGY_ARGS+=(-c)
    fi
fi

AGY_ARGS+=(-p "$PROMPT" --print-timeout 60m)

# Run Antigravity via proot
# Ensure PWD env variable is explicitly set so Go binaries (like agy) read the correct working directory,
# and explicitly use /bin/sh to launch the linker to establish a proper environment context.
RESPONSE=$(env -u LD_PRELOAD -u LD_LIBRARY_PATH PWD="$TARGET_DIR" AGENT_ENV_TYPE="ToggleTalkApp" "$PROOT_BIN" --kill-on-exit \
      -w "$TARGET_DIR" \
      -b /data/data/com.termux/files/usr/etc/resolv.conf:/etc/resolv.conf \
      -b /data/data/com.termux/files/usr/bin/env:/usr/bin/env \
      -b /data/data/com.termux/files/usr/bin/sh:/bin/sh \
      -b /data/data/com.termux/files/usr/bin/bash:/bin/bash \
      /bin/sh -c 'cd "$1" 2>/dev/null || true; shift; exec "$@"' sh "$TARGET_DIR" "${AGY_ARGS[@]}" < /dev/null 2>>"$ERR_FILE")

# Stop streaming updates
sleep 0.5
kill $STREAM_PID 2>/dev/null
wait $STREAM_PID 2>/dev/null

# Find the session ID if it was not passed (new chat)
if [ -z "$SESSION_ID" ]; then
    SESSION_ID=$(ls -td "$HOME/.gemini/antigravity-cli/brain"/*/ 2>/dev/null | head -n 1 | xargs basename)
fi

# Extract latest and sanitized versions
LATEST_RESPONSE=$(printf "%s" "$RESPONSE" | python3 "$SCRIPT_DIR/tts_sanitize.py" --history-file "$LOG_FILE" --history-only)
SANITIZED_TTS=$(printf "%s" "$RESPONSE" | python3 "$SCRIPT_DIR/tts_sanitize.py" --history-file "$LOG_FILE")

# Save history
echo "$RESPONSE" > "$LOG_FILE"

# Output JSON formatted string for the Android app using Python (robust, no dependency on jq)
python3 -c 'import sys, json; print(json.dumps({"latest_response": sys.argv[1], "sanitized_tts": sys.argv[2], "session_id": sys.argv[3]}))' "$LATEST_RESPONSE" "$SANITIZED_TTS" "$SESSION_ID"
