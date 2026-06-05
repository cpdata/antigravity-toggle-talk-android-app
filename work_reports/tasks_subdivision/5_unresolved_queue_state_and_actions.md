# Task Part 5: Unresolved Queue State & Actions

This task details the implementation of the Unresolved Queue State on stopped sessions, including hiding the Send button and rendering/wiring the 4 vertical buttons (`Resume`, `Delete`, `Combine`, `Add`).

---

## 1. Rubric of Criteria

| Specification ID | Success Criteria (Pass) | Failure Criteria (Fail) |
|---|---|---|
| **R-UNS-1** | Stopped session with non-empty queue hides Send button and shows label `"Prompt Queue?"` + 4 vertical buttons. | Send button is visible, or the 4 vertical buttons are missing. |
| **R-UNS-2** | Clicking `Resume` starts executing the queue prompts sequentially. | Prompts do not auto-run or are deleted. |
| **R-UNS-3** | Clicking `Delete` triggers an "Are you sure?" dialog to clear the entire queue. | Queue cleared without confirmation. |
| **R-UNS-4** | Clicking `Add` appends main input text to queue and clears field, keeping unresolved state active. | Input not cleared or unresolved state clears. |
| **R-UNS-5** | Clicking `Combine` opens edit popup with all prompts and main input combined with double newlines (`\n\n`), showing only `Send`/`Cancel`. | Popup has other buttons or incorrect combined text. |
| **R-UNS-6** | Clicking `Send` in combine popup immediately sends combined prompt, clears queue and input field. | Queue or input not cleared. |
| **R-UNS-7** | Clicking individual prompts in unresolved state shows edit popup with `Send` button hidden. | `Send` button is visible. |
| **R-UNS-8** | Once queue is resolved/cleared, red border and 4 buttons go away, and original Send button is restored. | UI elements persist after resolution. |

---

## 2. Research & Context
- The Send button `mBtnSend` is located inside the input bar in `activity_main.xml`.
- When the session transitions to `IDLE` (or is terminated) but the prompt queue is not empty, the UI must toggle to the unresolved queue state.

---

## 3. Implementation Steps

### A. Add UI Buttons to `activity_main.xml`
Create a vertical `LinearLayout` containing the `"Prompt Queue?"` label and the 4 buttons (`btn_queue_resume`, `btn_queue_delete`, `btn_queue_combine`, `btn_queue_add`) inside the input bar (or next to the edit text box). Set its visibility to `GONE` by default.

### B. Modular Helper Class: `UnresolvedQueueManager.java`
- Controls visibility of `mBtnSend` vs the vertical button layout.
- Listens to queue updates and session states.
- Implements:
  - **Resume**: Sends a broadcast to start `runAntigravityReasoning` for the first prompt, keeping the rest queued to run sequentially as each turn finishes.
  - **Delete**: Shows a confirmation dialog: "Are you sure you want to delete all queued prompts?". If confirmed, sends intent to service to clear the queue for the session, and restores the Send button.
  - **Add**: Appends the text in `mEtMessage` to `mSessionQueues` for the active session, clears `mEtMessage`, and updates the queue list view.
  - **Combine**:
    - Aggregates all prompts in the session's queue and any text inside `mEtMessage`, joining them with double newlines `\n\n`.
    - Displays `PromptEditPopup` with the combined text.
    - Sets a special configuration flag on `PromptEditPopup` so it displays only `Send` and `Cancel` buttons.
    - Upon clicking `Send`: sends the combined text immediately to the service, clears `mEtMessage` and the session queue, and restores the normal UI.

---

## 4. Verification Plan
- Terminate a session that has queued prompts to enter the unresolved queue state.
- Verify that the Send button is hidden and the 4 buttons appear.
- Test `Add` (verifying text appends and clears input), `Delete` (verifying confirmation dialog), `Combine` (verifying double-newline join and Send clearing input/queue), and `Resume` (verifying prompts run in sequence).
