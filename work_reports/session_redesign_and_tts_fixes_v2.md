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
| **R-POP-2** | Pop-up Auto-focus & Cursor | Clicked prompt loads into edit box, auto-focuses, keyboard pops up, cursor is placed on a new line after the text. | Edit field does not focus, cursor is at start, or no new line is added. |
| **R-POP-3** | Return/Enter Key Behavior | Pressing Enter/Return key in the edit field inserts a newline, matching main input box behavior. | Pressing Enter closes keyboard or updates prompt. |
| **R-POP-4** | Pop-up Action Buttons | Buttons include `Update`, `Cancel`, `DELETE`. If session is inactive/stopped, a `Send` button is also visible. | Buttons are missing or `Send` button is visible when session is active. |
| **R-POP-5** | Outside Dismiss & Cancel | Clicking Cancel or outside the pop-up dismisses it, leaving the prompt queue unchanged. | Pop-up persists, or queue is modified. |
| **R-DEL-1** | Delete & Trashcan Actions | Clicking DELETE or the red trashcan icon displays an "Are you sure?" dialog. Selecting "Yes" removes the prompt. | Prompt is deleted without confirmation, or trashcan icon is missing. |
| **R-TRM-1** | Running Session Termination | Terminate button (X) is displayed next to the activity spinner. Clicking it immediately kills the background process. | Terminate button is missing, or background process continues running. |
| **R-TRM-2** | Terminate Preserves Queue | Terminating a session preserves all queued prompts. | Queue is cleared upon termination. |
| **R-TTS-1** | Send Button Unlocked | Send button is always available to add new messages to the queue, even during active reasoning or TTS playback. | Send button changes to a terminate button or is disabled. |
| **R-TTS-2** | TTS Stop & Flush | A separate X button below the mic button terminates current TTS playback and flushes the queue immediately. | Button is missing, or flushing fails to stop active audio track. |
| **R-TTS-3** | Mic Recording Pauses TTS | Pressing mic button during TTS pauses playback immediately, keeping the TTS queue intact. | Mic recording starts without pausing TTS, or queue is cleared. |
| **R-TTS-4** | Chunk Resumption & Repeat | Once recording is released, TTS playback resumes, repeating the exact sentence chunk that was playing when paused. | Playback skips the interrupted chunk or fails to resume. |

---

## 2. Research & Codebase Context

1. **Active Session UI**: Maintained in `MainActivity.java` where `mSelectedSessionId` holds the active session ID, and `mTvStatus` shows status text.
2. **Foreground Service**: `ToggleTalkService.java` manages recording, playback, and launching the background runner via `com.termux.RUN_COMMAND`.
3. **Background Runner**: `run_antigravity.sh` starts `stream_session.py` in the background and runs the `agy` compiler binaries.
4. **Log Streaming**: `stream_session.py` tails `transcript_full.jsonl` under `~/.gemini/antigravity-cli/brain/<session_id>/.system_generated/logs/` and calls `parse_transcript_steps` from `transcript_parser.py` to compile UI-ready chat history.

---

## 3. Meticulous Architectural Design

### A. Per-Session State Tracking (Service & Activity)
Instead of a single global state, `ToggleTalkService` must track states per session to support seamless background execution and switching:
```java
// ToggleTalkService.java
private final Map<String, String> mSessionStates = new HashMap<>(); // sessionId -> State (IDLE, THINKING, SPEAKING)
private final Map<String, String> mSessionTexts = new HashMap<>();   // sessionId -> State Text (e.g. "Speaking...")
```
Whenever `updateState(state, text)` is invoked in `ToggleTalkService.java`, it must map to a specific session ID:
```java
private void updateState(String sessionId, String state, String text) {
    if (sessionId == null || sessionId.isEmpty()) sessionId = "new_chat_session";
    mSessionStates.put(sessionId, state);
    mSessionTexts.put(sessionId, text);
    
    Intent intent = new Intent(ACTION_STATE_CHANGED);
    intent.putExtra(EXTRA_STATE, state);
    intent.putExtra(EXTRA_TEXT, text);
    intent.putExtra("session_id", sessionId);
    sendBroadcast(intent);
}
```
In `MainActivity.java`, update the state receiver to update a local map and change UI elements only if `session_id` equals the current `mSelectedSessionId`:
```java
// MainActivity.java
private final Map<String, String> mSessionStates = new HashMap<>();
private final Map<String, String> mSessionTexts = new HashMap<>();

private final BroadcastReceiver mStateReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
        String state = intent.getStringExtra(ToggleTalkService.EXTRA_STATE);
        String text = intent.getStringExtra(ToggleTalkService.EXTRA_TEXT);
        String sessionId = intent.getStringExtra("session_id");
        
        mSessionStates.put(sessionId, state);
        mSessionTexts.put(sessionId, text);
        
        if (sessionId != null && sessionId.equals(mSelectedSessionId)) {
            onStateChanged(state, text);
        }
    }
};
```

