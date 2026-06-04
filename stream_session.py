#!/usr/bin/env python3
import os
import sys
import json
import time
import re
import subprocess
import signal

BRAIN_DIR = "/data/data/com.termux/files/home/.gemini/antigravity-cli/brain"
stop_requested = False

def handle_signal(signum, frame):
    global stop_requested
    stop_requested = True

signal.signal(signal.SIGTERM, handle_signal)
signal.signal(signal.SIGINT, handle_signal)

def find_log_path(session_id):
    if session_id:
        path = os.path.join(BRAIN_DIR, session_id, ".system_generated", "logs", "transcript_full.jsonl")
        return path
    
    # Capture initial directories to exclude them (avoid latching onto the previous session)
    initial_dirs = set()
    if os.path.exists(BRAIN_DIR):
        try:
            initial_dirs = {d for d in os.listdir(BRAIN_DIR) if os.path.isdir(os.path.join(BRAIN_DIR, d))}
        except Exception:
            pass
            
    # Wait for a new transcript file to appear
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
                    if os.path.exists(path):
                        return path
        except Exception:
            pass
        time.sleep(0.2)
        
    # Fallback to the latest directory overall if no new directory was created after timeout
    try:
        subdirs = [os.path.join(BRAIN_DIR, d) for d in os.listdir(BRAIN_DIR) if os.path.isdir(os.path.join(BRAIN_DIR, d))]
        if subdirs:
            latest_dir = max(subdirs, key=os.path.getmtime)
            path = os.path.join(latest_dir, ".system_generated", "logs", "transcript_full.jsonl")
            if os.path.exists(path):
                return path
    except Exception:
        pass
        
    return None

def send_broadcast(session_id, step_index, role, text):
    cmd = [
        "am", "broadcast", "--user", "0",
        "-p", "com.toggletalk.android",
        "-a", "com.toggletalk.android.ACTION_STREAM_UPDATE",
        "--es", "session_id", session_id,
        "--ei", "step_index", str(step_index),
        "--es", "role", role,
        "--es", "text", text
    ]
    subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

def find_start_index(lines, prompt):
    if not prompt:
        return len(lines)
    
    # Normalize prompt for comparison
    norm_prompt = re.sub(r'\s+', ' ', prompt.strip().lower())
    
    # Search backwards for a matching USER_INPUT
    for idx in range(len(lines) - 1, -1, -1):
        try:
            obj = json.loads(lines[idx].strip())
            if obj.get("type") == "USER_INPUT":
                content = obj.get("content") or ""
                norm_content = re.sub(r'\s+', ' ', content.strip().lower())
                if norm_prompt in norm_content or norm_content in norm_prompt:
                    return idx
        except Exception:
            pass
    return len(lines)

def tail_transcript(path, session_id, prompt):
    global stop_requested
    
    # Read initial lines if file exists
    initial_lines = []
    if os.path.exists(path):
        try:
            with open(path, "r", encoding="utf-8") as f:
                initial_lines = f.readlines()
        except Exception:
            pass

    # Find the starting index for streaming
    start_idx = find_start_index(initial_lines, prompt)

    # Extract session ID from path if not provided
    if not session_id:
        parts = path.split(os.sep)
        if len(parts) >= 6:
            session_id = parts[-4]
        else:
            session_id = "unknown"

    print(f"Streaming from {path} for session {session_id}, starting from index {start_idx}")

    current_line_idx = start_idx
    while True:
        if not os.path.exists(path):
            if stop_requested:
                break
            time.sleep(0.5)
            continue
            
        try:
            with open(path, "r", encoding="utf-8") as f:
                lines = f.readlines()
                
            # If the file was truncated/recreated
            if len(lines) < current_line_idx:
                current_line_idx = 0
                
            while current_line_idx < len(lines):
                line = lines[current_line_idx].strip()
                current_line_idx += 1
                
                if not line:
                    continue
                    
                try:
                    obj = json.loads(line)
                    msg_type = obj.get("type", "")
                    source = obj.get("source", "")
                    content = obj.get("content") or obj.get("thinking") or ""
                    step_index = obj.get("step_index", -1)
                    
                    if msg_type == "USER_INPUT" and content.strip():
                        m = re.search(r"<USER_REQUEST>(.*?)(?:</USER_REQUEST>|$)", content, re.DOTALL)
                        text = m.group(1).strip() if m else content.strip()
                        text = re.sub(r"<[^>]+>.*?</[^>]+>", "", text, flags=re.DOTALL).strip()
                        text = re.sub(r"<[^>]+>", "", text).strip()
                        if text:
                            send_broadcast(session_id, step_index, "user", text)
                        
                    elif msg_type == "PLANNER_RESPONSE" and source == "MODEL":
                        tool_calls = obj.get("tool_calls") or []
                        if tool_calls:
                            if content.strip():
                                send_broadcast(session_id, step_index, "thought", content.strip())
                            for tc in tool_calls:
                                tc_name = tc.get("name", "")
                                tc_args = tc.get("args", {})
                                args_str = ", ".join(f"{k}={v}" for k, v in tc_args.items())
                                send_broadcast(session_id, step_index, "tool_call", f"Calling tool {tc_name}({args_str})")
                        else:
                            if content.strip():
                                send_broadcast(session_id, step_index, "agent", content.strip())
                            
                    elif msg_type in ["RUN_COMMAND", "LIST_DIRECTORY", "GENERIC", "SYSTEM_MESSAGE"] and content.strip():
                        status = obj.get("status", "")
                        status_suffix = f" ({status})" if status and status != "DONE" else ""
                        send_broadcast(session_id, step_index, "tool_call", f"Tool result{status_suffix}:\n{content.strip()}")
                except Exception as e:
                    print(f"Error parsing line: {e}")
            
            # Break if stop was requested and we have read all lines
            if stop_requested and current_line_idx >= len(lines):
                break
                
        except Exception as e:
            print(f"Error reading file: {e}")
            if stop_requested:
                break
            
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
