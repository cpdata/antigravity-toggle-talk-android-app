#!/usr/bin/env python3
import os
import sys
import json
import time
import re
import subprocess
import signal
import glob

# Default brain dir (Antigravity)
DEFAULT_BRAIN_DIR = "/data/data/com.termux/files/home/.gemini/antigravity-cli/brain"
BRAIN_DIR = os.environ.get("AGENT_BRAIN_DIR", DEFAULT_BRAIN_DIR)
GEMINI_TMP_DIR = "/data/data/com.termux/files/home/.gemini/tmp"

stop_requested = False

def handle_signal(signum, frame):
    global stop_requested
    stop_requested = True

signal.signal(signal.SIGTERM, handle_signal)
signal.signal(signal.SIGINT, handle_signal)

def find_gemini_transcript(session_id):
    project_name = os.path.basename(os.getcwd()).lower()
    chats_dir = os.path.join(GEMINI_TMP_DIR, project_name, "chats")
    
    if session_id and not session_id.startswith("new_"):
        if os.path.exists(chats_dir):
            pattern = os.path.join(chats_dir, f"session-*-{session_id[:8]}*.jsonl")
            files = glob.glob(pattern)
            if files: return files[0]
            
            # Fallback: scan files for session_id in first line
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
    
    # If new session, wait for a new file in current project
    if os.path.exists(chats_dir):
        start_time = time.time()
        initial_files = set(os.listdir(chats_dir))
        while time.time() - start_time < 30:
            if os.path.exists(chats_dir):
                current_files = set(os.listdir(chats_dir))
                new_files = current_files - initial_files
                if new_files:
                    # Pick the newest one
                    new_paths = [os.path.join(chats_dir, f) for f in new_files]
                    return max(new_paths, key=os.path.getmtime)
            time.sleep(0.5)
    
    # Final fallback: latest file in current project's chats_dir
    if os.path.exists(chats_dir):
        files = [os.path.join(chats_dir, f) for f in os.listdir(chats_dir) if f.endswith(".jsonl")]
        if files: return max(files, key=os.path.getmtime)
        
    return None

def find_log_path(session_id):
    explicit_path = os.environ.get("AGENT_TRANSCRIPT_PATH")
    if explicit_path and os.path.exists(explicit_path):
        return explicit_path

    agent = os.environ.get("AGENT", "antigravity")
    if agent == "gemini":
        return find_gemini_transcript(session_id)

    if session_id:
        path = os.path.join(BRAIN_DIR, session_id, ".system_generated", "logs", "transcript_full.jsonl")
        if os.path.exists(path): return path
    
    # Antigravity new session wait logic
    initial_dirs = set()
    if os.path.exists(BRAIN_DIR):
        try:
            initial_dirs = {d for d in os.listdir(BRAIN_DIR) if os.path.isdir(os.path.join(BRAIN_DIR, d))}
        except: pass
            
    start_time = time.time()
    while time.time() - start_time < 30:
        try:
            if os.path.exists(BRAIN_DIR):
                current_dirs = {d for d in os.listdir(BRAIN_DIR) if os.path.isdir(os.path.join(BRAIN_DIR, d))}
                new_dirs = current_dirs - initial_dirs
                if new_dirs:
                    new_paths = [os.path.join(BRAIN_DIR, d) for d in new_dirs]
                    latest_dir = max(new_paths, key=os.path.getmtime)
                    path = os.path.join(latest_dir, ".system_generated", "logs", "transcript_full.jsonl")
                    if os.path.exists(path): return path
        except: pass
        time.sleep(0.2)
        
    try:
        subdirs = [os.path.join(BRAIN_DIR, d) for d in os.listdir(BRAIN_DIR) if os.path.isdir(os.path.join(BRAIN_DIR, d))]
        if subdirs:
            latest_dir = max(subdirs, key=os.path.getmtime)
            path = os.path.join(latest_dir, ".system_generated", "logs", "transcript_full.jsonl")
            if os.path.exists(path): return path
    except: pass
    return None

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
from transcript_parser import parse_transcript_steps

