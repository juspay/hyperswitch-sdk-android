#!/bin/bash

# Auto-Pull Performance Reports Script
# Continuously pulls performance reports from Android emulator to your laptop

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
EMULATOR_PATH="/sdcard/Download/HyperswitchPerformance"
DEST_DIR="./performance-reports"
CHECK_INTERVAL=2  # seconds

# Create destination directory if it doesn't exist
mkdir -p "$DEST_DIR"

echo -e "${BLUE}╔════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║    Hyperswitch Performance Reports Auto-Puller        ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "${GREEN}📁 Monitoring: ${EMULATOR_PATH}${NC}"
echo -e "${GREEN}💾 Saving to:  ${DEST_DIR}${NC}"
echo -e "${YELLOW}⏱️  Check interval: ${CHECK_INTERVAL}s${NC}"
echo ""
echo -e "${YELLOW}Press Ctrl+C to stop${NC}"
echo "────────────────────────────────────────────────────────"
echo ""

# Track previously seen files to avoid duplicate messages
declare -A seen_files

while true; do
    # Check if adb is available
    if ! command -v adb &> /dev/null; then
        echo -e "${YELLOW}⚠️  ADB not found. Please install Android SDK platform-tools.${NC}"
        sleep 10
        continue
    fi

    # Check if device is connected
    if ! adb devices | grep -q "device$"; then
        echo -e "${YELLOW}⚠️  No device connected. Waiting...${NC}"
        sleep 5
        continue
    fi

    # Pull files from emulator
    # Use 2>&1 to capture both stdout and stderr, then filter
    output=$(adb pull "$EMULATOR_PATH/" "$DEST_DIR/" 2>&1)

    # Check if pull was successful and files were transferred
    if echo "$output" | grep -q "pulled"; then
        # Extract file names from output
        files=$(echo "$output" | grep "pulled" | sed 's/.*: //' | sed 's/ pulled.*//')

        # Process each file
        while IFS= read -r file; do
            if [ ! -z "$file" ] && [ -z "${seen_files[$file]}" ]; then
                filename=$(basename "$file")
                echo -e "${GREEN}✅ Pulled: ${filename}${NC}"
                seen_files[$file]=1
            fi
        done <<< "$files"
    fi

    # Wait before next check
    sleep "$CHECK_INTERVAL"
done
