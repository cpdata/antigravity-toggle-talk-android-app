#!/data/data/com.termux/files/usr/bin/bash
# toggle_talk_antigravity.sh - Speech-to-Speech single turn with Antigravity and TTS

PID_FILE="$HOME/.toggle_talk_antigravity.pid"
LOG_FILE="$HOME/.toggle_talk_antigravity.log"
ERR_FILE="$HOME/.toggle_talk_antigravity.err"

# Ensure environment variables are set for headless widget execution
export PREFIX="/data/data/com.termux/files/usr"
export PATH="$PREFIX/bin:$HOME/.local/bin:$PATH"
export HOME="/data/data/com.termux/files/home"

# Source .bashrc to load user environment variables (like API keys)
[ -f "$HOME/.bashrc" ] && source "$HOME/.bashrc" 2>/dev/null

cleanup() {
    rm -f "$PID_FILE"
    rm -f "$ERR_FILE"
    termux-media-player stop >/dev/null 2>&1
    am broadcast -a com.toggletalk.android.STATE_UPDATE --es state IDLE --es text "" >/dev/null 2>&1 &
}

# Check if session is already running
if [ -f "$PID_FILE" ]; then
    BG_PID=$(cat "$PID_FILE" 2>/dev/null)
    if [ ! -z "$BG_PID" ] && kill -0 "$BG_PID" 2>/dev/null; then
        # Session is running! Check if currently recording
        STATUS=$(termux-microphone-record -i 2>/dev/null)
        if echo "$STATUS" | grep -q '"isRecording":\s*true'; then
            # Recording is active. Stop recording to submit prompt.
            termux-toast -g top -s "⏹️ Processing speech..."
            # Call stt to stop the recording
            stt >/dev/null 2>&1
        else
            # Not recording. User wants to stop/cancel the session.
            termux-toast -g top -s "🛑 Stopping session..."
            termux-media-player stop >/dev/null 2>&1
            kill "$BG_PID" 2>/dev/null
            cleanup
        fi
        exit 0
    fi
fi

# Clean up any leftover state
cleanup
echo "$$" > "$PID_FILE"
trap cleanup EXIT INT TERM

# Parse arguments
CONTINUE=false
TEST_MODE=false
TEST_PROMPT="Say Hello"
TARGET_DIR="$HOME"

while [ "$#" -gt 0 ]; do
    case "$1" in
        -c|--continue)
            CONTINUE=true
            shift
            ;;
        -d|--directory)
            if [ -n "$2" ] && [[ "$2" != -* ]]; then
                TARGET_DIR="$2"
                shift 2
            else
                termux-toast -g top "⚠️ Error: --directory requires a directory path"
                echo "Error: --directory requires a directory path" >&2
                exit 1
            fi
            ;;
        --directory=*)
            TARGET_DIR="${1#*=}"
            shift
            ;;
        -t|--test)
            TEST_MODE=true
            shift
            if [ -n "$1" ] && [[ "$1" != -* ]]; then
                TEST_PROMPT="$1"
                shift
            fi
            ;;
        *)
            shift
            ;;
    esac
done

if [ "$TEST_MODE" = "true" ]; then
    TRANSCRIPT="$TEST_PROMPT"
else
    # 1. Alert user we are listening (vibrate and toast)
    termux-vibrate -d 80 >/dev/null 2>&1
    termux-toast -g top "🎤 Speak now..."
    am broadcast -a com.toggletalk.android.STATE_UPDATE --es state RECORDING --es text "" >/dev/null 2>&1 &

    # 2. Block and listen
    TRANSCRIPT=$(stt)

    # 3. Check if transcript is empty
    if [ -z "$TRANSCRIPT" ]; then
        termux-toast -g top "Idle timeout or empty. Exiting."
        exit 0
    fi
fi

termux-toast -g top "Heard: $TRANSCRIPT"

# 4. Process with Antigravity
    termux-toast -g top "🧠 Antigravity is thinking..."
    am broadcast -a com.toggletalk.android.STATE_UPDATE --es state THINKING --es text "$TRANSCRIPT" >/dev/null 2>&1 &

# Direct glibc execution variables to bypass proot (which hangs in headless/widget context)
export GODEBUG=netdns=go
export SSL_CERT_FILE="/data/data/com.termux/files/usr/etc/tls/cert.pem"
GLIBC_LINKER="/data/data/com.termux/files/usr/glibc/lib/ld-linux-aarch64.so.1"
GLIBC_LIBS="/data/data/com.termux/files/home/.local/lib/agy-glibc:/data/data/com.termux/files/usr/glibc/lib"
AGY_BIN="/data/data/com.termux/files/home/.local/bin/agy.va39"

# Ensure the patched resolv.conf destination (/sdcard/resolv.c) is populated with nameservers
if [ ! -s "/sdcard/resolv.c" ]; then
    mkdir -p "/sdcard" 2>/dev/null
    echo -e "nameserver 8.8.8.8\nnameserver 8.8.4.4" > "/sdcard/resolv.c" 2>/dev/null
