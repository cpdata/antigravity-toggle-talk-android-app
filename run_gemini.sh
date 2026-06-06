#!/data/data/com.termux/files/usr/bin/bash
# run_gemini.sh - Invoke Gemini CLI and output JSON response for Android

TRANSCRIPT="$1"
TARGET_DIR="${2:-$HOME}"
CONTINUE_SESSION="${3:-false}"
SESSION_ID="$4"

# Set up environment
export AGENT_ENV_TYPE="ToggleTalkApp"
export PATH="/data/data/com.termux/files/usr/bin:$PATH"
export AGENT_BRAIN_DIR="$HOME/.gemini/gemini-cli/history"

# If SESSION_ID is not provided, we should probably generate one or wait for Gemini to create it.
# For simplicity, if empty, we let Gemini create it.
if [ -z "$SESSION_ID" ]; then
    # Generate a temporary ID for the PID file and stream watching
    SESSION_ID="new_$(date +%s)"
fi

PID_DIR="$HOME/.gemini/agent-pids"
mkdir -p "$PID_DIR"
PID_FILE="$PID_DIR/gemini_${SESSION_ID}.pid"
echo "$$" > "$PID_FILE"

trap 'kill "$STREAM_PID" 2>/dev/null; rm -f "$PID_FILE"' EXIT

cd "$TARGET_DIR" 2>/dev/null || cd "$HOME"
TARGET_DIR="$(pwd)"

# Start streaming watcher in background
python3 "/data/data/com.termux/files/home/ToggleTalkAndroid/stream_session.py" "$SESSION_ID" "$TRANSCRIPT" &
STREAM_PID=$!

# Prepare Gemini command
GEMINI_BIN="/data/data/com.termux/files/usr/bin/gemini"
ARGS=("--yolo" "--output-format" "stream-json")

if [ "$CONTINUE_SESSION" = "true" ] && [ -n "$SESSION_ID" ] && [[ ! "$SESSION_ID" == new_* ]]; then
    ARGS+=("--resume" "$SESSION_ID")
fi

# Run Gemini and pipe to stream adapter
# We capture the final JSON from the last line of the adapter or rerun at the end.
RESPONSE=$("$GEMINI_BIN" "${ARGS[@]}" --prompt "$TRANSCRIPT" 2>/dev/null | python3 "/data/data/com.termux/files/home/ToggleTalkAndroid/gemini_stream_adapter.py" "$SESSION_ID")

# Wait for stream to finish
sleep 1
kill "$STREAM_PID" 2>/dev/null

# Since we used stream-json, we need a final response object for the Android callback.
# We can extract it from the log file we just wrote.
LOG_FILE="$AGENT_BRAIN_DIR/$SESSION_ID/.system_generated/logs/transcript_full.jsonl"

python3 - <<EOF
import sys
import json
import re
import os

log_file = "$LOG_FILE"
session_id = "$SESSION_ID"

latest_text = "No response from Gemini."
if os.path.exists(log_file):
    try:
        with open(log_file, "r") as f:
            lines = f.readlines()
            for line in reversed(lines):
                data = json.loads(line)
                if data.get("content"):
                    latest_text = data["content"]
                    break
    except:
        pass

# Sanitize for TTS
sanitized_tts = re.sub(r'#+\s+', '', latest_text)
sanitized_tts = re.sub(r'\*\*|\*', '', sanitized_tts)
sanitized_tts = re.sub(r'\[(.*?)\]\(.*?\)', r'\1', sanitized_tts)

# Output final JSON for Android app callback
print(json.dumps({
    "latest_response": latest_text,
    "sanitized_tts": sanitized_tts,
    "session_id": session_id
}))
EOF
