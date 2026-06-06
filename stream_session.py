#!/usr/bin/env python3
import os
import sys
import json
import time
import re
import subprocess
import signal
import glob

DEFAULT_BRAIN_DIR = "/data/data/com.termux/files/home/.gemini/antigravity-cli/brain"
GEMINI_TMP_DIR = "/data/data/com.termux/files/home/.gemini/tmp"
BRAIN_DIR = os.environ.get("AGENT_BRAIN_DIR", DEFAULT_BRAIN_DIR)
stop_requested = False

def handle_signal(signum, frame):
    global stop_requested
    stop_requested = True

signal.signal(signal.SIGTERM, handle_signal)
signal.signal(signal.SIGINT, handle_signal)

def find_gemini_transcript(session_id):
    project_name = os.path.basename(os.getcwd()).lower()
    chats_dir = os.path.join(GEMINI_TMP_DIR, project_name, "chats")
    print(f"Searching for transcript. project={project_name}, session_id={session_id}")
    
    if session_id and not session_id.startswith("new_"):
        if os.path.exists(chats_dir):
            pattern = os.path.join(chats_dir, f"session-*-{session_id[:8]}*.jsonl")
            files = glob.glob(pattern)
            if files: 
                print(f"Found transcript by pattern: {files[0]}")
                return files[0]
            
            for f in glob.glob(os.path.join(chats_dir, "*.jsonl")):
                try:
                    with open(f, "r") as fh:
                        if session_id in fh.readline(): 
                            print(f"Found transcript by scanning: {f}")
                            return f
                except: continue

        print("Falling back to global search...")
        pattern = os.path.join(GEMINI_TMP_DIR, "*", "chats", "*.jsonl")
        for f in glob.glob(pattern):
            try:
                with open(f, "r") as fh:
                    if session_id in fh.readline(): 
                        print(f"Found transcript globally: {f}")
                        return f
            except: continue
    
    if os.path.exists(chats_dir):
        print(f"New session. Waiting for new file in {chats_dir}...")
        start_time = time.time()
        initial_files = set(os.listdir(chats_dir))
        while time.time() - start_time < 30:
            if os.path.exists(chats_dir):
                current_files = set(os.listdir(chats_dir))
                new_files = current_files - initial_files
                if new_files:
                    new_paths = [os.path.join(chats_dir, f) for f in new_files]
                    res = max(new_paths, key=os.path.getmtime)
                    print(f"Found new transcript file: {res}")
                    return res
            time.sleep(0.5)
        
    print("Transcript not found.")
    return None

def find_log_path(session_id):
    explicit_path = os.environ.get("AGENT_TRANSCRIPT_PATH")
    if explicit_path and os.path.exists(explicit_path):
        return explicit_path

    agent = os.environ.get("AGENT", "antigravity")
    if agent == "gemini":
        return find_gemini_transcript(session_id)

    if session_id and not session_id.startswith("new_"):
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

agent = os.environ.get("AGENT", "antigravity")
if agent == "gemini":
    from gemini_transcript_parser import parse_transcript_steps
else:
    from antigravity_transcript_parser import parse_transcript_steps

def send_broadcast(session_id, json_str=None, file_path=None, tts_text=None):
    cmd = [
        "am", "broadcast", "--user", "0",
        "-p", "com.toggletalk.android",
        "-a", "com.toggletalk.android.ACTION_STREAM_UPDATE",
        "--es", "session_id", session_id
    ]
    if json_str:
        cmd += ["--es", "messages_json", json_str]
    if file_path:
        cmd += ["--es", "file_path", file_path]
    if tts_text:
        cmd += ["--es", "tts_text", tts_text]
    subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

def extract_tts_text(step_obj):
    msg_type = step_obj.get("type", "")
    source = step_obj.get("source", "")
    if msg_type == "PLANNER_RESPONSE" and source == "MODEL":
        content = step_obj.get("content") or step_obj.get("thinking") or ""
        tts_segments = re.findall(r"<tts>(.*?)</tts>", content, re.DOTALL)
        if tts_segments:
            return " ".join(seg.strip() for seg in tts_segments).strip()
    elif msg_type == "gemini":
        content = step_obj.get("content", "")
        tts_segments = re.findall(r"<tts>(.*?)</tts>", content, re.DOTALL)
        if tts_segments:
            return " ".join(seg.strip() for seg in tts_segments).strip()
    return None

