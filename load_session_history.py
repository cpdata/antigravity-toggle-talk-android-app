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

    messages = []
    try:
        with open(path, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                try:
                    obj = json.loads(line)
                    msg_type = obj.get("type", "")
                    source = obj.get("source", "")
                    content = obj.get("content") or ""

                    if msg_type == "USER_INPUT" and content.strip():
                        # Strip wrapping tags, keep the user request text
                        m = re.search(
                            r"<USER_REQUEST>(.*?)(?:</USER_REQUEST>|$)",
                            content, re.DOTALL
                        )
                        text = m.group(1).strip() if m else content.strip()
                        # Remove remaining XML/metadata tags
                        text = re.sub(r"<[^>]+>.*?</[^>]+>", "", text, flags=re.DOTALL).strip()
                        text = re.sub(r"<[^>]+>", "", text).strip()
                        if text:
                            messages.append({"role": "user", "text": text})

                    elif msg_type == "PLANNER_RESPONSE" and source == "MODEL" and content.strip():
                        messages.append({"role": "agent", "text": content.strip()})

                except Exception:
                    pass
    except Exception as e:
        print(json.dumps({"error": str(e)}))
        return

    print(json.dumps(messages))

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(json.dumps({"error": "No session_id provided"}))
        sys.exit(1)
    load_history(sys.argv[1])
