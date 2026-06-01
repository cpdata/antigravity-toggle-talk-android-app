# ToggleTalkAndroid: Native GPU-Accelerated Speech Integration Plan

This document outlines the detailed architecture and step-by-step plan to transition speech processing from Termux bash scripts to native Android integration. By running Whisper (Speech-to-Text) and Kokoro (Text-to-Speech) directly inside the Android application, we take advantage of the device's GPU/NPU, reducing latency and creating a seamless, robust mobile experience.

---

## 1. Current Architecture vs. Proposed Native Architecture

| Feature | Current Architecture (Termux-driven) | Proposed Native Architecture (Android-driven) |
| :--- | :--- | :--- |
| **Control Flow** | Termux script drives state changes and broadcasts updates to Android. | Android application drives the state machine and invokes Termux for agent reasoning only. |
| **Audio Capture** | Termux-API (`termux-microphone-record`) -> `.m4a` file. | Native `AudioRecord` API -> 16kHz mono 16-bit PCM float array in-memory. |
| **STT (Whisper)** | CPU-bound `whisper.cpp` CLI inside Termux. Requires file conversion using `ffmpeg`. | GPU-accelerated **Sherpa-ONNX** (Whisper) running natively on device. |
| **Reasoning (LLM)** | Antigravity CLI `agy.va39` binary (run via glibc proot bypass). | Antigravity CLI `agy.va39` binary (invoked via `com.termux.RUN_COMMAND` intent). |
| **TTS (Kokoro)** | Python `tts_engine.py` running inside Ubuntu proot-distro (extremely CPU intensive). | GPU/CPU optimized **Sherpa-ONNX** (Kokoro) running natively on device. |
| **Audio Playback** | Termux-API (`termux-media-player`) on synthesized WAV files. | Native Android `AudioTrack` API playing back raw PCM float buffers. |
| **Inter-Process Latency** | High (multiple disk writes, process spawns, shell pipelines, IPC broadcasts). | Low (in-memory audio processing, single-shot intent execution for reasoning). |

---

## 2. Technical Data Flow

The following sequence details how a single interactive Speech-to-Speech turn is executed:

1. **Idle -> Recording**:
   - The user taps the Microphone button in the UI, Quick Settings Tile, or Widget.
   - The Android app vibrates briefly, updates state to `RECORDING` (pulsing red rings), and initializes `AudioRecord` to capture 16kHz mono PCM audio directly into an in-memory buffer.
2. **Recording -> Thinking**:
   - The user taps the button again to signal completion.
   - The app stops `AudioRecord`, takes the raw PCM array, and feeds it to the native **Sherpa-ONNX OfflineRecognizer** (configured with the Whisper model).
   - The transcriber performs inference on the GPU/CPU and returns the transcribed text.
   - The app updates the state to `THINKING` (blue indicator) and displays the transcript.
3. **Agent Invocation (Termux)**:
   - The app packages the transcript and active directory path, and launches the `com.termux.RUN_COMMAND` intent to execute `~/ToggleTalkAndroid/run_antigravity.sh`.
   - The intent includes a `PendingIntent` callback to receive command results.
   - `run_antigravity.sh` executes the Antigravity agent CLI `agy.va39`.
   - The agent outputs a cumulative response. The script filters history using `tts_sanitize.py`, formatting the result as a clean JSON output:
     ```json
     {
       "latest_response": "Here is the summary of files...",
       "sanitized_tts": "Here is the summary of files"
     }
     ```
   - Standard output is returned to Android via the PendingIntent callback.
4. **Thinking -> Speaking**:
   - The app parses the JSON stdout, updates the UI text area, and transitions to `SPEAKING` (green breathing rings).
   - The app feeds the `sanitized_tts` string to the **Sherpa-ONNX OfflineTts** (configured with the Kokoro model and target voice).
   - Kokoro synthesizes raw PCM audio buffers in-memory.
   - The app feeds the PCM stream directly into a native `AudioTrack` for low-latency playback.
5. **Speaking -> Idle**:
   - Once playback completes, the app releases audio resources and resets state to `IDLE`.

---

## 3. Required Modifications

