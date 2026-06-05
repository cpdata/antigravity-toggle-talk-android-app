# Task Part 2: TTS Playback Control, Pause/Resume, & Flushing

This task details changes to `ToggleTalkService.java` to support advanced text-to-speech queueing with session metadata, pausing during microphone input, and queue flushing.

---

## 1. Rubric of Criteria

| Specification ID | Success Criteria (Pass) | Failure Criteria (Fail) |
|---|---|---|
| **R-TTS-1** | Send button is always available to add new messages to the queue during active reasoning or TTS. | Send button is disabled or changes to delete icon. |
| **R-TTS-2** | Tapping X button below mic immediately stops TTS playback and flushes queue. | Flushing fails to stop active audio track or clear queue. |
| **R-TTS-3** | Pressing mic button pauses TTS playback immediately, keeping queue intact. | Mic starts without pausing, or queue gets cleared. |
| **R-TTS-4** | Once mic is released, TTS playback resumes, repeating the exact sentence chunk that was playing when paused. | Playback skips chunk or fails to resume. |

---

## 2. Research & Context
- `ToggleTalkService` contains the TTS playback thread which polls `mTtsQueue` and writes floats to `AudioTrack`.
- The current queue holds strings directly, which makes it impossible to know which session generated the audio.

---

## 3. Implementation Steps

### A. TTS Queue Metadata
1. Define a helper class in `ToggleTalkService.java`:
   ```java
   class TtsItem {
       String sessionId;
       String text;
       TtsItem(String sessionId, String text) {
           this.sessionId = sessionId;
           this.text = text;
       }
   }
   ```
2. Modify the queue declaration:
   `private final Queue<TtsItem> mTtsQueue = new LinkedList<>();`
3. Modify `queueTtsText(String sessionId, String text)` to insert `TtsItem` objects.

### B. Mic Interrupt Pausing
1. In `ToggleTalkService.java`, add variables:
   ```java
   private TtsItem mCurrentlyPlayingItem = null;
   private String mCurrentPlayingChunk = null;
   private boolean mIsTtsPaused = false;
   ```
2. In `handleToggle()`, if the current state is `SPEAKING`:
   - Set `mIsTtsPaused = true`.
   - Set `mIsPlayingAudio = false` to break the playback write loop.
   - Stop and release the `AudioTrack` immediately.
   - Do **NOT** clear `mTtsQueue`.
3. In `stopNativeRecording()`, when the recording thread finishes joining:
   - Clear `mIsTtsPaused = false`.
   - Prepend `mCurrentPlayingChunk` to `mTtsQueue` so it plays first.
   - Invoke `startTtsPlaybackIfNeeded()` to resume playback.

### C. TTS Stop & Flush Intent
1. Add intent action `com.toggletalk.android.ACTION_TERMINATE_TTS` in `ToggleTalkService.java`.
2. On receive:
   - Call `stopAudioPlayback()`.
   - Clear `mTtsQueue` fully.
   - Revert the session state to `IDLE` and broadcast it.

---

## 4. Verification Plan
- Build and run. Verify that pressing the mic button during active speaking stops speech, starts recording, and resumes speaking the exact sentence chunk once released.
- Send the `ACTION_TERMINATE_TTS` broadcast via ADB and verify that TTS flushes.
