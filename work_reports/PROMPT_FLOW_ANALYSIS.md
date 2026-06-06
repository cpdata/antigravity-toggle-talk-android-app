# Prompt Lifecycle Documentation: ToggleTalkAndroid

## 1. Overview
This document outlines the lifecycle of a user prompt within the ToggleTalkAndroid application. It maps the flow from initial UI interaction to the execution of the agent, script processing, and the return of the response.

## 2. Prompt Flow Lifecycle
The flow follows a structured path between the Android UI/Service layer and the underlying Termux environment:

1.  **UI Entry:** User submits a prompt (e.g., via `PromptEditPopup`).
2.  **Manager Invocation:** The prompt is passed to `AgentManager`, which retrieves the current active agent.
3.  **Agent Execution:** The selected agent (`AntigravityAgent` or `GeminiAgent`) inherits from `BaseAgent` and initiates the task via `sendRunCommand`.
4.  **Termux Bridge:** An Android Intent (`com.termux.RUN_COMMAND`) is fired, instructing Termux to execute a specific bash script (`run_antigravity.sh` or `run_gemini.sh`).
5.  **Processing:** The script executes the CLI agent logic.
6.  **Callback:** The processing results are broadcast back to the `ToggleTalkService` (via `ACTION_ANTIGRAVITY_RESPONSE`), which updates the UI.

## 3. Component Breakdown

| Component | Responsibility |
| :--- | :--- |
| **UI Layer** | Captures input, triggers `AgentManager.run()`. |
| **AgentManager** | Stores active agent configuration and dispatches tasks. |
| **Agent Classes** | Abstract CLI execution (e.g., script paths, process termination). |
| **Termux Bridge** | Asynchronous execution of bash scripts via Intent. |
| **Processing** | Script-based CLI (Antigravity/Gemini) + Parsers. |
| **Service Layer** | Handles incoming Intents from Termux, broadcasts results. |

## 4. Agent Implementation Differences

While the agents share the common `BaseAgent` infrastructure for execution, they differ in the backend scripts and termination logic.

| Feature | AntigravityAgent | GeminiAgent |
| :--- | :--- | :--- |
| **Execution Script** | `run_antigravity.sh` | `run_gemini.sh` |
| **Termination Logic** | Kills `run_antigravity.sh` and `antigravity` processes. | Kills `run_gemini.sh`, `gemini`, and associated `node` processes. |

## 5. Summary
The architecture uses a unified Android Service interface to dispatch tasks to different CLI backend implementations in Termux. The primary divergence lies in the terminal command orchestration and process management, allowing for flexibility in swapping or updating agent CLI backends without modifying the Android application layer.
