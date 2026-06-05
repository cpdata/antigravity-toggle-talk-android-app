# Detailed Implementation Plan: Session Redesign, Prompt Queueing, and TTS Playback Control

This implementation plan provides a comprehensive blueprint, root cause analysis, architectural specifications, and exact code design details to allow any agent or developer to fully implement the multi-session state tracking, prompt queueing, and advanced TTS playback fixes without any additional research.

---

## 1. Rubric of Criteria for Specifications

| Specification ID | Feature / Component | Success Criteria (Pass) | Failure Criteria (Fail) |
|---|---|---|---|
| **R-VAL-1** | Empty/Whitespace Input Rejection | Main prompt input and queue edit text fields reject transmission or update if empty or containing only spaces (`trim().isEmpty()`). | Empty or spaces-only inputs are sent to Antigravity or saved to the queue. |
| **R-QUE-1** | Queue Count Badge | Left side of the queue box displays a distinct small box containing the exact count of queued prompts. | Badge is missing, misaligned, or shows incorrect count. |
| **R-QUE-2** | Queue Text Truncation | Shows only the truncated first line of the *most recent prompt* added to the queue when collapsed. | Shows multiple lines or older prompts when collapsed. |
| **R-QUE-3** | Queue Box Expansion (1 Prompt) | Clicking the queue box expands it to show the single prompt wrapped up to exactly 3 lines. | Height does not expand, text is cut off, or wraps beyond 3 lines. |
| **R-QUE-4** | Queue Box Expansion (2 Prompts) | Expanded box shows 2 lines for the top (most recent) prompt and 1 line for the second prompt. | Prompts are given equal lines or misaligned height. |
| **R-QUE-5** | Queue Box Expansion (3 Prompts) | Expanded box shows exactly 1 line for each of the 3 prompts. | Any prompt takes more than 1 line or is pushed out of view. |
| **R-QUE-6** | Queue Box Expansion (4+ Prompts) | Expanded box shows each prompt truncated to 1 line inside a scrollable list capped at 3 lines of height. | Height grows indefinitely, or view is not scrollable. |
| **R-QUE-7** | Red Glowing Pulsating Border | If there are queued prompts, the queue box displays a red glowing border that continuously pulsates. | Border does not glow, does not pulsate, or remains visible when queue is empty. |
| **R-POP-1** | Prompt Edit Pop-up Layout | Edit pop-up appears at the very bottom of the screen hugging the edge, with narrow margins and padding. | Pop-up is centered, floats, or has wide margins. |
| **R-POP-2** | Pop-up Auto-focus & Cursor | Clicked prompt loads into edit box, auto-focuses, keyboard pops up, cursor is placed on a new line after the text (`\n` appended). | Edit field does not focus, cursor is at start, or no new line is added. |
| **R-POP-3** | Return/Enter Key Behavior | Pressing Enter/Return key in the edit field inserts a newline, matching main input box behavior. | Pressing Enter closes keyboard or updates prompt. |
| **R-POP-4** | Pop-up Action Buttons (Active) | When the session is active, the individual prompt edit pop-up includes only `Update`, `Cancel`, `DELETE`. The `Send` button is hidden. | `Send` button is visible for individual prompts during active sessions. |
| **R-POP-5** | Outside Dismiss & Cancel | Clicking Cancel or outside the pop-up dismisses it, leaving the prompt queue unchanged. | Pop-up persists, or queue is modified. |
| **R-DEL-1** | Delete & Trashcan Actions | Clicking DELETE or the red trashcan icon displays an "Are you sure?" dialog. Selecting "Yes" removes the prompt. | Prompt is deleted without confirmation, or trashcan icon is missing. |
| **R-TRM-1** | Running Session Termination | Terminate button (X) is displayed next to the activity spinner. Clicking it immediately kills the background process. | Terminate button is missing, or background process continues running. |
| **R-TRM-2** | Terminate Preserves Queue | Terminating a session preserves all queued prompts. | Queue is cleared upon termination. |
| **R-TTS-1** | Send Button Unlocked | Send button is always available to add new messages to the queue, even during active reasoning or TTS playback. | Send button changes to a terminate button or is disabled. |
| **R-TTS-2** | TTS Stop & Flush | A separate X button below the mic button terminates current TTS playback and flushes the queue immediately. | Button is missing, or flushing fails to stop active audio track. |
| **R-TTS-3** | Mic Recording Pauses TTS | Pressing mic button during TTS pauses playback immediately, keeping the TTS queue intact. | Mic recording starts without pausing TTS, or queue is cleared. |
| **R-TTS-4** | Chunk Resumption & Repeat | Once recording is released, TTS playback resumes, repeating the exact sentence chunk that was playing when paused. | Playback skips the interrupted chunk or fails to resume. |
| **R-UNS-1** | Unresolved Queue UI State | In a stopped/terminated session with a non-empty queue, the main Send button is hidden. A label `"Prompt Queue?"` and 4 vertical buttons (`Resume`, `Delete`, `Combine`, `Add`) are shown. | Send button remains visible, or the 4 vertical buttons are missing. |
| **R-UNS-2** | Resume Action | Clicking `Resume` sends the next prompt from the queue to Antigravity and executes remaining queued prompts in succession as turns finish. | Queued prompts do not auto-run or are deleted. |
| **R-UNS-3** | Delete All Action | Clicking `Delete` in the 4-button layout triggers an "Are you sure?" confirmation dialog. If "Yes", the entire queue is cleared, and the UI returns to normal. | Queue is cleared without confirmation, or UI doesn't restore. |
| **R-UNS-4** | Add Action | Clicking `Add` appends the main prompt field text to the queue, clears the input, and keeps the unresolved queue state active. | Input is not cleared, or the unresolved queue state is resolved. |
| **R-UNS-5** | Combine Action & Edit Pop-up | Clicking `Combine` opens the edit popup with all queued prompts and any main prompt text combined with double newlines (`\n\n`). Only `Send` and `Cancel` buttons are visible. | The edit pop-up has other buttons or combined text is incorrect. |
| **R-UNS-6** | Combine Send Action | Clicking `Send` in the combine pop-up sends the combined prompt immediately, clears the main prompt input field, clears the queue, and resolves the unresolved state. | Main input text is not cleared, or queue is not cleared. |
| **R-UNS-7** | Pop-up Buttons (Unresolved State) | Editing individual prompts via clicking them in unresolved queue state shows only `Update`, `Delete`, and `Cancel`. The `Send` button is hidden. | `Send` button is visible for individual prompts. |
| **R-UNS-8** | Resolution & State Restore | Once the queue is empty or resolved, the pulsating red border and 4 buttons disappear, and the main Send button is restored. | UI elements persist after resolution. |
| **R-MOD-1** | Code Modularity | Core UI logic for the prompt queue, popup overlays, and animations are implemented in separate files rather than bloating `MainActivity.java`. | All features are dumped directly into `MainActivity.java`. |

