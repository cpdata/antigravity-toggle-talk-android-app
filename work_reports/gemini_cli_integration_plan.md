# WORK REPORT: Gemini CLI Integration Plan

## Overview
This document outlines the architectural plan to integrate **Gemini CLI** as an alternative agent backend for the ToggleTalk Android application. The goal is to provide users with a choice between the existing **Antigravity CLI** and **Gemini CLI**, while establishing a clean, extensible interface for future agent integrations (e.g., Codex, Claude Code).

## 1. Architectural Strategy: The Agent CLI Abstraction

Currently, the application is tightly coupled with Antigravity (e.g., `run_antigravity.sh`, hardcoded session paths). We will introduce an abstraction layer to decouple the UI and Service logic from specific CLI implementations.

### 1.1 Android Interface (`AgentCLI`)
A new Java interface `AgentCLI` will be created to encapsulate agent behavior:

```java
public interface AgentCLI {
    /**
     * Executes the agent with the given prompt.
     * @param sessionId The existing session ID, or null for a new session.
     * @param transcript The user's prompt text.
     * @param targetDir The directory context for the agent.
     * @param continueSession Whether to resume the previous session.
     */
    void run(String sessionId, String transcript, String targetDir, boolean continueSession);

    /**
     * Forces termination of the agent process.
     */
    void terminate(String sessionId);

    /**
     * Returns the human-readable name of the agent.
     */
    String getName();

    /**
     * Returns the internal ID of the agent (e.g., "antigravity", "gemini").
     */
    String getId();

    /**
     * Path to the Termux harness script.
     */
    String getScriptPath();
}
```

### 1.2 Agent Implementations
- **`AntigravityAgent`**: Wraps the existing `run_antigravity.sh` logic.
- **`GeminiAgent`**: Wraps the new `run_gemini.sh` logic.

### 1.3 `AgentManager`
A singleton class to manage the active agent based on user preferences.

## 2. Settings & UI Updates

### 2.1 Agent Selection
- Add a new radio button group in the Settings popup to select the "Active Agent".
- Persist the selection in `SharedPreferences` under the key `active_agent_cli`.
- Default value: `antigravity`.

### 2.2 UI Consistency
- The "Terminate" button logic will be updated to call `AgentManager.getActiveAgent().terminate(sessionId)`.
- The session list will be updated to reflect the sessions of the active agent.

## 3. Termux Side: Standardized Harness Scripts

Each agent will have a dedicated harness script (e.g., `run_antigravity.sh`, `run_gemini.sh`) that follows a strict contract.

### 3.1 Contract Requirements
1. **Arguments**:
   - `$1`: Transcript (Prompt)
   - `$2`: Target Directory
   - `$3`: Continue Session (true/false)
   - `$4`: Session ID (optional)
2. **PID Tracking**:
   - Must write the main process PID to a predictable location for termination.
   - Recommended: `$HOME/.gemini/agent-pids/[agent_id]_[session_id].pid`.
3. **JSON Output**:
   - Must return a single JSON object on `stdout` upon completion:
     ```json
     {
       "latest_response": "Markdown response text",
       "sanitized_tts": "Text for TTS (stripped of markdown)",
       "session_id": "The UUID/ID of the session"
     }
     ```
4. **Streaming**:
   - Must write real-time updates to a log file that can be tailed by `stream_session.py`.

### 3.2 `run_gemini.sh` Implementation
This script will invoke the `gemini` command:
- Use `-p "$TRANSCRIPT"` for non-interactive mode.
- Use `--session-id "$SESSION_ID"` to maintain context.
- Use `--yolo` to allow the agent to perform actions without manual confirmation.
- Use `--output-format stream-json` to support real-time UI updates.

## 4. Session Management & Listing

### 4.1 Unified Session Listing
The `list_sessions.py` script will be updated to be agent-aware:
- It will detect the active agent via an environment variable or argument.
- **Antigravity**: Reads from `~/.gemini/antigravity-cli/brain`.
- **Gemini CLI**: Executes `gemini --list-sessions --output-format json` and maps the results to the app's expected format:
  ```json
  [
    {"id": "uuid-1", "title": "Session Title"},
    {"id": "uuid-2", "title": "Another Session"}
  ]
  ```

### 4.2 History Mapping
- **Antigravity**: Loads history from `transcript.jsonl`.
- **Gemini CLI**: Can load history from its internal storage via a helper command or by reading `~/.gemini/history/`.

## 5. Streaming & Feedback Loop

The `stream_session.py` script currently expects Antigravity's `transcript_full.jsonl` format.
To support Gemini CLI:
1. `run_gemini.sh` will pipe `gemini --output-format stream-json` output into a conversion script.
2. The conversion script will translate Gemini's stream chunks into the `PLANNER_RESPONSE` / `USER_INPUT` JSONL format.
3. This allows the existing Android `ACTION_STREAM_UPDATE` mechanism to remain unchanged.

## 6. Implementation Steps

1. **Phase 1: Android Abstraction**
   - Create `AgentCLI` interface and `AgentManager`.
   - Refactor `ToggleTalkService.runAntigravityReasoning` into a generic `runAgent` method.
2. **Phase 2: Gemini Harness**
   - Create `run_gemini.sh` and test it manually in Termux.
   - Ensure it returns the correct JSON format.
3. **Phase 3: Settings UI**
   - Add the Agent Selection UI to the app.
4. **Phase 4: Unified Session Listing**
   - Update `list_sessions.py` to support Gemini CLI.
5. **Phase 5: Streaming Integration**
   - Implement the stream adapter for Gemini CLI.

## 7. Future Agents Checklist
To add a new agent (e.g., "Codex"):
1. Create `run_codex.sh` following the contract in Section 3.1.
2. Implement `CodexAgent` in Java.
3. Register the agent in `AgentManager`.
4. Update `list_sessions.py` to support the new agent's history format.

---
**Status**: Plan Ready for Implementation.
**Build Number**: 4
**Date**: Saturday, June 6, 2026
