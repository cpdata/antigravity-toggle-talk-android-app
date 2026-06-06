#!/bin/bash

# Verify ADB connectivity before building
if [[ -z $(adb devices | grep -v "List of" | grep "device") ]]; then
    echo "Error: No ADB devices connected. Please connect a device or start an emulator."
    exit 1
fi

# Build and install the app
gradle assembleDebug
adb install -r build/outputs/apk/debug/ToggleTalkAndroid-debug.apk
