#!/data/data/com.termux/files/usr/bin/bash
# run_antigravity.sh - Invoke Antigravity CLI and output JSON response for Android

TRANSCRIPT="$1"
TARGET_DIR="${2:-$HOME}"
CONTINUE_SESSION="${3:-false}"

LOG_FILE="$HOME/.toggle_talk_antigravity.log"
ERR_FILE="$HOME/.toggle_talk_antigravity.err"

# Setup environments (Glibc bypass path)
export GODEBUG=netdns=go
export SSL_CERT_FILE="/data/data/com.termux/files/usr/etc/tls/cert.pem"
GLIBC_LINKER="/data/data/com.termux/files/usr/glibc/lib/ld-linux-aarch64.so.1"
GLIBC_LIBS="/data/data/com.termux/files/home/.local/lib/agy-glibc:/data/data/com.termux/files/usr/glibc/lib"
AGY_BIN="/data/data/com.termux/files/home/.local/bin/agy.va39"

cd "$TARGET_DIR" 2>/dev/null

PROMPT_SUFFIX="

[Context: Your active working directory for this session is '$(basename "$TARGET_DIR")'. Format your response in standard Markdown to display inside the app. For voice output, use the custom 'tts' command to speak a brief summary of your response.]"
PROMPT="${TRANSCRIPT}${PROMPT_SUFFIX}"

# Run Antigravity
if [ "$CONTINUE_SESSION" = "true" ]; then
    RESPONSE=$(env -u LD_PRELOAD -u LD_LIBRARY_PATH "$GLIBC_LINKER" --library-path "$GLIBC_LIBS" "$AGY_BIN" --dangerously-skip-permissions -c -p "$PROMPT" < /dev/null 2>>"$ERR_FILE")
else
    RESPONSE=$(env -u LD_PRELOAD -u LD_LIBRARY_PATH "$GLIBC_LINKER" --library-path "$GLIBC_LIBS" "$AGY_BIN" --dangerously-skip-permissions -p "$PROMPT" < /dev/null 2>>"$ERR_FILE")
fi

# Extract latest and sanitized versions
LATEST_RESPONSE=$(printf "%s" "$RESPONSE" | python3 "$HOME/toggle-talk-antigravity/tts_sanitize.py" --history-file "$LOG_FILE" --history-only)
SANITIZED_TTS=$(printf "%s" "$RESPONSE" | python3 "$HOME/toggle-talk-antigravity/tts_sanitize.py" --history-file "$LOG_FILE")

# Save history
echo "$RESPONSE" > "$LOG_FILE"

# Output JSON formatted string for the Android app using Python (robust, no dependency on jq)
python3 -c 'import sys, json; print(json.dumps({"latest_response": sys.argv[1], "sanitized_tts": sys.argv[2]}))' "$LATEST_RESPONSE" "$SANITIZED_TTS"
