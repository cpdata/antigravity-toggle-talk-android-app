#!/usr/bin/env python3
import os
import json
import re

BRAIN_DIR = "/data/data/com.termux/files/home/.gemini/antigravity-cli/brain"

def get_session_title(session_id):
    transcript_path = os.path.join(BRAIN_DIR, session_id, ".system_generated", "logs", "transcript.jsonl")
    if not os.path.exists(transcript_path):
        return session_id
    try:
        with open(transcript_path, "r", encoding="utf-8") as f:
            first_line = f.readline()
            if not first_line:
                return session_id
            data = json.loads(first_line)
            content = data.get("content", "")
            
            # Extract content from <USER_REQUEST>...</USER_REQUEST> if present
            user_req_match = re.search(r"<USER_REQUEST>(.*?)(?:</USER_REQUEST>|$)", content, re.DOTALL)
            if user_req_match:
                title = user_req_match.group(1).strip()
            else:
                title = content.strip()
            
            # Remove any XML/HTML tags
            title = re.sub(r"<[^>]+>", "", title).strip()
            
            # Clean up newlines and excessive whitespaces
            title = re.sub(r"\s+", " ", title)
            
            if not title:
                return session_id
                
            # Truncate title if it's too long
            if len(title) > 60:
                title = title[:57] + "..."
            return title
    except Exception as e:
        return session_id

def main():
    if not os.path.exists(BRAIN_DIR):
        print(json.dumps([]))
        return
        
    sessions = []
    for item in os.listdir(BRAIN_DIR):
        item_path = os.path.join(BRAIN_DIR, item)
        if os.path.isdir(item_path):
            if os.path.exists(os.path.join(item_path, ".system_generated")):
                title = get_session_title(item)
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
        
    print(json.dumps(sessions))

if __name__ == "__main__":
    main()
