#!/usr/bin/env python3
import os
import sys
import json
import re
import subprocess
import glob

# Antigravity paths
AGY_BRAIN_DIR = "/data/data/com.termux/files/home/.gemini/antigravity-cli/brain"
# Gemini CLI paths
GEMINI_TMP_DIR = "/data/data/com.termux/files/home/.gemini/tmp"

def get_gemini_sessions(target_dir=None):
    # Derive project name from target_dir relative to home
    home = os.environ.get("HOME", "/data/data/com.termux/files/home")
    candidates = []
    
    if target_dir and target_dir.startswith(home):
        rel_path = os.path.relpath(target_dir, home)
        if rel_path == ".":
            candidates.append("home")
        else:
            candidates.append(rel_path)
            if rel_path.lower() != rel_path:
                candidates.append(rel_path.lower())
    
    # Fallback/Default candidates
    for d in ["toggletalkandroid", "home"]:
        if d not in candidates: candidates.append(d)
        
    if os.path.exists(GEMINI_TMP_DIR):
        try:
            detected = [d for d in os.listdir(GEMINI_TMP_DIR) if os.path.isdir(os.path.join(GEMINI_TMP_DIR, d))]
            for d in detected:
                if d not in candidates: candidates.append(d)
        except: pass

    sessions = []
    seen_session_ids = set()

    for proj in candidates:
        chats_dir = os.path.join(GEMINI_TMP_DIR, proj, "chats")
        if not os.path.exists(chats_dir): continue

        try:
            for filename in os.listdir(chats_dir):
                if filename.endswith(".jsonl"):
                    path = os.path.join(chats_dir, filename)
                    try:
                        with open(path, "r", encoding="utf-8") as f:
                            first_line = f.readline()
                            if not first_line: continue
                            meta = json.loads(first_line)
                            sess_id = meta.get("sessionId")
                            if not sess_id or sess_id in seen_session_ids: continue
                            
                            title = None
                            f.seek(0)
                            for line in f:
                                try:
                                    entry = json.loads(line)
                                    if entry.get("type") == "user":
                                        content = entry.get("content")
                                        if isinstance(content, list) and len(content) > 0:
                                            title = content[0].get("text", sess_id)
                                        elif isinstance(content, str):
                                            title = content
                                        break
                                except: continue
                            
                            if title is None: continue
                            
                            seen_session_ids.add(sess_id)
                            title = re.sub(r"\s+", " ", title).strip()
                            if len(title) > 60:
                                title = title[:57] + "..."
                                
                            mtime = os.path.getmtime(path)
                            sessions.append({
                                "id": sess_id,
                                "title": title,
                                "mtime": mtime
                            })
                    except: continue
        except: continue
                    
    # Sort by mtime descending (most recent first)
    sessions.sort(key=lambda x: x["mtime"], reverse=True)
    for s in sessions:
        if "mtime" in s: del s["mtime"]
    return sessions

def get_antigravity_session_title(session_id):
    transcript_path = os.path.join(AGY_BRAIN_DIR, session_id, ".system_generated", "logs", "transcript.jsonl")
    if not os.path.exists(transcript_path):
        return session_id
    try:
        with open(transcript_path, "r", encoding="utf-8") as f:
            first_line = f.readline()
            if not first_line:
                return session_id
            data = json.loads(first_line)
            content = data.get("content", "")
            
            user_req_match = re.search(r"<USER_REQUEST>(.*?)(?:</USER_REQUEST>|$)", content, re.DOTALL)
            if user_req_match:
                title = user_req_match.group(1).strip()
            else:
                title = content.strip()
            
            title = re.sub(r"<[^>]+>", "", title).strip()
            title = re.sub(r"\s+", " ", title)
            
            if not title:
                return session_id
            if len(title) > 60:
                title = title[:57] + "..."
            return title
    except Exception:
        return session_id

def get_antigravity_sessions():
    if not os.path.exists(AGY_BRAIN_DIR):
        return []
    sessions = []
    for item in os.listdir(AGY_BRAIN_DIR):
        item_path = os.path.join(AGY_BRAIN_DIR, item)
        if os.path.isdir(item_path):
            if os.path.exists(os.path.join(item_path, ".system_generated")):
                title = get_antigravity_session_title(item)
                mtime = 0
                transcript_path = os.path.join(item_path, ".system_generated", "logs", "transcript.jsonl")
                if os.path.exists(transcript_path):
                    mtime = os.path.getmtime(transcript_path)
                sessions.append({
                    "id": item,
                    "title": title,
                    "mtime": mtime
                })
    sessions.sort(key=lambda x: x["mtime"], reverse=True)
    for s in sessions:
        del s["mtime"]
    return sessions

def main():
    agent = os.environ.get("AGENT", "antigravity")
    target_dir = sys.argv[1] if len(sys.argv) > 1 else None
    
    if agent == "gemini":
        print(json.dumps(get_gemini_sessions(target_dir)))
    else:
        print(json.dumps(get_antigravity_sessions()))

if __name__ == "__main__":
    main()
