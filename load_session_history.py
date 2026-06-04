#!/usr/bin/env python3
"""
Reads an Antigravity session transcript and outputs parsed conversation
messages as a JSON array for display in the ToggleTalk Android app.

Usage: python3 load_session_history.py <session_id>
Output: JSON array of {"role": "user"|"agent", "text": "..."} objects
"""
import os
import sys
import json
import re
from transcript_parser import parse_transcript_steps

BRAIN_DIR = "/data/data/com.termux/files/home/.gemini/antigravity-cli/brain"

def load_history(session_id):
    base = os.path.join(BRAIN_DIR, session_id, ".system_generated", "logs")
    full_path = os.path.join(base, "transcript_full.jsonl")
    compact_path = os.path.join(base, "transcript.jsonl")

    # Prefer full transcript; fall back to compact
    path = full_path if os.path.exists(full_path) else compact_path
    if not os.path.exists(path):
        print(json.dumps({"error": "No transcript found"}))
        return

    raw_steps = []
    try:
        with open(path, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                try:
                    raw_steps.append(json.loads(line))
                except Exception:
                    pass
    except Exception as e:
        print(json.dumps({"error": str(e)}))
        return

    messages = parse_transcript_steps(raw_steps)
    json_str = json.dumps(messages)

    # If the JSON string fits within Termux's 100KB intent limit, return it directly.
    # Otherwise, write to a shared file on the SDCard to bypass the Binder limit.
    if len(json_str.encode('utf-8')) < 90 * 1024:
        print(json_str)
    else:
        out_dir = "/sdcard/Android/media/com.toggletalk.android"
        os.makedirs(out_dir, exist_ok=True)
        out_path = os.path.join(out_dir, "session_history.json")
        try:
            with open(out_path, "w", encoding="utf-8") as out_f:
                out_f.write(json_str)
            print(json.dumps({"status": "success", "file": out_path}))
        except Exception as e:
            # Fallback if writing fails
            print(json_str)

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(json.dumps({"error": "No session_id provided"}))
        sys.exit(1)
    load_history(sys.argv[1])
