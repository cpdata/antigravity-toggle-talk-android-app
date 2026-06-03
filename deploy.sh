
#!/bin/bash

# Build and inatall the app

gradle assembleDebug
adb install -r build/outputs/apk/debug/ToggleTalkAndroid-debug.apk
