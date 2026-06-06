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
    # Try multiple possible project names
    project_names = ["toggletalkandroid", os.path.basename(os.getcwd()).lower(), "home"]
    
    # Also look at directories in GEMINI_TMP_DIR
    if os.path.exists(GEMINI_TMP_DIR):
        try:
            project_names.extend([d for d in os.listdir(GEMINI_TMP_DIR) if os.path.isdir(os.path.join(GEMINI_TMP_DIR, d))])
        except: pass
    
    # Deduplicate while preserving order
    project_names = list(dict.fromkeys(project_names))
    
    print(f"Searching for transcript. session_id='{session_id}', projects={project_names}")
    
    if session_id and not session_id.startswith("new_"):
        for project in project_names:
            chats_dir = os.path.join(GEMINI_TMP_DIR, project, "chats")
            if os.path.exists(chats_dir):
                # Try specific pattern first
                pattern = os.path.join(chats_dir, f"session-*-{session_id[:8]}*.jsonl")
                files = glob.glob(pattern)
                if files: 
                    print(f"Found transcript by pattern in {project}: {files[0]}")
                    return files[0]
                
                # Fallback: scan files in this project's chats_dir
                for f in glob.glob(os.path.join(chats_dir, "*.jsonl")):
                    try:
                        with open(f, "r") as fh:
                            first_line = fh.readline()
                            if session_id in first_line: 
                                print(f"Found transcript by scanning in {project}: {f}")
                                return f
                    except: continue

        print("Falling back to global search...")
        pattern = os.path.join(GEMINI_TMP_DIR, "*", "chats", "*.jsonl")
        for f in glob.glob(pattern):
            try:
                with open(f, "r") as fh:
                    first_line = fh.readline()
                    if session_id in first_line: 
                        print(f"Found transcript globally: {f}")
                        return f
            except: continue
    
    # For new sessions or if not found yet, watch all possible chats directories
    print(f"Waiting for transcript file to appear...")
    start_time = time.time()
    
    initial_states = {}
    for project in project_names:
        chats_dir = os.path.join(GEMINI_TMP_DIR, project, "chats")
        if os.path.exists(chats_dir):
            try:
                initial_states[chats_dir] = set(os.listdir(chats_dir))
            except: pass
    
    while time.time() - start_time < 30:
        for project in project_names:
            chats_dir = os.path.join(GEMINI_TMP_DIR, project, "chats")
            if not os.path.exists(chats_dir): continue
            
            try:
                current_files = set(os.listdir(chats_dir))
                initial_files = initial_states.get(chats_dir, set())
                new_files = current_files - initial_files
                
                if new_files:
                    new_paths = [os.path.join(chats_dir, f) for f in new_files]
                    res = max(new_paths, key=os.path.getmtime)
                    print(f"Found new transcript file in {project}: {res}")
                    return res
                
                # If session_id is still unknown, we might have to settle for the newest file overall
                # if it was modified VERY recently (within the last few seconds)
                all_files = [os.path.join(chats_dir, f) for f in current_files if f.endswith(".jsonl")]
                if all_files:
                    latest = max(all_files, key=os.path.getmtime)
                    if os.path.getmtime(latest) > start_time:
                        print(f"Adopting recent transcript file in {project}: {latest}")
                        return latest
            except: pass
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
        if os.path.exists(path):
            return path
    
    # Capture initial directories to exclude them (avoid latching onto the previous session)
    initial_dirs = set()
    if os.path.exists(BRAIN_DIR):
        try:
            initial_dirs = {d for d in os.listdir(BRAIN_DIR) if os.path.isdir(os.path.join(BRAIN_DIR, d))}
        except Exception:
            pass
            
    # Wait for a new transcript file to appear
    print(f"Waiting for new Antigravity session directory in {BRAIN_DIR}...")
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
                    # Wait a bit for the file to actually be created within the new dir
                    for _ in range(20):
                        if os.path.exists(path):
                            print(f"Found new Antigravity transcript: {path}")
                            return path
                        time.sleep(0.2)
                
                # Fallback: check if ANY directory was modified recently
                all_dirs = [os.path.join(BRAIN_DIR, d) for d in current_dirs]
                if all_dirs:
                    latest_dir = max(all_dirs, key=os.path.getmtime)
                    if os.path.getmtime(latest_dir) > start_time:
                        path = os.path.join(latest_dir, ".system_generated", "logs", "transcript_full.jsonl")
                        if os.path.exists(path):
                            print(f"Adopting recent Antigravity transcript: {path}")
                            return path
        except Exception:
            pass
        time.sleep(0.5)
        
    print("Antigravity transcript not found.")
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

