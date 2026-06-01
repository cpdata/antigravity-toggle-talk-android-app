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
- **Termux API App**: Installed, configured, and accessible (enabling commands like `termux-tts-speak`).
- **Termux Storage**: Configured (`termux-setup-storage` has been run) allowing access to shared device storage at `~/storage/` (Access the SDCard at `/sdcard/` but don't write scripts that write temporary files to thr SD card, we want to avoid causing repeated filesystem writes to the SD card).
- **TTS**: You have access to a custom command `tts <message>` that you can use to speak to the user. (notify the user if you cannot access this command directly, do not review the code for the tts command unless instructed to do so.)
Always format your response in standard Markdown to display inside the app. For voice output, use the custom TTS command `tts 'say something here'` to speak a brief summary of your response.
