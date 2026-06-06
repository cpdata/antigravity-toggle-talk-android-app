import re
import json

def parse_transcript_steps(raw_steps):
    """
    Parses a list of JSON transcript steps and returns a list of dictionaries
    representing messages to display, each with 'role' and 'text'.
    
    Supports both Antigravity (USER_INPUT/PLANNER_RESPONSE) and Gemini CLI (user/gemini/etc.) formats.
    """
    if not raw_steps:
        return []

    # Detect format based on the first few entries
    is_gemini_cli = False
    for step in raw_steps:
        if step.get("type") in ["user", "gemini", "warning"]:
            is_gemini_cli = True
            break
        if step.get("type") in ["USER_INPUT", "PLANNER_RESPONSE"]:
            break

    if is_gemini_cli:
        return parse_gemini_cli_format(raw_steps)
    else:
        return parse_antigravity_format(raw_steps)

def parse_gemini_cli_format(raw_steps):
    messages = []
    for obj in raw_steps:
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

    return messages

def parse_antigravity_format(raw_steps):
    messages = []
    num_steps = len(raw_steps)

    user_input_indices = [i for i, step in enumerate(raw_steps) if step.get("type") == "USER_INPUT"]
    blocks = []
    if not user_input_indices:
        blocks.append((0, num_steps))
    else:
        if user_input_indices[0] > 0:
            blocks.append((0, user_input_indices[0]))
        for i in range(len(user_input_indices)):
            start = user_input_indices[i]
            end = user_input_indices[i+1] if i + 1 < len(user_input_indices) else num_steps
            blocks.append((start, end))

    final_planner_response_indices = set()
    for start, end in blocks:
        last_pr_idx = -1
        for idx in range(start, end):
            step = raw_steps[idx]
            if step.get("type") == "PLANNER_RESPONSE" and step.get("source") == "MODEL":
                last_pr_idx = idx
        if last_pr_idx != -1:
            final_planner_response_indices.add(last_pr_idx)

    for idx, obj in enumerate(raw_steps):
        msg_type = obj.get("type", "")
        source = obj.get("source", "")
        content = obj.get("content") or obj.get("thinking") or ""
        tool_calls = obj.get("tool_calls") or []

        if msg_type == "USER_INPUT" and content.strip():
            m = re.search(r"<USER_REQUEST>(.*?)(?:</USER_REQUEST>|$)", content, re.DOTALL)
            text = m.group(1).strip() if m else content.strip()
            text = re.sub(r"<[^>]+>.*?</[^>]+>", "", text, flags=re.DOTALL).strip()
            text = re.sub(r"<[^>]+>", "", text).strip()
            if text:
                messages.append({"role": "user", "text": text})

        elif msg_type == "PLANNER_RESPONSE" and source == "MODEL":
            is_final = (idx in final_planner_response_indices)
            stripped_content = content.strip() if content else ""
            if stripped_content:
                status = obj.get("status", "")
                if is_final and status == "DONE":
                    messages.append({"role": "agent", "text": stripped_content})
                else:
                    messages.append({"role": "thought", "text": stripped_content})

            for tc in tool_calls:
                tc_name = tc.get("name", "")
                tc_args = tc.get("args", {})
                args_str = ", ".join(f"{k}={v}" for k, v in tc_args.items())
                messages.append({"role": "tool_call", "text": f"Calling tool {tc_name}({args_str})"})

        elif msg_type not in ["USER_INPUT", "PLANNER_RESPONSE"]:
            text = content.strip() if content else ""
            if not text and "error" in obj:
                text = f"Error: {obj['error']}"
            if text:
                friendly_type = msg_type.replace("_", " ").title()
                status = obj.get("status", "")
                status_suffix = f" ({status})" if status and status != "DONE" else ""
                messages.append({"role": "tool_result", "text": f"{friendly_type}{status_suffix}:\n{text}"})

    return messages
