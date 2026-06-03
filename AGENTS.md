# Environment Details

This file outlines the environment details and integration configurations for the AI Agent (Antigravity) operating on this device.

## Device & Operating System
- **Device Model**: Samsung Galaxy S20
- **Operating System**: Android (running within a Termux environment)

## Connection & Debugging
- **Android Debug Bridge (ADB)**: Installed and configured.
- **Wireless Debugging**: Prompt the user to connect wireless debug when you need to use `adb` if you do not see any devices listed when running `adb devices`..
- **ADB Keep-Alive**: Use the `~/adb-keepalive.sh` script to keep the connection active.
  - To start it in the background for 5 hours (default): `~/adb-keepalive.sh` (logs to `~/adb-keepalive.log`, PID written to `~/.keepalive.pid`).
  - To stop it: `~/adb-keepalive.sh --stop` or `touch ~/.keepalive.stop`.
  - Flags supported: `--hours <hours>` (duration in hours), `--interval <seconds>` (ping interval), `--trigger <filepath>` (custom stop trigger file), and `--foreground` (run in foreground, useful for running as an agent background task).

## Integrations & Tools
- **Native Android Speech (Sherpa-ONNX)**: The app runs Whisper (STT) and Kokoro (TTS) natively, utilizing on-device GPU/NPU acceleration. Model assets are loaded from `/sdcard/ToggleTalkModels/`.
- **Termux Bridge**: The app coordinates the interaction loop and calls Termux asynchronously via the `com.termux.RUN_COMMAND` intent only for invoking the Antigravity agent reasoning CLI (`run_antigravity.sh`).
- **Termux Storage**: Configured (`termux-setup-storage` has been run) allowing access to shared device storage at `~/storage/` (Access the SDCard at `/sdcard/` but don't write scripts that write temporary files to the SD card, we want to avoid causing repeated filesystem writes to the SD card).

## Git
- Always git stage and commit your file changes at the end of every turn  Always use the current 'main' branch unless instructed otherwise.

## Agent Task CLI - AI Agent Guide

Use the `task-cli` command to manage tasks for this project.

Pipelines:
1. Solve: ready -> claim -> [work] -> update --status completed --result '...'
2. Create: group (optional) -> create --group <id> --depends <ids>
3. Monitor: stats -> list --status claimed -> heartbeat <id>
Core Commands (task-cli <cmd>):
- list/search: [id title status priority assign tags] (filter: -s, -p, -a, -g, -l <tags>)
- ready: tasks unblocked & for you
- claim [id]: lock task for work
- read <id> [-v]: details, metadata, deps
- update <id>: -s <status>, -r <result>, -t <title>, -d <desc>, -l <tags>, -a <assign>
- update dep <id>: --add <id>, --remove <id>
- group: create/list groups (-t, -d, -l <tags>, -p, -a)
- tags: list all unique tags
- stats: task/group metrics


Context:
User: main-agent
Repo: /data/data/com.termux/files/home/ToggleTalkAndroid

Run "task-cli --help" for full command reference.