def find_start_index(lines, prompt):
    if not prompt:
        return len(lines)
    
    agent = os.environ.get("AGENT", "antigravity")
    
    # Find the index of the last final response (the end of the last turn)
    last_final_resp_idx = -1
    for idx in range(len(lines) - 1, -1, -1):
        try:
            obj = json.loads(lines[idx].strip())
            if agent == "gemini":
                # For Gemini CLI, assume last agent message represents the end of the previous turn
                if obj.get("type") == "gemini":
                    last_final_resp_idx = idx
                    break
            else:
                if obj.get("type") == "PLANNER_RESPONSE" and obj.get("source") == "MODEL":
                    tool_calls = obj.get("tool_calls") or []
                    if not tool_calls: # Final response has no tool calls
                        last_final_resp_idx = idx
                        break
        except Exception:
            pass
            
    # Look for the USER_INPUT step after the last final response
    for idx in range(last_final_resp_idx + 1, len(lines)):
        try:
            obj = json.loads(lines[idx].strip())
            if agent == "gemini":
                if obj.get("type") == "user":
                    return idx
            else:
                if obj.get("type") == "USER_INPUT":
                    # Found the new user input! Start streaming from here.
                    return idx
        except Exception:
            pass
            
    # If no USER_INPUT was found after the last final response, it hasn't been written yet.
    # Start streaming from the end of the file.
    return len(lines)

def tail_transcript(path, session_id, prompt):
    global stop_requested
    
    if not session_id or session_id.startswith("new_"):
        try:
            if os.path.exists(path):
                with open(path, "r", encoding="utf-8") as f:
                    first_line = f.readline()
                    if first_line:
                        obj = json.loads(first_line)
                        if "sessionId" in obj:
                            session_id = obj["sessionId"]
                            print(f"Pre-adopted session ID from first line: {session_id}")
        except: pass

    if not session_id or session_id.startswith("new_"):
        if "antigravity-cli/brain" in path:
            parts = path.split(os.sep)
            try:
                brain_idx = parts.index("brain")
                if len(parts) > brain_idx + 1:
                    session_id = parts[brain_idx + 1]
                    print(f"Pre-adopted session ID from path: {session_id}")
            except ValueError:
                pass
                
    if not session_id or session_id.startswith("new_"):
        session_id = "unknown"

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
                if stop_requested:
                    break
                line = lines[current_line_idx].strip()
                current_line_idx += 1
                
                if not line:
                    continue
                    
                try:
                    obj = json.loads(line)
                    
                    # Update session_id if it's still a placeholder
                    if (not session_id or session_id == "unknown" or session_id.startswith("new_")) and "sessionId" in obj:
                        old_session_id = session_id
                        session_id = obj["sessionId"]
                        print(f"DEBUG: Adopted real session ID from stream: {old_session_id} -> {session_id}")

                    # Extract any TTS text to speak from this new step
                    tts_text = extract_tts_text(obj)
                    
                    # Parse all steps up to the current line
                    parsed_steps = []
                    for l in lines[:current_line_idx]:
                        l = l.strip()
                        if l:
                            try:
                                parsed_steps.append(json.loads(l))
                            except Exception:
                                pass
                    
                    messages = parse_transcript_steps(parsed_steps)
                    json_str = json.dumps(messages)
                    
                    print(f"DEBUG: Broadcasting update for session: {session_id}")
                    if len(json_str.encode('utf-8')) < 90 * 1024:
                        send_broadcast(session_id, json_str=json_str, tts_text=tts_text)
                    else:
                        out_dir = "/sdcard/Android/media/com.toggletalk.android"
                        os.makedirs(out_dir, exist_ok=True)
                        out_path = os.path.join(out_dir, "stream_history.json")
                        try:
                            with open(out_path, "w", encoding="utf-8") as out_f:
                                out_f.write(json_str)
                            send_broadcast(session_id, file_path=out_path, tts_text=tts_text)
                        except Exception:
                            # Fallback if SD card write fails
                            send_broadcast(session_id, json_str=json_str, tts_text=tts_text)
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