### B. Prompt Queueing Data Structures
Maintain session-specific prompt queues and tracking sets:
```java
// ToggleTalkService.java
private final Map<String, List<String>> mSessionQueues = new HashMap<>(); // sessionId -> List of queued prompts
private final Set<String> mRunningSessions = new HashSet<>();              // Set of session IDs with active agy runs
```

---

## 4. Step-by-Step Implementation Details

### Step 4.1: Correct Turn Parsing in `transcript_parser.py`
Modify `parse_transcript_steps` to accurately define turns.
```python
def parse_transcript_steps(raw_steps):
    messages = []
    num_steps = len(raw_steps)
    
    # 1. Identify indices of all USER_INPUT steps
    user_input_indices = [i for i, step in enumerate(raw_steps) if step.get("type") == "USER_INPUT"]
    
    # 2. Iterate through turns
    for turn_idx, start_idx in enumerate(user_input_indices):
        end_idx = user_input_indices[turn_idx + 1] if turn_idx + 1 < len(user_input_indices) else num_steps
        turn_steps = raw_steps[start_idx:end_idx]
        
        # Within this turn, find the last PLANNER_RESPONSE step index
        last_planner_idx = -1
        for local_idx, step in enumerate(turn_steps):
            if step.get("type") == "PLANNER_RESPONSE" and step.get("source") == "MODEL":
                last_planner_idx = start_idx + local_idx
                
        # 3. Parse steps within this turn
        for idx in range(start_idx, end_idx):
            obj = raw_steps[idx]
            msg_type = obj.get("type", "")
            source = obj.get("source", "")
            content = obj.get("content") or obj.get("thinking") or ""
            tool_calls = obj.get("tool_calls") or []
            
            if msg_type == "USER_INPUT" and content.strip():
                # Extract user prompt (cleaning tags)
                text = clean_user_tags(content)
                if text:
                    messages.append({"role": "user", "text": text})
                    
            elif msg_type == "PLANNER_RESPONSE" and source == "MODEL":
                # Check if this planner step is final for this turn block
                is_final = (idx == last_planner_idx)
                
                stripped_content = content.strip()
                if stripped_content:
                    if "<tts" in stripped_content:
                        # Extract TTS contents as role 'agent' and non-TTS as 'thought'
                        parse_tts_segments(stripped_content, messages)
                    else:
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
                    messages.append({"role": "tool_result", "text": f"{friendly_type}:\n{text}"})
                    
    return messages
```

### Step 4.2: Session Termination Logic
To ensure running processes are immediately killed without corrupting files, we must write PIDs to session directories and invoke system kills.

1. **Update `run_antigravity.sh`**:
   At the start of the execution:
   ```bash
   PID_FILE="$HOME/.gemini/antigravity-cli/brain/$SESSION_ID/.system_generated/logs/run.pid"
   mkdir -p "$(dirname "$PID_FILE")"
   echo "$$" > "$PID_FILE"
   trap 'rm -f "$PID_FILE"' EXIT
   ```
2. **Update `ToggleTalkService.java`**:
   Implement `terminateSession(String sessionId)`:
   ```java
   private void terminateSession(String sessionId) {
       if (sessionId == null || sessionId.isEmpty()) return;
       mRunningSessions.remove(sessionId);
       
       // Fire a background RUN_COMMAND intent to kill the script PID
       String pidFilePath = "/data/data/com.termux/files/home/.gemini/antigravity-cli/brain/" + sessionId + "/.system_generated/logs/run.pid";
       Intent killIntent = new Intent();
       killIntent.setClassName("com.termux", "com.termux.app.RunCommandService");
       killIntent.setAction("com.termux.RUN_COMMAND");
       killIntent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");
       killIntent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-c", "kill -9 $(cat " + pidFilePath + ")"});
       killIntent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
       startService(killIntent);
       
       updateState(sessionId, "IDLE", "Session terminated.");
   }
   ```

