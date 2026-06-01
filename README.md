# ToggleTalk Android App (antigravity-toggle-talk-android-app)

ToggleTalk Android is a native GPU/NPU-accelerated speech-to-speech companion app for the **Antigravity AI Agent**. Instead of driving conversational loops via Termux terminal scripts, this native Android application manages the state machine, records audio in-memory, transcribes speech using Whisper, handles agent reasoning in Termux, synthesizes response speech using Kokoro, and plays it back—all with minimum latency.

---

## 🚀 Key Features

* **GPU-Accelerated Speech Processing**: Runs on-device AI models using the **Sherpa-ONNX** library, utilizing NNAPI/XNNPACK execution providers for GPU/NPU acceleration.
  * **Speech-to-Text (STT)**: Whisper Offline Recognizer (`whisper-tiny.en.int8.onnx`).
  * **Text-to-Speech (TTS)**: Kokoro TTS Offline Generator (`kokoro-en-v0_19-int8.onnx`).
* **Interactive Main Interface**:
  * **Breathing Visualizer Rings**: Animated state-specific concentric rings (Recording: Red, Thinking: Blue, Speaking: Green, Idle: Purple).
  * **Session Continuity Toggle**: Opt-in check for continuous agent conversation sessions.
  * **Log Console**: High-contrast, scrollable transcript and logger output.
* **Workspace / Directory Drawer**:
  * Slide out edge drawer showing available project directories. Allows users to switch the active context directory of the Antigravity agent CLI directly from the UI.
* **Quick Access Points**:
  * **Quick Settings Tile**: Start, pause, or reset voice sessions from any screen.
  * **Home Screen Widget**: A visually synchronized home-screen widget to start and stop speech interactions instantly.
* **Termux Bridge**: Asynchronously executes the reasoning script via `com.termux.RUN_COMMAND` intent.

---

## 📐 Architecture and Data Flow

```
+-----------------------------------------------------------+
|                     Android Application                   |
|                                                           |
|  +--------------------+             +------------------+  |
|  |     MainActivity   | <---------> |   ToggleTalk     |  |
|  |  (State Machine UI)|             |  ForegroundSvc   |  |
|  +--------------------+             +------------------+  |
|                                         |          ^      |
|     +-----------------------------------+          |      |
|     |                                              |      |
|     v                                              v      |
|  [AudioRecord]                                [AudioTrack] |
|   (16kHz PCM)                                 (24kHz PCM) |
|     |                                              ^      |
|     v                                              |      |
|  [Sherpa-ONNX STT]                            [Sherpa-ONNX TTS]|
|    (Whisper)                                   (Kokoro Engine)|
|     |                                              ^      |
|     v                                              |      |
+-----+----------------------------------------------+------+
      |                                              |
      | intent: com.termux.RUN_COMMAND               |
      | callback: PendingIntent                      |
      v                                              |
+-----------------------------------------------------------+
|                     Termux Environment                    |
|                                                           |
|             run_antigravity.sh <------------------+       |
|                     |                                     |
|                     v                                     |
|             Antigravity Agent CLI                         |
|             (agy.va39 binary & model)                     |
+-----------------------------------------------------------+
```

### Turn Lifecycle:
1. **Idle ➔ Recording**: Triggered via UI button, widget, or tile. `AudioRecord` captures mono 16kHz raw PCM samples.
2. **Recording ➔ Thinking**: Tapped again to stop recording. The raw PCM buffer is transcribed in-memory by Sherpa-ONNX (Whisper).
3. **Agent Invocation**: App sends the transcript to Termux via `com.termux.RUN_COMMAND` invoking `run_antigravity.sh`.
4. **Thinking ➔ Speaking**: Termux processes the transcript, returns a JSON object with the output. The app parses the response and feeds it to Sherpa-ONNX (Kokoro) to generate speech.
5. **Speaking ➔ Idle**: The generated audio plays via `AudioTrack` at 24kHz. The app resets to the `IDLE` state once completed.

---

## 🛠️ Prerequisites & Setup

### 1. Device Environment
* Samsung Galaxy S20 (or equivalent arm64 device) running Android.
* **Termux** environment installed with `termux-api` utilities configured.
* Enabled developer options and **Wireless Debugging** (ADB) for local deployment.

### 2. Dependency Libraries
* Download the official `sherpa-onnx` Android archive (`sherpa-onnx-vX.Y.Z-android-*.tar.bz2`) from the [k2-fsa/sherpa-onnx Releases](https://github.com/k2-fsa/sherpa-onnx/releases) page.
* Copy the `.aar` libraries and binary dependencies to `libs/` folder:
  ```bash
  mkdir -p libs/
  # Place sherpa-onnx aar archives here
  ```

### 3. Dynamic Model Assets
To maintain a small build size, models are dynamically loaded from local device storage:
* Create a dedicated storage folder on the device: `/sdcard/ToggleTalkModels/`
* Place the following model assets within that directory:
  * **Whisper (STT)**:
    * `whisper-tiny.en-encoder.int8.onnx`
    * `whisper-tiny.en-decoder.int8.onnx`
    * `whisper-tiny.en-tokens.txt`
  * **Kokoro (TTS)**:
    * `kokoro-en-v0_19-int8.onnx`
    * `voices.bin` (Style/voice embeddings)
    * `tokens.txt` (Characters & phoneme tokens file)
    * `espeak-ng-data/` (Folder containing Espeak grapheme-to-phoneme rules)

---

## 📦 Building & Development

The project is built using Gradle. Since compilation takes place on-device or locally via terminal tools:

```bash
# Build the Debug APK
./gradlew assembleDebug

# Install on connected device/emulator
./gradlew installDebug
```

---

## 📄 File Map

* `src/main/java/com/toggletalk/android/`
  * `MainActivity.java`: UI layout, breathing ring animations, edge drawer panel, and gesture handling.
  * `ToggleTalkService.java`: Foreground Service orchestration, in-memory audio recording, Sherpa-ONNX model inference, and Termux `PendingIntent` execution.
  * `ToggleTileService.java`: Quick Settings Tiles implementation linking to the toggle actions.
  * `ToggleWidgetProvider.java`: Home screen widget providers with dynamic state-based color tinting.
* `run_antigravity.sh`: The Termux bash script that executes the glibc bypass wrapper for the `agy.va39` binary, cleans the response using `tts_sanitize.py`, and returns standard JSON.
* `AGENTS.md`: Technical configurations detailing Termux paths, ADB configurations, and local environment specifications.