---

## 2. Research & Codebase Context

1. **Active Session UI**: Maintained in `MainActivity.java` where `mSelectedSessionId` holds the active session ID, and `mTvStatus` shows status text.
2. **Foreground Service**: `ToggleTalkService.java` manages recording, playback, and launching the background runner via `com.termux.RUN_COMMAND`.
3. **Background Runner**: `run_antigravity.sh` starts `stream_session.py` in the background and runs the `agy` compiler binaries.
4. **Log Streaming**: `stream_session.py` tails `transcript_full.jsonl` under `~/.gemini/antigravity-cli/brain/<session_id>/.system_generated/logs/` and calls `parse_transcript_steps` from `transcript_parser.py` to compile UI-ready chat history.

---

## 3. Meticulous Architectural Design

### A. Per-Session State & Queueing
`ToggleTalkService` must track states and prompt queues per session:
```java
// ToggleTalkService.java
private final Map<String, String> mSessionStates = new HashMap<>(); // sessionId -> State
private final Map<String, String> mSessionTexts = new HashMap<>();   // sessionId -> State Text
private final Map<String, List<String>> mSessionQueues = new HashMap<>(); // sessionId -> Prompt Queue
private final Set<String> mRunningSessions = new HashSet<>();        // Active agy runs
```

### B. Unresolved Queue State Definition
A session enters the **Unresolved Queue State** if:
- The session is stopped (the runner has completed or was terminated, i.e., state is `IDLE` or state is not running).
- AND the prompt queue for that session is **not empty**.