### Step 4.3: Per-Session Queueing & Service Coordination
In `ToggleTalkService.java`, intercepts prompts to decide if they should run or queue:
```java
// Handle com.toggletalk.android.ACTION_SEND_PROMPT
String prompt = intent.getStringExtra("prompt");
String sessionId = intent.getStringExtra("session_id");

if (prompt == null || prompt.trim().isEmpty()) {
    return; // Reject empty inputs
}

if (mRunningSessions.contains(sessionId)) {
    // Session is active -> Queue it
    List<String> queue = mSessionQueues.computeIfAbsent(sessionId, k -> new ArrayList<>());
    queue.add(prompt);
    broadcastQueueChanged(sessionId);
} else {
    // Session is inactive -> Start run
    mRunningSessions.add(sessionId);
    updateState(sessionId, "THINKING", prompt);
    new Thread(() -> runAntigravityReasoning(prompt, sessionId)).start();
}
```
When reasoning completes and TTS audio playback finishes, check the queue:
```java
// Triggered when session goes back to IDLE
List<String> queue = mSessionQueues.get(sessionId);
if (queue != null && !queue.isEmpty()) {
    String nextPrompt = queue.remove(0);
    broadcastQueueChanged(sessionId);
    
    mRunningSessions.add(sessionId);
    updateState(sessionId, "THINKING", nextPrompt);
    new Thread(() -> runAntigravityReasoning(nextPrompt, sessionId)).start();
} else {
    mRunningSessions.remove(sessionId);
    updateState(sessionId, "IDLE", "");
}
```

### Step 4.4: Advanced TTS Audio Playback Control

1. **Unified Queue & Metadata**:
   Define `TtsItem` to encapsulate session context:
   ```java
   class TtsItem {
       String sessionId;
       String text;
       TtsItem(String sessionId, String text) {
           this.sessionId = sessionId;
           this.text = text;
       }
   }
   private final Queue<TtsItem> mTtsQueue = new LinkedList<>();
   private TtsItem mCurrentlyPlayingItem = null;
   private String mCurrentPlayingChunk = null;
   private boolean mIsTtsPaused = false;
   ```
2. **マイクロホン割り込み (Mic Interrupt Pausing)**:
   - When the mic recording starts (state transitions to `RECORDING` via `handleToggle`):
     - Set `mIsTtsPaused = true`.
     - Stop the current `AudioTrack` playback and break the sample writing loop in the playback thread.
     - Save `mCurrentlyPlayingItem` and `mCurrentPlayingChunk`. Do **NOT** clear `mTtsQueue`.
   - When recording finishes:
     - Clear `mIsTtsPaused = false`.
     - Prepend `mCurrentPlayingChunk` to `mTtsQueue` (for playback) or cache it as the starting chunk.
     - Call `startTtsPlaybackIfNeeded()` to resume playing the interrupted chunk.
3. **TTS Stop & Flush Button**:
   - In `MainActivity.java`, display a small red X button below the mic button when the selected session is `SPEAKING`.
   - Clicking this button fires `ACTION_TERMINATE_TTS` with `session_id`.
   - Service action handler:
     ```java
     private void handleTerminateTts(String sessionId) {
         stopAudioPlayback();
         synchronized (mTtsQueue) {
             // Remove all items belonging to this session
             mTtsQueue.removeIf(item -> item.sessionId.equals(sessionId));
         }
         mCurrentPlayingChunk = null;
         mCurrentlyPlayingItem = null;
         mIsTtsPaused = false;
         updateState(sessionId, "IDLE", "");
     }
     ```

### Step 4.5: Prompt Queue Box UI Layout (`activity_main.xml`)
Inject the custom layouts in `card_container` below the `scroll_log`:
```xml
<!-- Glowing Pulsating Prompt Queue Container -->
<LinearLayout
    android:id="@+id/layout_queue_container"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="6dp"
    android:layout_marginTop="4dp"
    android:layout_marginBottom="4dp"
    android:visibility="gone"
    android:background="@drawable/card_glass">
    
    <!-- Collapsed View (Single Line) -->
    <LinearLayout
        android:id="@+id/layout_queue_collapsed"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical">
        
        <!-- Small circular badge for count -->
        <TextView
            android:id="@+id/tv_queue_badge"
            android:layout_width="20dp"
            android:layout_height="20dp"
            android:background="@drawable/bg_badge"
            android:gravity="center"
            android:textColor="#FFFFFF"
            android:textSize="10sp"
            android:textStyle="bold" />
            
        <TextView
            android:id="@+id/tv_queue_collapsed_text"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:layout_marginStart="8dp"
            android:textColor="#E6E6FA"
            android:textSize="11sp"
            android:ellipsize="end"
            android:singleLine="true" />
    </LinearLayout>

    <!-- Expanded Scrollable Container -->
    <ScrollView
        android:id="@+id/scroll_queue_expanded"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:visibility="gone">
        <LinearLayout
            android:id="@+id/layout_queue_expanded_list"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical" />
    </ScrollView>
</LinearLayout>
```