def find_start_index(lines, prompt, session_id):
    agent = os.environ.get("AGENT", "antigravity")
    
    # If it's a new session, we want everything
    if not session_id or session_id == "unknown" or session_id.startswith("new_"):
        print("New session detected, starting from index 0")
        return 0
        
    if not prompt:
        # If no prompt, start from the beginning to show full history
        return 0
    
    # Clean prompt for matching
    clean_prompt = prompt.strip().lower()
    
    # Find the last USER_INPUT that matches the prompt
    # We look backwards to find the most recent one
    for idx in range(len(lines) - 1, -1, -1):
        try:
            obj = json.loads(lines[idx].strip())
            content = obj.get("content", "").lower()
            msg_type = obj.get("type", "")
            
            is_user = False
            if agent == "gemini":
                if msg_type == "user": is_user = True
            else:
                if msg_type == "USER_INPUT": is_user = True
                
            if is_user and clean_prompt in content:
                print(f"Found matching prompt at index {idx}")
                return idx
        except Exception:
            pass

    # Fallback: Find the index of the last final response (the end of the last turn)
    last_final_resp_idx = -1
    for idx in range(len(lines) - 1, -1, -1):
        try:
            obj = json.loads(lines[idx].strip())
            if agent == "gemini":
                if obj.get("type") == "gemini":
                    # For Gemini, a message with content is a final response
                    if obj.get("content"):
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
                    return idx
        except Exception:
            pass
            
    # If we are here, it means we didn't find a new user input.
    # If the file has lines but we are skipping them all, that's likely wrong for a streaming session
    # unless we are sure they are old.
    
    # If the last line is a model response, maybe it's the one we are looking for?
    # To be safe, if we have a prompt but couldn't find a matching USER_INPUT, 
    # we start from after the last final response, but if that skips everything, 
    # and the prompt was recent, we might want to be less aggressive.
    
    start_idx = last_final_resp_idx + 1
    print(f"Fallback: streaming from index {start_idx} (last_final_resp_idx={last_final_resp_idx})")
    return start_idx

def tail_transcript(path, session_id, prompt):
    global stop_requested
    
    print(f"Starting tail on {path} for session {session_id}")
    
    # Initial session ID detection from path/file
    if not session_id or session_id == "unknown" or session_id.startswith("new_"):
        try:
            if os.path.exists(path):
                with open(path, "r", encoding="utf-8") as f:
                    first_line = f.readline()
                    if first_line:
                        obj = json.loads(first_line)
                        if "sessionId" in obj:
                            session_id = obj["sessionId"]
                            print(f"Adopted session ID from first line: {session_id}")
        except: pass

    if not session_id or session_id == "unknown" or session_id.startswith("new_"):
        parts = path.split(os.sep)
        if "antigravity-cli/brain" in path:
            try:
                idx = parts.index("brain")
                if len(parts) > idx + 1:
                    session_id = parts[idx + 1]
                    print(f"Adopted Antigravity session ID from path: {session_id}")
            except ValueError: pass
        elif "chats" in path:
            filename = os.path.basename(path)
            if filename.startswith("session-"):
                parts = filename.split('-')
                if len(parts) >= 5:
                    session_id = parts[-1].replace('.jsonl', '')
                    print(f"Adopted Gemini session ID from filename: {session_id}")

    # Read initial lines
    initial_lines = []
    if os.path.exists(path):
        try:
            with open(path, "r", encoding="utf-8") as f:
                initial_lines = f.readlines()
        except: pass

    current_line_idx = find_start_index(initial_lines, prompt, session_id)
    
    # If we have lines to process, process them immediately once
    if current_line_idx < len(initial_lines):
        print(f"Processing {len(initial_lines) - current_line_idx} initial lines")
    else:
        print(f"No initial lines to process (total lines: {len(initial_lines)})")

    while True:
        if stop_requested:
            print("Stop requested, exiting tail loop")
            break
            
        if not os.path.exists(path):
            time.sleep(0.5)
            continue
            
        try:
            with open(path, "r", encoding="utf-8") as f:
                lines = f.readlines()
                
            if len(lines) < current_line_idx:
                print(f"File truncated? resetting index from {current_line_idx} to 0")
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
                    
                    if (not session_id or session_id == "unknown" or session_id.startswith("new_")) and "sessionId" in obj:
                        session_id = obj["sessionId"]
                        print(f"Updated session ID from stream: {session_id}")

                    tts_text = extract_tts_text(obj)
                    
                    # Parse everything up to NOW to get full context for the app
                    parsed_steps = []
                    for l in lines[:current_line_idx]:
                        l = l.strip()
                        if l:
                            try:
                                parsed_steps.append(json.loads(l))
                            except: pass
                    
                    messages = parse_transcript_steps(parsed_steps)
                    json_str = json.dumps(messages)
                    
                    print(f"Broadcasting update for {session_id} (msg count: {len(messages)})")
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
                    print(f"Error parsing line {current_line_idx-1}: {e}")
            
        except Exception as e:
            print(f"Error reading file: {e}")
            
        time.sleep(0.5)


def main():
    session_id = sys.argv[1] if len(sys.argv) > 1 else ""
    prompt = sys.argv[2] if len(sys.argv) > 2 else ""
    
    print(f"Stream session starting. sess='{session_id}', prompt_len={len(prompt)}")
    
    path = find_log_path(session_id)
    if not path:
        print("Transcript file not found. Exiting.")
        sys.exit(1)
        
    tail_transcript(path, session_id, prompt)

if __name__ == "__main__":
    main()
