#!/data/data/com.termux/files/usr/bin/bash
# run_gemini.sh - Invoke Gemini CLI and output JSON response for Android

TRANSCRIPT="$1"
TARGET_DIR="${2:-$HOME}"
CONTINUE_SESSION="${3:-false}"
SESSION_ID="$4"

# Find script directory to locate helper scripts
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Set up environment
export AGENT_ENV_TYPE="ToggleTalkApp"
export AGENT="gemini"
export SCRIPT_DIR="$SCRIPT_DIR"
export AGENT_TARGET_DIR="$TARGET_DIR"
export PYTHONPATH="$SCRIPT_DIR:$PYTHONPATH"
export PATH="/data/data/com.termux/files/usr/bin:$PATH"
export CI=true

# Ensure .gemini directory exists
mkdir -p "$HOME/.gemini"

# PID Tracking
PID_DIR="$HOME/.gemini/agent-pids"
mkdir -p "$PID_DIR"
PID_FILE="$PID_DIR/gemini_${SESSION_ID:-new}.pid"
echo "$$" > "$PID_FILE"

cd "$TARGET_DIR" 2>/dev/null || cd "$HOME"
TARGET_DIR="$(pwd)"

# Prepare Gemini command
GEMINI_BIN="/data/data/com.termux/files/usr/bin/gemini"
# If GEMINI_BIN is a symlink to gemini.js, we call it with node
if [[ "$GEMINI_BIN" == *.js ]] || [ -L "$GEMINI_BIN" ]; then
    NODE_BIN="/data/data/com.termux/files/usr/bin/node"
    RUN_CMD=("$NODE_BIN" "$GEMINI_BIN")
else
    RUN_CMD=("$GEMINI_BIN")
fi

ARGS=("--yolo" "--output-format" "stream-json" "--skip-trust")

if [ "$CONTINUE_SESSION" = "true" ] && [ -n "$SESSION_ID" ]; then
    ARGS+=("--resume" "$SESSION_ID")
fi

# Run Gemini in the background and redirect output to a temporary JSONL file
TEMP_STREAM="$HOME/.gemini/stream_output_$$.jsonl"
touch "$TEMP_STREAM"

"${RUN_CMD[@]}" "${ARGS[@]}" -p "$TRANSCRIPT" > "$TEMP_STREAM" 2>>"$HOME/.gemini/gemini_err.log" &
GEMINI_PID=$!

# Wait for the init event to capture the actual SESSION_ID
REAL_SESSION_ID=""
MAX_WAIT=50 # 5 seconds max wait
WAIT_COUNT=0
while [ -z "$REAL_SESSION_ID" ] && [ $WAIT_COUNT -lt $MAX_WAIT ]; do
    if ! kill -0 $GEMINI_PID 2>/dev/null; then
        break
    fi
    # Use python/jq to extract session_id from the init event
    REAL_SESSION_ID=$(python3 -c '
import json, sys
try:
    with open(sys.argv[1], "r") as f:
        for line in f:
            obj = json.loads(line)
            if obj.get("type") == "init":
                print(obj.get("session_id", ""))
                sys.exit(0)
except: pass
print("")
' "$TEMP_STREAM")
    if [ -n "$REAL_SESSION_ID" ]; then
        break
    fi
    sleep 0.1
    WAIT_COUNT=$((WAIT_COUNT + 1))
done

if [ -z "$REAL_SESSION_ID" ]; then
    REAL_SESSION_ID="$SESSION_ID"
fi

# Start streaming watcher in background using the real session ID
STREAM_LOG="$HOME/.gemini/stream_session_gemini.log"
echo "--- Starting new Gemini stream session at $(date) ---" > "$STREAM_LOG"
python3 "$SCRIPT_DIR/stream_session.py" "$REAL_SESSION_ID" "$TRANSCRIPT" >> "$STREAM_LOG" 2>&1 &
STREAM_PID=$!

trap 'kill "$STREAM_PID" "$GEMINI_PID" 2>/dev/null; rm -f "$PID_FILE" "$TEMP_STREAM"' EXIT

# Wait for Gemini CLI to finish
wait $GEMINI_PID

# Give stream_session.py more time to finish broadcasting final steps
sleep 2.0
kill "$STREAM_PID" 2>/dev/null

# Parse the final message from the stream JSONL file
LATEST_RESPONSE=$(python3 -c '
import json, sys
try:
    with open(sys.argv[1], "r") as f:
        latest_text = ""
        for line in f:
            try:
                obj = json.loads(line)
                # stream-json outputs "message" type with "assistant" role
                if obj.get("type") == "message" and obj.get("role") in ["model", "assistant"]:
                    if obj.get("delta"):
                        latest_text += obj.get("content", "")
                    else:
                        latest_text = obj.get("content", "")
            except: pass
        print(latest_text)
except: print("")
' "$TEMP_STREAM")

# If empty, fallback to reading the transcript file directly
if [ -z "$LATEST_RESPONSE" ]; then
    export RESPONSE_FILE=$(AGENT=gemini python3 -c "from stream_session import find_gemini_transcript; print(find_gemini_transcript('$REAL_SESSION_ID') or '')")
    if [ -n "$RESPONSE_FILE" ] && [ -f "$RESPONSE_FILE" ]; then
        LATEST_RESPONSE=$(python3 -c '
import json, os
path = os.environ.get("RESPONSE_FILE")
try:
    with open(path, "r") as f:
        latest_text = ""
        for line in reversed(f.readlines()):
            try:
                data = json.loads(line)
                if data.get("type") == "gemini" and data.get("content"):
                    latest_text = data["content"]
                    break
            except: continue
        print(latest_text)
except: print("")
')
    fi
fi

if [ -z "$LATEST_RESPONSE" ]; then
    echo "{\"latest_response\": \"No response from Gemini.\", \"sanitized_tts\": \"No response from Gemini.\", \"session_id\": \"$REAL_SESSION_ID\"}"
    exit 1
fi

SANITIZED_TTS=$(printf "%s" "$LATEST_RESPONSE" | python3 "$SCRIPT_DIR/tts_sanitize.py")
python3 -c 'import sys, json; print(json.dumps({"latest_response": sys.argv[1], "sanitized_tts": sys.argv[2], "session_id": sys.argv[3]}))' "$LATEST_RESPONSE" "$SANITIZED_TTS" "$REAL_SESSION_ID"