### Step 4.6: Dynamic Height & Wrapping in `MainActivity.java`
Implement the render queue method to configure spacing and max lines dynamically depending on item counts:
```java
private void renderQueueUI(List<String> queue) {
    if (queue == null || queue.isEmpty()) {
        mLayoutQueueContainer.setVisibility(View.GONE);
        stopPulsatingBorder();
        return;
    }
    mLayoutQueueContainer.setVisibility(View.VISIBLE);
    startPulsatingBorder();
    
    mTvQueueBadge.setText(String.valueOf(queue.size()));
    
    if (!mIsQueueExpanded) {
        mLayoutQueueCollapsed.setVisibility(View.VISIBLE);
        mScrollQueueExpanded.setVisibility(View.GONE);
        // Display truncated first line of the most recent prompt (last in list)
        String mostRecent = queue.get(queue.size() - 1);
        mTvQueueCollapsedText.setText(mostRecent.replace("\n", " "));
    } else {
        mLayoutQueueCollapsed.setVisibility(View.GONE);
        mScrollQueueExpanded.setVisibility(View.VISIBLE);
        mLayoutQueueExpandedList.removeAllViews();
        
        int count = queue.size();
        float density = getResources().getDisplayMetrics().density;
        
        // Dynamic Height Constraints
        if (count >= 4) {
            ViewGroup.LayoutParams lp = mScrollQueueExpanded.getLayoutParams();
            lp.height = (int) (120 * density); // Cap scroll height to exactly 3 lines of text equivalent
            mScrollQueueExpanded.setLayoutParams(lp);
        } else {
            ViewGroup.LayoutParams lp = mScrollQueueExpanded.getLayoutParams();
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            mScrollQueueExpanded.setLayoutParams(lp);
        }
        
        // Populating Prompts
        for (int i = 0; i < count; i++) {
            final int index = i;
            String promptText = queue.get(i);
            
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, (int)(4*density), 0, (int)(4*density));
            
            TextView tv = new TextView(this);
            tv.setText(promptText);
            tv.setTextColor(Color.parseColor("#E6E6FA"));
            tv.setTextSize(11);
            
            // Apply line limits based on prompt count
            if (count == 1) {
                tv.setMaxLines(3);
            } else if (count == 2) {
                if (i == 1) tv.setMaxLines(2); // Most recent prompt shows 2 lines
                else tv.setMaxLines(1);
            } else {
                tv.setMaxLines(1); // 3 or 4+ prompts shows 1 line each
                tv.setEllipsize(TextUtils.TruncateAt.END);
            }
            
            LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tv.setLayoutParams(tvLp);
            
            // Red Trashcan Icon on the Right
            ImageButton btnTrash = new ImageButton(this);
            btnTrash.setImageResource(android.R.drawable.ic_menu_delete);
            btnTrash.setImageTintList(ColorStateList.valueOf(Color.parseColor("#FF3B30")));
            btnTrash.setBackgroundColor(Color.TRANSPARENT);
            btnTrash.setPadding((int)(4*density), 0, (int)(4*density), 0);
            btnTrash.setOnClickListener(v -> showDeleteConfirmation(index));
            
            row.addView(tv);
            row.addView(btnTrash);
            
            // Make prompt line clickable
            row.setOnClickListener(v -> showEditPopup(index, promptText));
            mLayoutQueueExpandedList.addView(row);
        }
    }
}
```

