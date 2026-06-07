#!/bin/bash

# Verify ADB connectivity before building
if [[ -z $(adb devices | grep -v "List of" | grep "device") ]]; then
    echo "Error: No ADB devices connected. Please connect a device or start an emulator."
    exit 1
fi

# Build the app
gradle assembleDebug -q

# Install the app
gradle installDebug -q

# Alternative install with adb directly
#adb install -r build/outputs/apk/debug/ToggleTalkAndroid-debug.apk
