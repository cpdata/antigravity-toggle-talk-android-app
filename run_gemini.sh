#!/data/data/com.termux/files/usr/bin/bash
# run_gemini.sh - Invoke Gemini CLI and output JSON response for Android

TRANSCRIPT="$1"
TARGET_DIR="${2:-$HOME}"
CONTINUE_SESSION="${3:-false}"
SESSION_ID="$4"

# Set up environment
export AGENT_ENV_TYPE="ToggleTalkApp"
export AGENT="gemini"
export PATH="/data/data/com.termux/files/usr/bin:$PATH"

# PID Tracking
PID_DIR="$HOME/.gemini/agent-pids"
mkdir -p "$PID_DIR"
PID_FILE="$PID_DIR/gemini_${SESSION_ID:-new}.pid"
echo "$$" > "$PID_FILE"

trap 'kill "$STREAM_PID" 2>/dev/null; rm -f "$PID_FILE"' EXIT

cd "$TARGET_DIR" 2>/dev/null || cd "$HOME"
TARGET_DIR="$(pwd)"

# Start streaming watcher in background
# It will find the transcript file automatically
python3 "/data/data/com.termux/files/home/ToggleTalkAndroid/stream_session.py" "$SESSION_ID" "$TRANSCRIPT" &
STREAM_PID=$!

# Prepare Gemini command
GEMINI_BIN="/data/data/com.termux/files/usr/bin/gemini"
ARGS=("--yolo" "--output-format" "json") # Using json for the final result

if [ "$CONTINUE_SESSION" = "true" ] && [ -n "$SESSION_ID" ]; then
    ARGS+=("--resume" "$SESSION_ID")
fi

# Run Gemini
RESPONSE=$("$GEMINI_BIN" "${ARGS[@]}" --prompt "$TRANSCRIPT" 2>/dev/null)

# Stop streaming
sleep 0.5
kill "$STREAM_PID" 2>/dev/null

# If RESPONSE is empty, Gemini CLI might have failed or session ID changed
if [ -z "$RESPONSE" ]; then
    # Fallback: Extract from the latest transcript file
    RESPONSE_FILE=$(AGENT=gemini python3 -c "from stream_session import find_gemini_transcript; print(find_gemini_transcript('$SESSION_ID') or '')")
    if [ -n "$RESPONSE_FILE" ] && [ -f "$RESPONSE_FILE" ]; then
        # Extract last message from transcript
        python3 - <<EOF
import json, re, os
try:
    with open("$RESPONSE_FILE", "r") as f:
        lines = f.readlines()
        latest_text = ""
        for line in reversed(lines):
            data = json.loads(line)
            if data.get("type") == "gemini" and data.get("content"):
                latest_text = data["content"]
                break
        
        # Sanitize for TTS
        sanitized_tts = re.sub(r'#+\s+', '', latest_text)
        sanitized_tts = re.sub(r'\*\*|\*', '', sanitized_tts)
        sanitized_tts = re.sub(r'\[(.*?)\]\(.*?\)', r'\1', sanitized_tts)
        
        print(json.dumps({
            "latest_response": latest_text,
            "sanitized_tts": sanitized_tts,
            "session_id": "$SESSION_ID"
        }))
except Exception as e:
    print(json.dumps({"latest_response": "Error: " + str(e), "sanitized_tts": "Error", "session_id": "$SESSION_ID"}))
EOF
        exit 0
    fi
    echo '{"latest_response": "No response from Gemini.", "sanitized_tts": "No response from Gemini.", "session_id": "'$SESSION_ID'"}'
    exit 1
fi

# Parse the JSON response from Gemini CLI
# Note: Gemini CLI output might contain extra text before/after JSON
python3 - <<EOF
import sys, json, re
raw = '''$RESPONSE'''
try:
    # Find JSON block
    match = re.search(r'\{.*\}', raw, re.DOTALL)
    if match:
        data = json.loads(match.group(0))
        messages = data.get('messages', [])
        latest_text = ""
        if messages:
            for msg in reversed(messages):
                if msg.get('role') in ['model', 'assistant']:
                    latest_text = msg.get('content', '')
                    break
        if not latest_text: latest_text = data.get('content', '')
        
        sess_id = data.get('sessionId', '$SESSION_ID')
        
        # Sanitize
        sanitized_tts = re.sub(r'#+\s+', '', latest_text)
        sanitized_tts = re.sub(r'\*\*|\*', '', sanitized_tts)
        sanitized_tts = re.sub(r'\[(.*?)\]\(.*?\)', r'\1', sanitized_tts)
        
        print(json.dumps({
            "latest_response": latest_text,
            "sanitized_tts": sanitized_tts,
            "session_id": sess_id
        }))
    else:
        print(json.dumps({"latest_response": raw, "sanitized_tts": raw, "session_id": "$SESSION_ID"}))
except Exception as e:
    print(json.dumps({"latest_response": raw, "sanitized_tts": "Error parsing response", "session_id": "$SESSION_ID"}))
EOF