fi

# Clear error file, and clear log file only if not continuing previous conversation
> "$ERR_FILE"
if [ "$CONTINUE" = "false" ]; then
    > "$LOG_FILE"
fi

# Debug: Log environment and context to a separate file
DEBUG_LOG="$HOME/.toggle_talk_antigravity.debug.log"
{
  echo "=== DEBUG RUN: $(date) ==="
  echo "PID: $$"
  echo "PPID: $(ps -o ppid= -p $$ 2>/dev/null || echo unknown)"
  echo "TTY: $(tty 2>/dev/null || echo 'no tty')"
  echo "CWD: $(pwd)"
  echo "USER: $(id)"
  echo "--- Environment ---"
  env | sort
  echo "=================="
} > "$DEBUG_LOG"

# Resolve TARGET_DIR relative to HOME if it's relative
if [[ "$TARGET_DIR" != /* ]]; then
    TARGET_DIR="$HOME/$TARGET_DIR"
fi

if [ ! -d "$TARGET_DIR" ]; then
    termux-toast -g top "⚠️ Directory not found: $TARGET_DIR"
    echo "Directory not found: $TARGET_DIR" >&2
    exit 1
fi

# Always cd to the target directory prior to initializing antigravity
cd "$TARGET_DIR"

# Determine display name for the target directory (use basename for Home)
if [ "$TARGET_DIR" = "$HOME" ]; then
    DIR_LABEL="Home (~)"
else
    DIR_LABEL="$TARGET_DIR"
fi

# Run with or without -c depending on arguments
# We use env -u to unset LD_PRELOAD and LD_LIBRARY_PATH only for the glibc dynamic linker call,
# keeping them intact for the rest of the script and other Termux utilities.
PROMPT_SUFFIX="

[Context: Your active working directory for this session is '$DIR_LABEL'. When the user asks about the current directory, answer with this path. Format your response in standard Markdown to display inside the app. For voice output, use the custom 'tts' command to speak a brief summary of your response.]"
PROMPT_WITH_SUFFIX="${TRANSCRIPT}${PROMPT_SUFFIX}"

if [ "$CONTINUE" = "true" ]; then
    # Continue conversation
    RESPONSE=$(env -u LD_PRELOAD -u LD_LIBRARY_PATH "$GLIBC_LINKER" --library-path "$GLIBC_LIBS" "$AGY_BIN" --dangerously-skip-permissions -c -p "$PROMPT_WITH_SUFFIX" < /dev/null 2>>"$ERR_FILE")
    EXIT_CODE=$?
else
    # Start new conversation
    RESPONSE=$(env -u LD_PRELOAD -u LD_LIBRARY_PATH "$GLIBC_LINKER" --library-path "$GLIBC_LIBS" "$AGY_BIN" --dangerously-skip-permissions -p "$PROMPT_WITH_SUFFIX" < /dev/null 2>>"$ERR_FILE")
    EXIT_CODE=$?
fi

echo "Antigravity exit code: $EXIT_CODE" >> "$ERR_FILE"

# 5. Speak the response
if [ ! -z "$RESPONSE" ]; then
    # Extract only the latest response (unsanitized)
    LATEST_RESPONSE=$(printf "%s" "$RESPONSE" | python3 "$HOME/toggle-talk-antigravity/tts_sanitize.py" --history-file "$LOG_FILE" --history-only)
    am broadcast -a com.toggletalk.android.STATE_UPDATE --es state SPEAKING --es text "$LATEST_RESPONSE" >/dev/null 2>&1 &

    # Display the latest response unsanitized in a toast message, cutoff at 400 characters
    RESPONSE_LEN=${#LATEST_RESPONSE}
    if [ "$RESPONSE_LEN" -gt 400 ]; then
        CUTOFF_CHARS=$((RESPONSE_LEN - 400))
        TOAST_MSG="${LATEST_RESPONSE:0:400}...[${CUTOFF_CHARS} characters not displayed]"
    else
        TOAST_MSG="$LATEST_RESPONSE"
    fi
    termux-toast -g top -- "$TOAST_MSG"

    termux-toast -g top "Generating TTS"
    echo "$LATEST_RESPONSE"
    # Clean and sanitize the output for TTS (using previous log file)
    SANITIZED_RESPONSE=$(printf "%s" "$RESPONSE" | python3 "$HOME/toggle-talk-antigravity/tts_sanitize.py" --history-file "$LOG_FILE")
    
    # Save the raw output from antigravity to log file, overwriting it for the next run
    echo "$RESPONSE" > "$LOG_FILE"
    
    # Play the response using the custom tts command
    tts "$SANITIZED_RESPONSE"
else
    # Save the empty response to log file (overwriting it)
    echo "$RESPONSE" > "$LOG_FILE"
    
    termux-toast -g top "⚠️ Empty response from Antigravity"
    if [ -s "$ERR_FILE" ]; then
        echo "=== Antigravity Errors & Logs ===" >&2
        cat "$ERR_FILE" >&2
    fi
fi

cleanup
