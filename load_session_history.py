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

    messages = []
    num_steps = len(raw_steps)

    for idx, obj in enumerate(raw_steps):
        msg_type = obj.get("type", "")
        source = obj.get("source", "")
        content = obj.get("content") or ""
        tool_calls = obj.get("tool_calls") or []

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
            # Check if this is the final agent response of a turn.
            # It is final if:
            # 1. It has no tool calls AND
            # 2. There are no subsequent PLANNER_RESPONSE or tool result messages before the next USER_INPUT.
            is_final = False
            if not tool_calls:
                is_final = True
                for next_idx in range(idx + 1, num_steps):
                    next_obj = raw_steps[next_idx]
                    next_type = next_obj.get("type", "")
                    if next_type == "USER_INPUT":
                        break
                    elif next_type in ["PLANNER_RESPONSE", "RUN_COMMAND", "LIST_DIRECTORY", "GENERIC", "SYSTEM_MESSAGE"] or next_obj.get("tool_calls"):
                        is_final = False
                        break

            if is_final:
                messages.append({"role": "agent", "text": content.strip()})
            else:
                messages.append({"role": "thought", "text": content.strip()})

            for tc in tool_calls:
                tc_name = tc.get("name", "")
                tc_args = tc.get("args", {})
                args_str = ", ".join(f"{k}={v}" for k, v in tc_args.items())
                messages.append({"role": "tool_call", "text": f"Calling tool {tc_name}({args_str})"})

        elif msg_type in ["RUN_COMMAND", "LIST_DIRECTORY", "GENERIC", "SYSTEM_MESSAGE"] and content.strip():
            status = obj.get("status", "")
            status_suffix = f" ({status})" if status and status != "DONE" else ""
            messages.append({"role": "tool_call", "text": f"Tool result{status_suffix}:\n{content.strip()}"})

    # Deduplicate consecutive identical messages
    deduped = []
    for msg in messages:
        if not deduped or deduped[-1] != msg:
            deduped.append(msg)

    json_str = json.dumps(deduped)

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
