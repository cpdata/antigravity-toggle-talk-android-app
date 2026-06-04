import re
import json

def parse_transcript_steps(raw_steps):
    """
    Parses a list of JSON transcript steps and returns a list of dictionaries
    representing messages to display, each with 'role' and 'text'.
    
    Roles:
      - 'user': User message
      - 'agent': Final agent response of a turn
      - 'thought': Intermediate planning/thoughts from the model
      - 'tool_call': Tool calls, execution results, system messages, or errors
    """
    messages = []
    num_steps = len(raw_steps)

    for idx, obj in enumerate(raw_steps):
        msg_type = obj.get("type", "")
        source = obj.get("source", "")
        content = obj.get("content") or obj.get("thinking") or ""
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

        elif msg_type == "PLANNER_RESPONSE" and source == "MODEL":
            # Check if this is the final agent response of a turn.
            # It is final if:
            # 1. It has no tool calls AND
            # 2. There are no subsequent steps before the next USER_INPUT or the end of the file.
            is_final = False
            if not tool_calls:
                is_final = True
                for next_idx in range(idx + 1, num_steps):
                    next_obj = raw_steps[next_idx]
                    next_type = next_obj.get("type", "")
                    if next_type == "USER_INPUT":
                        break
                    else:
                        is_final = False
                        break

            stripped_content = content.strip() if content else ""
            if stripped_content:
                if is_final:
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