### Step 4.7: Prompt Edit Bottom Sheet Popup (`MainActivity.java`)
Create a custom overlay layout for the prompt editor popup inside the main layout XML, and control its presentation:
```java
private void showEditPopup(int index, String initialText) {
    mPromptEditPopupRoot.setVisibility(View.VISIBLE);
    mEtEditPrompt.setText(initialText + "\n");
    mEtEditPrompt.requestFocus();
    
    // Position cursor at the end of the text on the new line
    mEtEditPrompt.setSelection(mEtEditPrompt.getText().length());
    
    // Automatically trigger keyboard
    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    if (imm != null) {
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
    }
    
    // Configure button visibility based on session active status
    boolean isSessionRunning = mRunningSessions.contains(mSelectedSessionId);
    mBtnEditSend.setVisibility(isSessionRunning ? View.GONE : View.VISIBLE);
    
    // Button listeners
    mBtnEditCancel.setOnClickListener(v -> dismissEditPopup());
    mBtnEditUpdate.setOnClickListener(v -> {
        String newText = mEtEditPrompt.getText().toString();
        if (newText.trim().isEmpty()) return; // Validation
        updateQueuedPrompt(index, newText.trim());
        dismissEditPopup();
    });
    
    mBtnEditDelete.setOnClickListener(v -> {
        dismissEditPopup();
        showDeleteConfirmation(index);
    });
    
    mBtnEditSend.setOnClickListener(v -> {
        String newText = mEtEditPrompt.getText().toString();
        if (newText.trim().isEmpty()) return; // Validation
        removeAndSendPromptImmediately(index, newText.trim());
        dismissEditPopup();
    });
}
```

---

## 5. Automated & Manual ADB Verification Plan

The plan requires full validation using `adb` to execute and verify UI configurations.

### A. General ADB Control & Keyboard Setup
Initialize adb connection and open app:
```bash
# Verify connection
adb devices

# Launch ToggleTalk application
adb shell am start -n com.toggletalk.android/.MainActivity
```

### B. Verification Scenarios & Commands

#### 1. Input Validation (R-VAL-1)
Verify that empty strings or whitespace-only inputs are ignored.
```bash
# Tap edit box, input spaces, and send
adb shell input tap 100 950 # Tap text input box (coordinate varies)
adb shell input text "    "
adb shell input keyevent 66 # Press Enter

# Verify logcat output shows no transmission
adb logcat -d | grep ToggleTalkService
```

#### 2. Queue Truncation & Expansion (R-QUE-2, R-QUE-6)
Trigger multiple background prompts to fill the queue and click to expand.
```bash
# Add 4 messages to the queue in rapid succession to verify truncation
for i in {1..4}; do
  adb shell am startservice -a com.toggletalk.android.ACTION_SEND_PROMPT --es prompt "Queued prompt number $i" --es session_id "session_1"
  sleep 0.1
done

# Verify queue layout container is visible
adb shell uiautomator dump /sdcard/window_dump.xml
adb shell grep "layout_queue_container" /sdcard/window_dump.xml

# Click the queue container to expand (use layout coordinates)
adb shell input tap 540 850
```

#### 3. Pulsating glowing border (R-QUE-7)
Verify red glow border state.
```bash
# Dump screen state during active queue state to check layout bounds
adb shell screencap -p /sdcard/glowing_border.png
adb pull /sdcard/glowing_border.png
```

#### 4. Running Session Termination (R-TRM-1)
Terminate background process and verify PID cleaning.
```bash
# Click the Terminate session button next to the progress bar
# (Find coordinates of btn_terminate_session or click by resource ID)
# Verify run.pid file is successfully cleaned up
adb shell ls /data/data/com.termux/files/home/.gemini/antigravity-cli/brain/session_1/.system_generated/logs/run.pid
```

#### 5. TTS Playback Pause/Resume & Repeat (R-TTS-3, R-TTS-4)
Verify mic button pauses TTS playback and repeats the active chunk upon release.
```bash
# Verify audio track active, then simulate mic button down (touch down)
adb shell input swipe 500 1000 500 1000 3000 # 3 second press to record

# Verify in logs that TTS was paused and current chunk cached
adb logcat -d | grep -E "TTS synthesis|Recording started"

# Release finger, verify log resumes playback and repeats cached chunk
adb logcat -d | grep "Resuming TTS synthesis"
```

---

## 6. Guidelines for the Implementing Agent

- **No Placeholders**: Do not include dummy coordinates or placeholders in layout layouts. Use exact values.
- **Git Stage and Commit**: Be sure to stage and commit all code changes incrementally. Commit each task separately to keep git history clean.
- **Testing**: Test the app fully using `adb` after implementing each step to confirm compliance with the rubric before starting the next one.
