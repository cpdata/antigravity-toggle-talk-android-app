#!/data/data/com.termux/files/usr/bin/bash
# run_antigravity.sh - Invoke Antigravity CLI and output JSON response for Android

TRANSCRIPT="$1"
TARGET_DIR="${2:-$HOME}"
CONTINUE_SESSION="${3:-false}"
SESSION_ID="$4"

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

PROMPT_SUFFIX="

[Context: Your active working directory for this session is '$(basename "$TARGET_DIR")'.
Format your response in standard Markdown to display inside of a markdown renderer.
Do not enclose your response in triple backticks.
CRITICAL: You MUST wrap any text that should be spoken out loud by the Text-to-Speech (TTS) system inside <tts>...</tts> tags. This must be in the final response output meant for the user, NOT inside tool calls, thought processes, or intermediate steps. ONLY the content inside <tts>...</tts> tags will be spoken. Place all thoughts, intermediate reasoning, tool calls, and verbose explanations outside the <tts>...</tts> tags so they are only displayed visually, keeping the spoken response concise and natural.

Example Response:
Here is a summary of the files I updated:
- [main.py](file:///path/to/main.py): Modified run configuration.

<tts>I have completed the requested changes. Please verify and run the tests.</tts>]"
PROMPT="${TRANSCRIPT}${PROMPT_SUFFIX}"

# Start streaming updates in the background
python3 "/data/data/com.termux/files/home/ToggleTalkAndroid/stream_session.py" "$SESSION_ID" &
STREAM_PID=$!

# Run Antigravity
if [ "$CONTINUE_SESSION" = "true" ]; then
    if [ -n "$SESSION_ID" ]; then
        CONV_ARG="--conversation $SESSION_ID"
    else
        CONV_ARG="-c"
    fi
    RESPONSE=$(env -u LD_PRELOAD -u LD_LIBRARY_PATH "$PROOT_BIN" --kill-on-exit \
      -w "$TARGET_DIR" \
      -b /data/data/com.termux/files/usr/etc/resolv.conf:/etc/resolv.conf \
      -b /data/data/com.termux/files/usr/bin/env:/usr/bin/env \
      -b /data/data/com.termux/files/usr/bin/sh:/bin/sh \
      -b /data/data/com.termux/files/usr/bin/bash:/bin/bash \
      "$GLIBC_LINKER" --library-path "$GLIBC_LIBS" "$AGY_BIN" \
      --dangerously-skip-permissions $CONV_ARG -p "$PROMPT" --print-timeout 60m < /dev/null 2>>"$ERR_FILE")
else
    RESPONSE=$(env -u LD_PRELOAD -u LD_LIBRARY_PATH "$PROOT_BIN" --kill-on-exit \
      -w "$TARGET_DIR" \
      -b /data/data/com.termux/files/usr/etc/resolv.conf:/etc/resolv.conf \
      -b /data/data/com.termux/files/usr/bin/env:/usr/bin/env \
      -b /data/data/com.termux/files/usr/bin/sh:/bin/sh \
      -b /data/data/com.termux/files/usr/bin/bash:/bin/bash \
      "$GLIBC_LINKER" --library-path "$GLIBC_LIBS" "$AGY_BIN" \
      --dangerously-skip-permissions -p "$PROMPT" --print-timeout 60m < /dev/null 2>>"$ERR_FILE")
fi

# Stop streaming updates
kill $STREAM_PID 2>/dev/null

# Extract latest and sanitized versions
LATEST_RESPONSE=$(printf "%s" "$RESPONSE" | python3 "$HOME/toggle-talk-antigravity/tts_sanitize.py" --history-file "$LOG_FILE" --history-only)
SANITIZED_TTS=$(printf "%s" "$RESPONSE" | python3 "$HOME/toggle-talk-antigravity/tts_sanitize.py" --history-file "$LOG_FILE")

# Save history
echo "$RESPONSE" > "$LOG_FILE"

# Output JSON formatted string for the Android app using Python (robust, no dependency on jq)
python3 -c 'import sys, json; print(json.dumps({"latest_response": sys.argv[1], "sanitized_tts": sys.argv[2]}))' "$LATEST_RESPONSE" "$SANITIZED_TTS"