In this state:
- The queue container exhibits a glowing, pulsating red border.
- The main Send button (`mBtnSend`) is hidden.
- The vertical buttons (`Resume`, `Delete`, `Combine`, `Add`) are shown under a `"Prompt Queue?"` label.

---

## 4. Step-by-Step Implementation Details

### Step 4.1: Correct Turn Parsing in `transcript_parser.py`
Group steps in `parse_transcript_steps` into logical blocks demarcated by `USER_INPUT` steps. The last `PLANNER_RESPONSE` with status `"DONE"` or containing `<tts>` tags in a turn block will be marked as final (`role: "agent"`).

### Step 4.2: Session Termination Logic
1. **Update `run_antigravity.sh`**:
   Write the script PID to a session-specific pid file:
   `echo "$$" > "$HOME/.gemini/antigravity-cli/brain/$SESSION_ID/.system_generated/logs/run.pid"`
2. **Update `ToggleTalkService.java`**:
   Implement a shell command execution that runs: `kill -9 $(cat <pid_file_path>)`. This terminates the process immediately, while preserving the prompt queue in `mSessionQueues`.

### Step 4.3: Per-Session Queueing & Service Coordination
In `ToggleTalkService.java`, intercept prompts to decide if they should run or queue:
- If `mRunningSessions.contains(sessionId)`: Add prompt to `mSessionQueues.get(sessionId)` and broadcast `ACTION_QUEUE_CHANGED`.
- If inactive: Add to `mRunningSessions` and start `runAntigravityReasoning`.

When a run finishes:
- Check if the queue has prompts.
  - If the session is active (normal running): Auto-dequeue and run the next prompt.
  - If the session is stopped (terminated or finished but we are in unresolved queue state): Do not auto-run. We wait for user actions via the 4 vertical buttons.

### Step 4.4: Advanced TTS Audio Playback Control
- Wrap queue items in a `TtsItem` class storing `sessionId` and `text`.
- If the microphone is pressed during TTS playback:
  - Pause the AudioTrack and set `mIsTtsPaused = true`.
  - Save the current playing text chunk (sentence). Do not clear `mTtsQueue`.
  - Once the microphone is released, prepend the saved chunk back to `mTtsQueue` and call `startTtsPlaybackIfNeeded()` to repeat it.
- Stop & Flush Button: Add a small X button below the mic button when state is `SPEAKING` to clear `mTtsQueue` and stop playback immediately.

### Step 4.5: Code Modularity & File Subdivision
To avoid bloating `MainActivity.java`, implement features in these new dedicated helper classes:
1. **`PromptQueueView.java`**: Custom class managing the queue list UI, dynamic height layout calculations (1 prompt = 3 lines, 2 prompts = 2+1 lines, 3 prompts = 1+1+1 lines, 4+ prompts = scrollable 3-line high view), count badge, and individual line click listeners.
2. **`PromptEditPopup.java`**: Manages the bottom-aligned popup overlay, focus, cursor placement, keyboard trigger, and input validation.
3. **`GlowAnimationHelper.java`**: Manages the `ValueAnimator` for animating the stroke of the queue container's background drawable to create the red glowing pulsating effect.
4. **`UnresolvedQueueManager.java`**: Handles the visibility toggles between the Send button and the 4 vertical buttons, and coordinates clicks on `Resume`, `Delete`, `Combine`, and `Add`.

---

## 5. Automated & Manual ADB Verification Plan
- Use `adb shell am startservice` to test queue additions, termination, and state changes.
- Use `adb shell input tap` and `adb shell input swipe` to simulate mic press interrupts and popup clicks.
- Use `adb shell screencap` to verify the presence of the pulsating border and button layouts.

---

## 6. Guidelines for the Implementing Agent
- **No Placeholders**: Layouts and classes must be implemented with production-grade details.
- **Git Stage and Commit**: Be sure to stage and commit all code changes incrementally.
- **Testing**: Test the app fully using `adb` after implementing each step.
