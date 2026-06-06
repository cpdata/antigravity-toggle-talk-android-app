#!/usr/bin/env python3
import sys
import json
import os

if len(sys.argv) < 2:
    sys.exit(1)

session_id = sys.argv[1]
# Point to a location that stream_session.py can watch
log_dir = os.path.expanduser(f"~/.gemini/gemini-cli/history/{session_id}/.system_generated/logs")
os.makedirs(log_dir, exist_ok=True)
log_file = os.path.join(log_dir, "transcript_full.jsonl")

# Read from stdin
# We expect --output-format stream-json which produces multiple JSON objects
# Since it's a stream, we might need to handle partial JSONs if not line-buffered.
# But usually gemini CLI is well-behaved.

def main():
    buffer = ""
    for line in sys.stdin:
        buffer += line
        try:
            # Try to parse individual JSON objects from the stream
            # The stream-json format might be a series of objects like {}{}{}
            # A simple way to handle this is to try parsing as objects are closed.
            # However, many CLIs output one JSON per line for streaming.
            
            data = json.loads(line.strip())
            # Map to Antigravity format
            # Antigravity's stream_session.py looks for:
            # type: PLANNER_RESPONSE, source: MODEL
            # content: ... or thinking: ...
            
            adapted = {
                "type": "PLANNER_RESPONSE",
                "source": "MODEL",
                "content": data.get("content", ""),
                "thinking": data.get("thinking", ""),
                "tool_calls": data.get("tool_calls", [])
            }
            
            with open(log_file, "a", encoding="utf-8") as f:
                f.write(json.dumps(adapted) + "\n")
        except json.JSONDecodeError:
            continue

if __name__ == "__main__":
    main()
