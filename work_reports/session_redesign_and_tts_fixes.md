# Implementation Plan: Session Redesign, Prompt Queueing, and TTS Playback Fixes

This document outlines the detailed design and step-by-step tasks required to resolve issues with conversation forks, disappearing final messages, speaking status bubble interference, and multi-session state synchronization.

## 1. Problem Analysis & Root Causes

### A. Conversation Forks
- **Root Cause**: When the user triggers voice input (microphone button) or types a prompt while the agent is in `THINKING` or `SPEAKING` state, `ToggleTalkService` receives the request and starts a parallel `runAntigravityReasoning` thread. This launches another instance of `agy` targeting the same session log, which corrupts the transcript file and forks the conversation tree.
- **Goal**: Queue any incoming requests (text or transcribing voice) when a session is active, showing them in a bottom container, and dispatching them one-by-one only when the active session stops.

### B. Collapsed / Disappearing Final Messages
- **Root Cause**: In [transcript_parser.py](file:///data/data/com.termux/files/home/ToggleTalkAndroid/transcript_parser.py), the `is_final` flag is calculated by checking if the *immediate next* step in the log file is `USER_INPUT`. If any step (like background task logs or tool execution status updates) occurs after a final agent response before a user replies, `is_final` is incorrectly evaluated as `false`. This causes the parser to reclassify the previous final response as a `thought`, which is collapsed by default and disappears from the chat bubble view.
- **Goal**: Group steps into turns bounded by `USER_INPUT` steps. The last `PLANNER_RESPONSE` with status `"DONE"` or containing `<tts>` tags in a turn block will be marked as final.

### C. Interference from TTS Playback ("Speaking..." Chat Bubble)
- **Root Cause**: When the TTS engine plays speech audio, it transitions `ToggleTalkService` state to `SPEAKING` with the text `"Speaking..."`. This triggers `onStateChanged` in [MainActivity.java](file:///data/data/com.termux/files/home/ToggleTalkAndroid/src/main/java/com/toggletalk/android/MainActivity.java), which adds a new chat bubble displaying `"Speaking..."`. When playback finishes, state changes to `IDLE` and converts that bubble to `"No response received."`.
- **Goal**: Chat bubbles must only be added/updated by transcript stream updates. We must remove the bubble creation logic inside `onStateChanged` for `THINKING` and `SPEAKING` states, and completely remove the `"..."` placeholder bubble logic.

### D. Multi-Session State and Global TTS Queue
- **Root Cause**: The service tracks state (`mCurrentState`) globally. If Session A is active in the background and Session B is loaded and idle in the foreground, the header displays the state of Session A. Also, background session updates overwrite the active chat container views.
- **Goal**: Track states per-session in the service, filter streaming displays in the activity to only update the screen if they match the selected session, and route TTS audio chunks to a global FIFO queue containing session metadata so that state changes only affect the session that produced the speech.

---

## 2. Proposed Architectural Changes

### A. Per-Session State Tracking
1. **Service Maps**: Implement `Map<String, String> mSessionStates` and `Map<String, String> mSessionTexts` in [ToggleTalkService.java](file:///data/data/com.termux/files/home/ToggleTalkAndroid/src/main/java/com/toggletalk/android/ToggleTalkService.java).
2. **State Broadcasts**: Include `session_id` in `ACTION_STATE_CHANGED` broadcasts.
3. **Activity Maps**: Maintain `mSessionStates` in [MainActivity.java](file:///data/data/com.termux/files/home/ToggleTalkAndroid/src/main/java/com/toggletalk/android/MainActivity.java). Update the header status only when a state change matches the selected session, and refresh header states immediately upon loading a new session.

### B. Prompt Queueing Map
1. **Session Queue**: Maintain `Map<String, List<String>> mSessionQueues` in `ToggleTalkService.java`.
2. **Active Runner Tracking**: Maintain a `Set<String> mRunningSessions` representing sessions currently executing an Antigravity reasoning run.
3. **Queue Box UI**: Add a glassmorphic container (`layout_queue_container`) below `scroll_log` in [activity_main.xml](file:///data/data/com.termux/files/home/ToggleTalkAndroid/src/main/res/layout/activity_main.xml) displaying the queue. Update it via a new broadcast `ACTION_QUEUE_CHANGED`.

### C. Unified Queue for TTS
1. **TtsItem Helper**: Define a helper class:
   ```java
   class TtsItem {
       String sessionId;
       String text;
   }
   ```
2. **Metadata Queue**: Change `mTtsQueue` in `ToggleTalkService.java` to `Queue<TtsItem>`. When playing back, extract the item and call `updateState(item.sessionId, "SPEAKING", "Speaking...")`.

---

## 3. Detailed Component Tasks

### Task 1: Update Parser Logic ([transcript_parser.py](file:///data/data/com.termux/files/home/ToggleTalkAndroid/transcript_parser.py))
- [ ] Group steps in `parse_transcript_steps` into logical blocks demarcated by `USER_INPUT` steps.
- [ ] For each block, identify the last `PLANNER_RESPONSE` with status `"DONE"` or containing `<tts>` tags.
- [ ] Mark that step as `is_final = True`, ensuring it parses as role `"agent"` even if subsequent non-user steps are appended.

### Task 2: Redesign Service State & TTS Queue ([ToggleTalkService.java](file:///data/data/com.termux/files/home/ToggleTalkAndroid/src/main/java/com/toggletalk/android/ToggleTalkService.java))
- [ ] Introduce `mSessionStates`, `mSessionTexts`, `mSessionQueues`, and `mRunningSessions`.
- [ ] Change `updateState(state, text)` to `updateState(sessionId, state, text)` to store and broadcast per-session states.
- [ ] Define `TtsItem` class. Modify `mTtsQueue` to hold `TtsItem` instances.
- [ ] In `startTtsPlaybackIfNeeded()`, update the state of the session associated with the active chunk to `SPEAKING`, and revert it to `IDLE` when the queue becomes empty or changes sessions.
- [ ] Implement `enqueuePrompt(sessionId, prompt)` which adds prompts to the queue if the session is running. Broadcast `ACTION_QUEUE_CHANGED`.
- [ ] When a run finishes and TTS completes (returning the session to `IDLE`), check if `mSessionQueues` has prompts for this session. Dequeue and run the next prompt, or remove the session from `mRunningSessions` if empty.
- [ ] Attach `session_id` to the callback intent inside `runAntigravityReasoning` with unique request codes using `sessionId.hashCode()`.

### Task 3: Redesign UI & Input Controller ([MainActivity.java](file:///data/data/com.termux/files/home/ToggleTalkAndroid/src/main/java/com/toggletalk/android/MainActivity.java))
- [ ] Add a visual queue container at the bottom of the conversation view in [activity_main.xml](file:///data/data/com.termux/files/home/ToggleTalkAndroid/src/main/res/layout/activity_main.xml).
- [ ] Keep a map `mSessionStates` inside the activity.
- [ ] Update `mStateReceiver` to read `session_id` and update state maps. If the ID matches the selected session, update header/mic/spinner visuals.
- [ ] In `performStreamedDisplay`, check if the broadcast's `session_id` matches the current `mSelectedSessionId`. If not, cache the response and return without updating the chat container.
- [ ] Remove `"..."` bubble generation and state updates:
  - Remove bubble creation inside `onStateChanged` for `THINKING` and `SPEAKING`.
  - Remove `"..."` check and fallback inside `onStateChanged` case `IDLE`.
  - Remove `"..."` creation in `displayMessagesInternal`.
- [ ] Add instant local UI feedback: When sending a message, append a temporary user bubble. This will be replaced cleanly when the stream broadcast arrives.

---

## 4. Verification & Testing Plan

### Automated Tests
- Since testing involves Termux intent integration, we can verify compilation and run manual scenario checks using `adb shell am broadcast`.

### Manual Scenarios
1. **Queue Verification**:
   - Start an agent run.
   - Type 2 prompts and click send.
   - Verify that the bottom panel shows: `Queued: "[First prompt text...]" (+1 more)`.
   - Verify that no conversation fork occurs.
   - Verify that once the agent run completes and speech playback ends, the next prompt starts automatically.
2. **Multi-Session Switching**:
   - Start an agent run in Session A.
   - Open the drawer, select Session B.
   - Verify that the header status of Session B shows `IDLE`, while Session A is still running in the background.
   - Verify that Session A's streamed displays do not corrupt Session B's conversation view.
   - Switch back to Session A and verify that its status is correctly shown (`THINKING` or `SPEAKING`) and all messages have rendered.
3. **TTS Queue order**:
   - Trigger prompts in Session A and Session B.
   - Verify that their spoken answers are queued globally and played in exact First-In-First-Out order, and they update only their corresponding session states to `SPEAKING` during playback.
