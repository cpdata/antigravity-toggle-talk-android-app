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

## 2. Context & Background Research

### Components & File Path
- `ToggleTalkService.java`: `/data/data/com.termux/files/home/ToggleTalkAndroid/src/main/java/com/toggletalk/android/ToggleTalkService.java`

### Audio Generation & Threading
`ToggleTalkService` handles speech synthesis via Kokoro offline TTS. It manages a background thread (`mTtsThread`) that polls items from a queue (`mTtsQueue`), generates PCM samples (`mTts.generate()`), and streams them to an `AudioTrack`.
Currently, `mTtsQueue` stores strings directly without session ID mappings, and when TTS is playing, it transitions global states. We need to modularize this queue to support:
1. Identifying which session the playing speech belongs to.
2. Pausing/resuming playback when microphone input interrupts speech without flushing the remaining queue.
3. Repeating the interrupted chunk after resumption.
4. Separate X button to flush the queue.

---

## 3. Implementation Steps

### A. Modularize the TTS Queue with `TtsItem`
1. Define a nested helper class in `ToggleTalkService.java`:
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
2. Modify the queue and tracking variables:
   ```java
   private final Queue<TtsItem> mTtsQueue = new LinkedList<>();
   private TtsItem mCurrentlyPlayingItem = null;
   private String mCurrentPlayingChunk = null;
   private boolean mIsTtsPaused = false;
   ```
3. Update `queueTtsText(String sessionId, String text)` to insert `TtsItem` objects.

### B. Mic Interrupt Pausing
When the user taps the mic button during playback:
1. In `handleToggle()`, if the service state is `SPEAKING` (or the TTS engine is active):
   - Set `mIsTtsPaused = true`.
   - Set `mIsPlayingAudio = false` (to break out of the AudioTrack sample-writing loop in the playback thread).
   - Stop and release the current `AudioTrack` immediately.
   - Do **NOT** clear `mTtsQueue` or reset variables. Save `mCurrentlyPlayingItem` and `mCurrentPlayingChunk`.
2. In `stopNativeRecording()`, when the recording thread completes joining:
   - Clear the pause flag: `mIsTtsPaused = false`.
   - Prepend `mCurrentPlayingChunk` back to `mTtsQueue` so it plays first.
   - Invoke `startTtsPlaybackIfNeeded()` to resume playing the interrupted chunk.

### C. TTS Stop & Flush Intent
1. Register receiver for intent action `com.toggletalk.android.ACTION_TERMINATE_TTS` in the service.
2. When received:
   - Call `stopAudioPlayback()`.
   - Synchronously clear `mTtsQueue`.
   - Reset `mCurrentPlayingChunk` and `mCurrentlyPlayingItem`.
   - Clear the pause flag: `mIsTtsPaused = false`.
   - Revert the session state to `IDLE` and broadcast it.

---

## 4. Verification Plan
- Play TTS audio from a session, tap the mic button to speak, verify speech pauses instantly.
- Release the mic and verify that TTS resumes, repeating the exact sentence chunk that was playing when paused.
- Trigger TTS, send `com.toggletalk.android.ACTION_TERMINATE_TTS` intent via ADB, verify playback stops immediately and the queue is flushed.
