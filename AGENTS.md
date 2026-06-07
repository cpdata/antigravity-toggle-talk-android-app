# Environment Details

This file outlines the environment details, integration configurations, and instructions for working on this project.

## Project Details
- **Project Name**: ToggleTalk Android - aka ToggleTalkAndroid
- **Description**: 
- See `./README.md` for full project details

## Device & Operating System
- **Device Model**: Samsung Galaxy S20
- **Operating System**: Android (running within a Termux environment)

## Connection & Debugging
- **Android Debug Bridge (ADB)**: Installed and configured.
- **Wireless Debugging**: Stop and notify the user to connect wireless debug when you need to use `adb` if you do not see any devices listed when running `adb devices`.
- **ADB Keep-Alive**: Use the `~/adb-keepalive.sh` script to keep the connection active.
  - To start it in the background for 5 hours (default): `~/adb-keepalive.sh` (logs to `~/adb-keepalive.log`, PID written to `~/.keepalive.pid`).
  - To stop it: `~/adb-keepalive.sh --stop` or `touch ~/.keepalive.stop`.
  - Flags supported: `--hours <hours>` (duration in hours), `--interval <seconds>` (ping interval), `--trigger <filepath>` (custom stop trigger file), and `--foreground` (run in foreground, useful for running as an agent background task).

## Integrations & Tools
- **Native Android Speech (Sherpa-ONNX)**: The app runs Whisper (STT) and Kokoro (TTS) natively, utilizing on-device GPU/NPU acceleration. Model assets are loaded from `/sdcard/ToggleTalkModels/`.
- **Termux Bridge**: The app coordinates the interaction loop and calls Termux asynchronously via the `com.termux.RUN_COMMAND` intent only for invoking the Antigravity agent reasoning CLI (`run_antigravity.sh`).
- **Termux Storage**: Configured (`termux-setup-storage` has been run) allowing access to shared device storage at `~/storage/` (Access the SDCard at `/sdcard/` but don't write scripts that write temporary files to the SD card, we want to avoid causing repeated filesystem writes to the SD card).

## Work Process & Build Tagging
- **Build Numbering**: A script `increment_build.sh` is used to maintain a build counter in `build_number.txt`.
- **Work Reports**: For each significant update, a report is created in `work_reports/WORK_REPORT_[X].md` where [X] is the build number.
- **Commit Tagging**: Every completed task that results in a build should be committed and tagged with the build number (numeric only).

## Text-to-Speech (TTS) & Notifications
- **Environment Detection**: Always check if the environment variable `AGENT_ENV_TYPE` exists and is set to 'ToggleTalkApp'.
- **Progress Notifications**: To keep the user informed during long-running tasks:
  - If `AGENT_ENV_TYPE` is 'ToggleTalkApp', emit notifications by printing `<tts>message</tts>` otherwise, use the custom shell command: `tts 'message'`.
- **Rules**:
  - ONLY the content inside `<tts>...</tts>` tags will be spoken.
  - Place all thoughts, intermediate reasoning, tool calls, and verbose explanations outside the `<tts>...</tts>` tags so they are only displayed visually, keeping the spoken response concise and natural.
  - Example:
    ```markdown
    Here is a summary of the files I updated:
    - [main.py](file:///path/to/main.py): Modified run configuration.

    <tts>I have completed the requested changes. Please verify and run the tests.</tts>
    ```

## ADB
- Use `adb` commands to directly control and test the app.

## Deploy
- Verify adb connection, build quietly, and install the app quieyly with `./deploy.sh`

## Git
- Always git stage and commit your file changes at the end of every turn  Always use the current 'main' branch unless instructed otherwise.

## Workspace Tool Interception Trigger: "refactor"

- Context Rule: The word refactor implies moving structural boundaries or cleaning architecture. AI Agents must not guess where complexity or duplicate blocks reside.
- Mandatory Action: Anytime a user includes the keyword refactor in their instruction payload, the agent MUST intercept the command and run the `/sonar-scan` skill workflow first.
- Constraint: Use the generated static metrics to construct the refactoring strategy before modifying any Android Activity, Fragment, ViewModel, or Repository layer.

## Test Verification & Regression Prevention Gates

- Context Rule: Code modifications are inherently prone to breaking existing features. The AI agent must approach changes with a verification mindset.
- Pre-Flight Requirement: Prior to executing any refactoring or feature engineering, the agent must run the local unit test task to ensure a stable green baseline.
- Post-Flight Gate: A task cannot be marked resolved until the agent runs `gradle testDebugUnitTest` and achieves an explicit exit code 0 status.
- Test Coverage Mandate: Refactored classes must maintain or increase their structural line coverage. Run the `/test-coverage` skill layout to guarantee code paths do not drop below a baseline threshold of 80%.
- Test Implementation Standard: When building new features, corresponding test cases must be written in `src/test/` using structured naming layouts such as `givenCondition_whenAction_thenExpectedResult()`.

## Graphify Architecture Mapping Protocols

- Context Rule: You are restricted from blindly guessing dependencies across large files. Graphify acts as the structural map.
- Dependency Mapping Gate: Before splitting or extracting any logic block from MainActivity or anywhere you are refactoring, you MUST execute `graphify` to locate structural clusters and check call edges.
- Multi-Tool Order of Operations:
  1. Use `/sonar-scan` to pinpoint high-complexity segments (Target Blocks).
  2. Use `/graphify-query` to map what components link to those target blocks (Dependency Safety).
  3. Perform the code extraction/refactor into isolated MVVM classes.
  4. Execute `/test-unit` to ensure exit code 0 stability.
  5. Use `/test-coverage` to verify testing coverage.
  6. Run Graphify `/graphify-query` update routines to re-index new file changes.