### A. Android Build Configuration
1. **Sherpa-ONNX Library Integration**:
   - Download the official `sherpa-onnx` Android archive (`sherpa-onnx-vX.Y.Z-android-*.tar.bz2`) from the [k2-fsa/sherpa-onnx Releases](https://github.com/k2-fsa/sherpa-onnx/releases) page.
   - Copy the `.aar` libraries (or import the native libraries and Kotlin bindings) into the `app/libs` directory.
   - Update `app/build.gradle` to include:
     ```groovy
     dependencies {
         implementation fileTree(dir: 'libs', include: ['*.aar', '*.jar'])
     }
     ```
2. **Permissions**:
   - Add the recording permission in `src/main/AndroidManifest.xml`:
     ```xml
     <uses-permission android:name="android.permission.RECORD_AUDIO" />
     ```

### B. Dynamic Model Loading Strategy
To keep the application small and build times fast in the Termux development environment, we will load Whisper and Kokoro model files dynamically from the device's shared storage.
1. Create a dedicated directory on the device: `/sdcard/ToggleTalkModels/`
2. Download and place the following files in this folder:
   - **Whisper (STT)**:
     - `whisper-tiny.en-encoder.int8.onnx` (Encoder model)
     - `whisper-tiny.en-decoder.int8.onnx` (Decoder model)
     - `whisper-tiny.en-tokens.txt` (Tokens file)
   - **Kokoro (TTS)**:
     - `kokoro-en-v0_19-int8.onnx` (Quantized Kokoro TTS model)
     - `voices.bin` (Style/voice embeddings)
     - `tokens.txt` (Kokoro character/phoneme tokens file)
     - `espeak-ng-data/` (Folder containing Espeak grapheme-to-phoneme lexicon rules)

### C. Android Code Changes
1. **`MainActivity.java`**:
   - Add runtime permission request handling for `Manifest.permission.RECORD_AUDIO` in `checkPermissionsAndPreferences()`.
2. **`ToggleTalkService.java`**:
   - Declare instance variables for the Sherpa-ONNX components:
     ```kotlin
     private var offlineRecognizer: OfflineRecognizer? = null
     private var offlineTts: OfflineTts? = null
     ```
   - Initialize them on startup, passing configuration pointing to the `/sdcard/ToggleTalkModels/` directory. Enable NNAPI/XNNPACK execution providers for GPU/NPU acceleration.
   - Implement `AudioRecord` capturing loop that collects 16kHz float PCM samples into an array during the `RECORDING` state.
   - Implement the STT execution:
     ```kotlin
     fun transcribeAudio(pcmData: FloatArray) {
         val stream = offlineRecognizer?.createStream()
         stream?.acceptWaveform(pcmData, 16000)
         offlineRecognizer?.decode(stream)
         val transcript = stream?.result?.text
         stream?.release()
         
         // Trigger reasoning step...
         runAntigravityReasoning(transcript)
     }
     ```
   - Implement `runAntigravityReasoning` to call Termux:
     - Target Path: `/data/data/com.termux/files/usr/bin/bash`
     - Arguments: `~/ToggleTalkAndroid/run_antigravity.sh`, `<transcript>`, `<directory_path>`, `--continue=<true/false>`
     - Supply a callback `PendingIntent` to receive the stdout.
   - Handle the command callback:
     - Parse the JSON response.
     - Extract `latest_response` and update the UI.
     - Feed `sanitized_tts` into `offlineTts?.generate(text)` to obtain the synthesized audio object.
     - Write the synthesized floats to an `AudioTrack` initialized with `AudioFormat.ENCODING_PCM_FLOAT` at `24000Hz` (Kokoro's native output sample rate).
     - When playback finishes, transition state back to `IDLE`.

### D. Termux Script Changes
1. **Create `run_antigravity.sh`** at `/data/data/com.termux/files/home/ToggleTalkAndroid/run_antigravity.sh`:
   ```bash
   #!/data/data/com.termux/files/usr/bin/bash
   # Accept transcript and configurations
   TRANSCRIPT="$1"
   TARGET_DIR="${2:-$HOME}"
   CONTINUE_SESSION="${3:-false}"
   
   LOG_FILE="$HOME/.toggle_talk_antigravity.log"
   ERR_FILE="$HOME/.toggle_talk_antigravity.err"
   
   # Setup environments (Glibc bypass path)
   export GODEBUG=netdns=go
   export SSL_CERT_FILE="/data/data/com.termux/files/usr/etc/tls/cert.pem"
   GLIBC_LINKER="/data/data/com.termux/files/usr/glibc/lib/ld-linux-aarch64.so.1"
   GLIBC_LIBS="/data/data/com.termux/files/home/.local/lib/agy-glibc:/data/data/com.termux/files/usr/glibc/lib"
   AGY_BIN="/data/data/com.termux/files/home/.local/bin/agy.va39"
   
   cd "$TARGET_DIR" 2>/dev/null
   
   PROMPT_SUFFIX="
   
   [Context: Your active working directory for this session is '$(basename "$TARGET_DIR")'. Format your response in standard Markdown to display inside the app. For voice output, use the custom 'tts' command to speak a brief summary of your response.]"
   PROMPT="${TRANSCRIPT}${PROMPT_SUFFIX}"
   
   # Run Antigravity
   if [ "$CONTINUE_SESSION" = "true" ]; then
       RESPONSE=$(env -u LD_PRELOAD -u LD_LIBRARY_PATH "$GLIBC_LINKER" --library-path "$GLIBC_LIBS" "$AGY_BIN" --dangerously-skip-permissions -c -p "$PROMPT" < /dev/null 2>>"$ERR_FILE")
   else
       RESPONSE=$(env -u LD_PRELOAD -u LD_LIBRARY_PATH "$GLIBC_LINKER" --library-path "$GLIBC_LIBS" "$AGY_BIN" --dangerously-skip-permissions -p "$PROMPT" < /dev/null 2>>"$ERR_FILE")
   fi
   
   # Extract latest and sanitized versions
   LATEST_RESPONSE=$(printf "%s" "$RESPONSE" | python3 "$HOME/toggle-talk-antigravity/tts_sanitize.py" --history-file "$LOG_FILE" --history-only)
   SANITIZED_TTS=$(printf "%s" "$RESPONSE" | python3 "$HOME/toggle-talk-antigravity/tts_sanitize.py" --history-file "$LOG_FILE")
   
   # Save history
   echo "$RESPONSE" > "$LOG_FILE"
   
   # Output JSON formatted string for the Android app
   jq -n \
      --arg lr "$LATEST_RESPONSE" \
      --arg st "$SANITIZED_TTS" \
      '{latest_response: $lr, sanitized_tts: $st}'
   ```
2. **Deprecate Legacy Scripts**:
   - Remove/deprecate `toggle_talk_antigravity.sh`, `stt`, and `tts` scripts in `/data/data/com.termux/files/home/ToggleTalkAndroid/`.

---

## 4. Execution & Integration Milestones

### Phase 1: Environment & Dependency Setup
- Obtain the native `sherpa-onnx` android `.aar` library.
- Place the library in `libs/` and verify Gradle sync.
- Create `/sdcard/ToggleTalkModels/` and transfer Whisper and Kokoro model artifacts.

### Phase 2: Native Audio Capture & Transcription (STT)
- Implement microphone record logic using `AudioRecord` at `16000Hz` mono.
- Configure `OfflineRecognizer` with Whisper models.
- Run transcription on recorded PCM floats and verify text outputs in Android Studio logcat.

### Phase 3: Termux Integration
- Write `run_antigravity.sh` in Termux.
- Modify the command call in `ToggleTalkService.java` to use the `run_antigravity.sh` target, registering the callback PendingIntent.
- Parse the resulting JSON string on callback completion.

### Phase 4: Native Speech Synthesis & Playback (TTS)
- Configure `OfflineTts` with Kokoro ONNX model, voices, and espeak-ng-data asset.
- Implement synthesis to generate raw PCM float buffers.
- Create `AudioTrack` playing at `24000Hz` and stream the audio samples.

### Phase 5: Verification & Cleanup
- Test the end-to-end loop: Tap to listen -> Tap to transcribe -> Reasoning in Termux -> Synthesize response -> Play response.
- Verify that device GPU is active during transcription and synthesis.
- Remove legacy script files from the workspace.
