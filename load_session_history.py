#!/usr/bin/env python3
import os
import sys
import json
import glob
from transcript_parser import parse_transcript_steps

AGY_BRAIN_DIR = "/data/data/com.termux/files/home/.gemini/antigravity-cli/brain"
GEMINI_TMP_DIR = "/data/data/com.termux/files/home/.gemini/tmp"

def find_gemini_transcript(session_id):
    project_name = os.path.basename(os.getcwd()).lower()
    chats_dir = os.path.join(GEMINI_TMP_DIR, project_name, "chats")
    
    if session_id:
        if os.path.exists(chats_dir):
            # Try specific pattern first
            pattern = os.path.join(chats_dir, f"session-*-{session_id[:8]}*.jsonl")
            files = glob.glob(pattern)
            if files: return files[0]
            
            # Fallback: scan files in this project's chats_dir
            for f in glob.glob(os.path.join(chats_dir, "*.jsonl")):
                try:
                    with open(f, "r") as fh:
                        if session_id in fh.readline(): return f
                except: continue

        # Global fallback: search all projects
        pattern = os.path.join(GEMINI_TMP_DIR, "*", "chats", "*.jsonl")
        for f in glob.glob(pattern):
            try:
                with open(f, "r") as fh:
                    if session_id in fh.readline(): return f
            except: continue
            
    return None

def load_history(session_id, agent="antigravity"):
    path = None
    if agent == "gemini":
        path = find_gemini_transcript(session_id)
    else:
        base = os.path.join(AGY_BRAIN_DIR, session_id, ".system_generated", "logs")
        full_path = os.path.join(base, "transcript_full.jsonl")
        compact_path = os.path.join(base, "transcript.jsonl")
        path = full_path if os.path.exists(full_path) else compact_path

    if not path or not os.path.exists(path):
        print(json.dumps({"error": f"No transcript found for session {session_id}"}))
        return

    raw_steps = []
    try:
        with open(path, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line: continue
                try:
                    raw_steps.append(json.loads(line))
                except: pass
    except Exception as e:
        print(json.dumps({"error": str(e)}))
        return

    messages = parse_transcript_steps(raw_steps)
    json_str = json.dumps(messages)

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
        except:
            print(json_str)

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(json.dumps({"error": "No session_id provided"}))
        sys.exit(1)
    
    agent = os.environ.get("AGENT", "antigravity")
    load_history(sys.argv[1], agent)
