# Task Part 1: Turn Parsing and PID Process Tracking

This task details the parsing logic fixes in Python and process PID tracking in Bash to support reliable conversation flow and clean process termination.

---

## 1. Rubric of Criteria

| Specification ID | Success Criteria (Pass) | Failure Criteria (Fail) |
|---|---|---|
| **R-TRM-1** | Terminate button next to spinner immediately kills background script process. | Terminate button is missing or process continues running. |
| **R-TRM-2** | Terminating a session preserves all queued prompts. | Queue is cleared upon termination. |

---

## 2. Context & Background Research

### Turn-Based Parsing Logic
In our system, transcript logs under `~/.gemini/antigravity-cli/brain/<session_id>/.system_generated/logs/transcript_full.jsonl` record every step during a conversation. Each step is represented as a JSON line. Both intermediate reasoning steps (thoughts/tool calls) and the final response of a turn are written as `PLANNER_RESPONSE` steps.
- **Intermediate steps**: Should be collapsed in the chat UI under expandable bubbles so they don't clutter the view.
- **Final steps**: Should be rendered as normal, non-collapsible chat bubbles.

To differentiate these, the transcript parser tracks `is_final`. If `is_final` is true and the step is complete (`status == "DONE"`), it is parsed as `role: "agent"`. If `is_final` is false, it is parsed as `role: "thought"`. 

The current implementation checks if the *immediate next* step in the log is `USER_INPUT`. However, if background cron/task logs occur right after a response, the immediate next step is not `USER_INPUT`, causing `is_final` to evaluate as `false` and hiding the final response in a collapsed block.

Also, the presence of `<tts>...</tts>` tags should **never** affect the calculation of `is_final`. The finality of a planner response is solely based on whether it is the last planner response of the turn block.

### Component Locations
- `transcript_parser.py`: `/data/data/com.termux/files/home/ToggleTalkAndroid/transcript_parser.py`
- `run_antigravity.sh`: `/data/data/com.termux/files/home/ToggleTalkAndroid/run_antigravity.sh`

---

## 3. Implementation Steps

### A. Turn-Based Parsing Logic in `transcript_parser.py`
Modify `parse_transcript_steps(raw_steps)` to correctly identify the final response of each turn:
1. Scan the list of steps and extract the indices of all steps with `"type": "USER_INPUT"`.
2. Segment the raw steps list into logical turn blocks. Each block starts at a `USER_INPUT` step and spans up to (but excluding) the next `USER_INPUT` step (or the end of the file).
3. Within each turn block, identify the last step of type `PLANNER_RESPONSE` where `source == "MODEL"`.
4. Only set `is_final = True` for this last planner response step of the turn if `status == "DONE"`. (Remove any check for `<tts>` tags deciding finality).
5. All other planner response steps in the block must be parsed as thoughts (`role: "thought"`), while the final one is parsed as agent response (`role: "agent"`).
6. The parser MUST NOT split the step content into separate thought and agent messages based on the presence of `<tts>...</tts>` tags. Instead, retain the original content with the `<tts>` tags intact as a single message under the determined role (either `"thought"` or `"agent"`). This ensures `<tts>` notifications can be provided in any non-tool message, not just final ones.


### B. PID Process Tracking in `run_antigravity.sh`
To allow the Android service to terminate a running session immediately and cleanly:
1. Define a session-specific PID file path:
   `PID_FILE="$HOME/.gemini/antigravity-cli/brain/$SESSION_ID/.system_generated/logs/run.pid"`
2. Create the parent directories if they do not exist:
   `mkdir -p "$(dirname "$PID_FILE")"`
3. Write the PID of the running bash script (`$$`) to this file immediately after execution starts:
   `echo "$$" > "$PID_FILE"`
4. Set up an exit trap to ensure the PID file is deleted when the script exits:
   `trap 'rm -f "$PID_FILE"' EXIT`

---

## 4. Verification & Testing Plan
- Run `run_antigravity.sh` with a dummy session ID and verify that `run.pid` containing the correct shell PID is written under the corresponding session logs folder.
- Run `python3 -c "import transcript_parser; ..."` or execute unit tests to parse a multi-turn transcript and verify that final messages from earlier turns remain categorized as `"role": "agent"` even when followed by subsequent user turns or task updates.
