# Task Part 1: Turn Parsing and PID Process Tracking

This task details the parsing logic fixes in Python and process PID tracking in Bash to support reliable conversation flow and clean process termination.

---

## 1. Rubric of Criteria

| Specification ID | Success Criteria (Pass) | Failure Criteria (Fail) |
|---|---|---|
| **R-TRM-1** | Terminate button next to spinner immediately kills background script process. | Terminate button is missing or process continues running. |
| **R-TRM-2** | Terminating a session preserves all queued prompts. | Queue is cleared upon termination. |

---

## 2. Research & Context
- Transcript logs are saved as JSON lines in `~/.gemini/antigravity-cli/brain/<session_id>/.system_generated/logs/transcript_full.jsonl`.
- `transcript_parser.py` parses these steps to extract user inputs, planner responses, thoughts, and tool executions.
- `run_antigravity.sh` invokes the background streaming parser (`stream_session.py`) and runs the `agy` compiler.

---

## 3. Implementation Steps

### A. Turn-Based Parsing Logic in `transcript_parser.py`
To prevent final messages from disappearing when the conversation continues, modify `parse_transcript_steps(raw_steps)`:
1. Scan the list to find all indices of steps with `"type": "USER_INPUT"`.
2. Segment the step array into turn blocks. Each block starts at a `USER_INPUT` step and ends before the next `USER_INPUT` (or end of file).
3. Within each turn block, find the last `PLANNER_RESPONSE` step where `source == "MODEL"`.
4. Only mark this specific step as `is_final = True` (if `status == "DONE"` or containing `<tts>` tags).
5. Ensure all other `PLANNER_RESPONSE` steps are parsed as thoughts (`role: "thought"`), while the final one is parsed as agent response (`role: "agent"`).

### B. PID Tracking in `run_antigravity.sh`
1. Define the PID file path based on the session ID:
   `PID_FILE="$HOME/.gemini/antigravity-cli/brain/$SESSION_ID/.system_generated/logs/run.pid"`
2. Write the process ID (`$$`) to this file immediately after starting the script:
   `echo "$$" > "$PID_FILE"`
3. Add a trap handler to clean up the PID file on exit:
   `trap 'rm -f "$PID_FILE"' EXIT`

---

## 4. Verification Plan
- Run `run_antigravity.sh` with a dummy prompt and verify `run.pid` is written to the correct folder.
- Execute `transcript_parser.py` test cases with mock transcripts containing multiple turns and verify that previous turns' final responses still preserve `"role": "agent"`.