def send_broadcast(session_id, json_str=None, file_path=None, tts_text=None):
    cmd = [
        "am", "broadcast", "--user", "0",
        "-p", "com.toggletalk.android",
        "-a", "com.toggletalk.android.ACTION_STREAM_UPDATE",
        "--es", "session_id", session_id
    ]
    if json_str: cmd += ["--es", "messages_json", json_str]
    if file_path: cmd += ["--es", "file_path", file_path]
    if tts_text: cmd += ["--es", "tts_text", tts_text]
    subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

def extract_tts_text(step_obj):
    msg_type = step_obj.get("type", "")
    # Handle Antigravity format
    if msg_type == "PLANNER_RESPONSE":
        content = step_obj.get("content") or step_obj.get("thinking") or ""
        tts_segments = re.findall(r"<tts>(.*?)</tts>", content, re.DOTALL)
        if tts_segments: return " ".join(seg.strip() for seg in tts_segments).strip()
    # Handle Gemini format
    elif msg_type == "gemini":
        content = step_obj.get("content", "")
        tts_segments = re.findall(r"<tts>(.*?)</tts>", content, re.DOTALL)
        if tts_segments: return " ".join(seg.strip() for seg in tts_segments).strip()
    return None

def find_start_index(lines, prompt):
    if not prompt: return len(lines)
    # Search backwards for the start of the current turn
    for idx in range(len(lines) - 1, -1, -1):
        try:
            obj = json.loads(lines[idx].strip())
            # Antigravity start
            if obj.get("type") == "USER_INPUT": return idx
            # Gemini start
            if obj.get("type") == "user": return idx
        except: pass
    return len(lines)

def tail_transcript(path, session_id, prompt):
    global stop_requested
    
    current_line_idx = 0
    if os.path.exists(path):
        try:
            with open(path, "r", encoding="utf-8") as f:
                lines = f.readlines()
                current_line_idx = find_start_index(lines, prompt)
        except: pass

    while True:
        if not os.path.exists(path):
            if stop_requested: break
            time.sleep(0.5)
            continue
            
        try:
            with open(path, "r", encoding="utf-8") as f:
                lines = f.readlines()
                
            if len(lines) < current_line_idx:
                current_line_idx = 0
                
            while current_line_idx < len(lines):
                if stop_requested: break
                line = lines[current_line_idx].strip()
                current_line_idx += 1
                if not line: continue
                    
                try:
                    obj = json.loads(line)
                    tts_text = extract_tts_text(obj)
                    
                    # Update real session ID if we were using a placeholder or it was empty
                    if not session_id or session_id.startswith("new_"):
                        if "sessionId" in obj:
                            session_id = obj["sessionId"]
                        elif "id" in obj and obj.get("type") == "user":
                            # Use file metadata or something
                            pass

                    parsed_steps = []
                    for l in lines[:current_line_idx]:
                        l = l.strip()
                        if l:
                            try: parsed_steps.append(json.loads(l))
                            except: pass
                    
                    messages = parse_transcript_steps(parsed_steps)
                    json_str = json.dumps(messages)
                    
                    if len(json_str.encode('utf-8')) < 90 * 1024:
                        send_broadcast(session_id, json_str=json_str, tts_text=tts_text)
                    else:
                        out_dir = "/sdcard/Android/media/com.toggletalk.android"
                        os.makedirs(out_dir, exist_ok=True)
                        out_path = os.path.join(out_dir, "stream_history.json")
                        with open(out_path, "w", encoding="utf-8") as out_f:
                            out_f.write(json_str)
                        send_broadcast(session_id, file_path=out_path, tts_text=tts_text)
                except Exception as e:
                    print(f"Error: {e}")
            
            if stop_requested and current_line_idx >= len(lines): break
        except Exception as e:
            print(f"Error: {e}")
            if stop_requested: break
        time.sleep(0.5)

def main():
    session_id = sys.argv[1] if len(sys.argv) > 1 else ""
    prompt = sys.argv[2] if len(sys.argv) > 2 else ""
    path = find_log_path(session_id)
    if not path:
        print("Transcript file not found.")
        sys.exit(1)
    tail_transcript(path, session_id, prompt)

if __name__ == "__main__":
    main()
