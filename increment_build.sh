#!/bin/bash
BUILD_FILE="build_number.txt"
if [ ! -f "$BUILD_FILE" ]; then
    echo "0" > "$BUILD_FILE"
fi
BUILD_NUMBER=$(cat "$BUILD_FILE")
BUILD_NUMBER=$((BUILD_NUMBER + 1))
echo "$BUILD_NUMBER" > "$BUILD_FILE"
echo "$BUILD_NUMBER"
