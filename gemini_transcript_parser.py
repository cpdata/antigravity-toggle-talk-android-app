import re
import json

def parse_transcript_steps(raw_steps):
    """
    Parses a list of JSON transcript steps from Gemini CLI and returns a list 
    of dictionaries representing messages to display.
    """
    return parse_gemini_cli_format(raw_steps)

def parse_gemini_cli_format(raw_steps):
    messages = []
    steps_dict = {}
    ordered_ids = []
    
    for obj in raw_steps:
        if "id" in obj:
            step_id = obj["id"]
            if step_id not in steps_dict:
                ordered_ids.append(step_id)
            steps_dict[step_id] = obj
        elif "$set" in obj:
            # $set updates are not directly useful without knowing which ID they apply to
            # Gemini CLI usually repeats the whole object with updated content anyway
            pass

    for step_id in ordered_ids:
        obj = steps_dict[step_id]
        msg_type = obj.get("type")
        content = obj.get("content")
        
        if msg_type == "user":
            text = ""
            if isinstance(content, list):
                text = " ".join(part.get("text", "") for part in content if isinstance(part, dict))
            elif isinstance(content, str):
                text = content
            
            if text.strip():
                messages.append({"role": "user", "text": text.strip()})
        
        elif msg_type == "gemini":
            text = ""
            if isinstance(content, str):
                text = content
            
            # Extract thoughts as separate messages
            thoughts = obj.get("thoughts", [])
            for thought in thoughts:
                thought_text = thought.get("description", "")
                if thought_text.strip():
                    messages.append({"role": "thought", "text": thought_text.strip()})
            
            # Handle tool calls
            tool_calls = obj.get("toolCalls", [])
            for tc in tool_calls:
                tc_name = tc.get("name", "")
                tc_args = tc.get("args", {})
                args_str = ", ".join(f"{k}={v}" for k, v in tc_args.items())
                messages.append({"role": "tool_call", "text": f"Calling tool {tc_name}({args_str})"})
                
                # Check for embedded results in Gemini CLI format
                tc_result = tc.get("result", [])
                if tc_result:
                    for res in tc_result:
                        if isinstance(res, dict) and "functionResponse" in res:
                            resp = res["functionResponse"].get("response", {})
                            output = resp.get("output", "")
                            if not output and "content" in resp:
                                output = resp["content"]
                            if output:
                                messages.append({"role": "tool_result", "text": f"{tc_name.title()} Result:\n{output.strip()}"})
            
            if text.strip():
                messages.append({"role": "agent", "text": text.strip()})
        
        elif msg_type == "tool_result":
            # Gemini CLI tool results
            text = ""
            if isinstance(content, str):
                text = content
            elif isinstance(content, dict):
                text = json.dumps(content, indent=2)
            
            if text.strip():
                tool_name = obj.get("toolName", "tool")
                messages.append({"role": "tool_result", "text": f"{tool_name.title()} Result:\n{text.strip()}"})

        elif msg_type == "warning":
            text = content.strip() if isinstance(content, str) else ""
            if text:
                messages.append({"role": "agent", "text": f"Warning: {text}"})

    return messages

