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

## 2. Context & Background Research

### Files & Paths
- `activity_main.xml`: `/data/data/com.termux/files/home/ToggleTalkAndroid/src/main/res/layout/activity_main.xml`
- `MainActivity.java`: `/data/data/com.termux/files/home/ToggleTalkAndroid/src/main/java/com/toggletalk/android/MainActivity.java`
- `UnresolvedQueueManager.java` [NEW]: `/data/data/com.termux/files/home/ToggleTalkAndroid/src/main/java/com/toggletalk/android/UnresolvedQueueManager.java`
- `ToggleTalkService.java`: `/data/data/com.termux/files/home/ToggleTalkAndroid/src/main/java/com/toggletalk/android/ToggleTalkService.java`

### Unresolved Queue State Logic
If a session finishes executing (or is terminated) but still contains unsent prompts in its queue, the session enters the **Unresolved Queue State**. To prevent accidental execution or UI corruption, the user must resolve the queue before resuming normal chat input.
In this state:
1. The queue box border glows and pulsates red.
2. The main Send button (`mBtnSend`) is hidden.
3. In its place, a stacked layout containing the label `"Prompt Queue?"` and four vertical buttons (`Resume`, `Delete`, `Combine`, `Add`) is rendered.

---

## 3. Implementation Steps

### A. Layout Overlay & Buttons (`activity_main.xml`)
In the input layout (next to `et_message`), add the vertical button panel:
```xml
<LinearLayout
    android:id="@+id/layout_unresolved_actions"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:gravity="center"
    android:visibility="gone"
    android:layout_marginStart="5dp">
    
    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Prompt Queue?"
        android:textColor="#FF3B30"
        android:textSize="9sp"
        android:textStyle="bold"
        android:layout_marginBottom="2dp" />
        
    <Button
        android:id="@+id/btn_queue_resume"
        android:layout_width="75dp"
        android:layout_height="28dp"
        android:text="Resume"
        android:textSize="10sp"
        android:padding="0dp"
        android:background="@drawable/card_glass"
        android:textColor="#4CD964" />
        
    <Button
        android:id="@+id/btn_queue_delete"
        android:layout_width="75dp"
        android:layout_height="28dp"
        android:text="Delete"
        android:textSize="10sp"
        android:padding="0dp"
        android:background="@drawable/card_glass"
        android:textColor="#FF3B30"
        android:layout_marginTop="2dp" />
        
    <Button
        android:id="@+id/btn_queue_combine"
        android:layout_width="75dp"
        android:layout_height="28dp"
        android:text="Combine"
        android:textSize="10sp"
        android:padding="0dp"
        android:background="@drawable/card_glass"
        android:textColor="#00F2FE"
        android:layout_marginTop="2dp" />
        
    <Button
        android:id="@+id/btn_queue_add"
        android:layout_width="75dp"
        android:layout_height="28dp"
        android:text="Add"
        android:textSize="10sp"
        android:padding="0dp"
        android:background="@drawable/card_glass"
        android:textColor="#E6E6FA"
        android:layout_marginTop="2dp" />
</LinearLayout>
```

### B. Implement `UnresolvedQueueManager.java`
Create this class to orchestrate the unresolved queue state actions:
- Bind layout references (`layout_unresolved_actions`, `btn_queue_resume`, `btn_queue_delete`, `btn_queue_combine`, `btn_queue_add`, and `mBtnSend` in `MainActivity`).
- Track state: when a state broadcast is received indicating session has stopped and queue is not empty, toggle visible panels (`mBtnSend` visibility to `GONE`, `layout_unresolved_actions` visibility to `VISIBLE`).
- Implement Button Click Actions:
  - **Resume**:
    - Sends a request to `ToggleTalkService` to start execution of the next queued prompt.
    - Set the service to continue processing subsequent prompts sequentially as turns finish.
  - **Delete**:
    - Triggers an `AlertDialog` confirming: "Are you sure you want to clear all prompts in the queue?".
    - If confirmed: sends request to the service to clear the queue, hides `layout_unresolved_actions`, and restores `mBtnSend`.
  - **Combine**:
    - Aggregates all prompts from the queue and any text inside the main `mEtMessage` field, joining them with double newlines (`\n\n`).
    - Opens the `PromptEditPopup` with this combined text, passing a flag `showSendOnly = true`.
    - If user clicks `Send` in the popup: sends the combined prompt immediately to Antigravity, clears `mEtMessage`, clears the queue, and restores the normal UI.
    - If user clicks `Cancel` or dismisses, the text in `mEtMessage` remains untouched.
  - **Add**:
    - Takes text from `mEtMessage` (if not empty/spaces-only), appends it as a new prompt to the queue, clears `mEtMessage`, and updates the queue UI. The unresolved state and 4 buttons remain active.
- Clicking individual prompts in the queue box in this state must trigger `PromptEditPopup` with `showSendOnly = false`. This displays `Update`, `Delete`, and `Cancel` (the `Send` button is hidden).
- Once the queue size reaches 0 (either via manual deletions or resolution), remove the red border, hide `layout_unresolved_actions`, and unhide `mBtnSend`.

---

## 4. Verification & Testing Plan
- Place prompts in the queue and terminate the active session to trigger the unresolved queue state.
- Verify Send button is hidden and the 4 actions are displayed.
- Click Delete: verify confirmation dialog displays.
- Test Combine: check double-newline format, and verify Send clears both inputs and queue.
- Test Add: check that main input text is cleanly appended to the queue list and the text field is cleared.
