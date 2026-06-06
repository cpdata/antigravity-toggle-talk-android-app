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

TEMP_STREAM="$HOME/.gemini/stream_output_$$.jsonl"
touch "$TEMP_STREAM"

# Execute gemini in background
"${RUN_CMD[@]}" "${ARGS[@]}" -p "$TRANSCRIPT" > "$TEMP_STREAM" 2>>"$HOME/.gemini/gemini_err.log" &
GEMINI_PID=$!

# Extract REAL_SESSION_ID from the first line (init event)
REAL_SESSION_ID=""
MAX_WAIT=50 # 5 seconds max wait
WAIT_COUNT=0
while [ -z "$REAL_SESSION_ID" ] && [ $WAIT_COUNT -lt $MAX_WAIT ]; do
    if ! kill -0 $GEMINI_PID 2>/dev/null && [ ! -s "$TEMP_STREAM" ]; then
        break
    fi
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

# We MUST have a real session ID to continue reliably. 
# If init failed, we use the passed ID, but this is a fallback we should avoid if possible.
USED_SESSION_ID="${REAL_SESSION_ID:-$SESSION_ID}"

# Start streaming watcher in background
STREAM_LOG="$HOME/.gemini/stream_session_gemini.log"
echo "--- Starting new Gemini stream session at $(date) for ID $USED_SESSION_ID ---" > "$STREAM_LOG"
echo "run_gemini.sh: Using session ID: $USED_SESSION_ID (captured: $REAL_SESSION_ID, original: $SESSION_ID)" >> "$STREAM_LOG"
python3 "$SCRIPT_DIR/stream_session.py" "$USED_SESSION_ID" "$TRANSCRIPT" >> "$STREAM_LOG" 2>&1 &
STREAM_PID=$!

trap 'kill "$STREAM_PID" "$GEMINI_PID" 2>/dev/null; rm -f "$PID_FILE" "$TEMP_STREAM"' EXIT

# Wait for Gemini CLI to finish
wait $GEMINI_PID

# Kill stream_session immediately to stop further broadcasts
kill "$STREAM_PID" 2>/dev/null

# Final check of the transcript file to get the definitive last response
FINAL_TRANSCRIPT=$(AGENT=gemini AGENT_TARGET_DIR="$TARGET_DIR" python3 -c "from stream_session import find_gemini_transcript; print(find_gemini_transcript('$USED_SESSION_ID') or '')")

LATEST_RESPONSE="No response received."
if [ -n "$FINAL_TRANSCRIPT" ] && [ -f "$FINAL_TRANSCRIPT" ]; then
    LATEST_RESPONSE=$(python3 -c '
import json, sys
path = sys.argv[1]
try:
    with open(path, "r", encoding="utf-8") as f:
        lines = f.readlines()
        latest_text = ""
        for line in reversed(lines):
            try:
                data = json.loads(line)
                if data.get("type") == "gemini" and data.get("content"):
                    latest_text = data["content"]
                    break
            except: continue
        print(latest_text if latest_text else "No response in transcript.")
except: print("Error reading transcript.")
' "$FINAL_TRANSCRIPT")
fi

SANITIZED_TTS=$(printf "%s" "$LATEST_RESPONSE" | python3 "$SCRIPT_DIR/tts_sanitize.py")

# Output JSON for Android
python3 -c 'import sys, json; print(json.dumps({"latest_response": sys.argv[1], "sanitized_tts": sys.argv[2], "session_id": sys.argv[3]}))' "$LATEST_RESPONSE" "$SANITIZED_TTS" "$USED_SESSION_ID"
