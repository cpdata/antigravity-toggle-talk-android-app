# Task Part 4: Prompt Edit Popup Overlay

This task details the design and implementation of the bottom-aligned popup editor overlay for editing prompts in the queue, including validation and keyboard interaction.

---

## 1. Rubric of Criteria

| Specification ID | Success Criteria (Pass) | Failure Criteria (Fail) |
|---|---|---|
| **R-VAL-1** | Empty/whitespace inputs are rejected from being saved/updated. | Empty inputs are saved. |
| **R-POP-1** | Edit pop-up appears at the bottom, hugging the edge, with narrow margins and padding. | Centered, floats, or wide margins. |
| **R-POP-2** | Clicking prompt opens pop-up, focuses editor, pops up keyboard, places cursor on a new line after text (`\n` appended). | No focus, keyboard hidden, cursor at start, or no newline. |
| **R-POP-3** | Return/Enter key inserts a newline. | Closes keyboard or updates prompt on Enter. |
| **R-POP-4** | Contains `Update`, `Cancel`, `DELETE`. Hides `Send` for individual prompts during active sessions. | Missing buttons or showing `Send` button incorrectly. |
| **R-POP-5** | Clicking Cancel or outside the pop-up dismisses editor without modifying the prompt. | Popup persists or queue is altered. |
| **R-DEL-1** | Clicking DELETE or trashcan displays "Are you sure?" confirmation dialog. If Yes, prompt is removed. | Prompt is deleted immediately without confirmation. |

---

## 2. Research & Context
- Pop-ups are handled via overlay layouts at the root level of `activity_main.xml`.
- Individual row clicks inside `PromptQueueView` must trigger this popup editor.

---

## 3. Implementation Steps

### A. Layout Overlay Setup (`activity_main.xml`)
Add the following layout overlay at the root level of the layout file:
```xml
<FrameLayout
    android:id="@+id/prompt_edit_popup_root"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:visibility="gone"
    android:clickable="true"
    android:focusable="true">
    <View
        android:id="@+id/prompt_edit_popup_dim_background"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:background="#66000000" />
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom"
        android:orientation="vertical"
        android:background="#F00B0518"
        android:padding="8dp"
        android:layout_margin="8dp"
        android:elevation="16dp">
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Edit Queued Prompt"
            android:textColor="#E6E6FA"
            android:textSize="12sp"
            android:textStyle="bold"
            android:layout_marginBottom="4dp" />
        <EditText
            android:id="@+id/et_edit_prompt"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:minHeight="48dp"
            android:maxLines="6"
            android:textColor="#FFFFFF"
            android:textSize="13sp"
            android:background="@drawable/card_glass"
            android:padding="8dp" />
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="end"
            android:layout_marginTop="6dp">
            <Button android:id="@+id/btn_edit_delete" android:text="Delete" android:textColor="#FF3B30" style="?android:attr/buttonBarButtonStyle" />
            <Button android:id="@+id/btn_edit_cancel" android:text="Cancel" android:textColor="#8A8A8F" style="?android:attr/buttonBarButtonStyle" />
            <Button android:id="@+id/btn_edit_update" android:text="Update" android:textColor="#00F2FE" style="?android:attr/buttonBarButtonStyle" />
            <Button android:id="@+id/btn_edit_send" android:text="Send" android:textColor="#4CD964" style="?android:attr/buttonBarButtonStyle" android:visibility="gone" />
        </LinearLayout>
    </LinearLayout>
</FrameLayout>
```

### B. Modular helper class: `PromptEditPopup.java`
- Manages visibility of the overlay.
- Handles outside-click dismiss via `prompt_edit_popup_dim_background` click listener.
- Prepopulates text, appends `\n`, focuses, moves cursor to the end, and triggers soft keyboard.
- Disables Editor Action IME_ACTION_SEND to ensure Return key inserts a newline.
- Implements empty/whitespace validation:
  `if (text.trim().isEmpty()) return;`
- Displays `AlertDialog` confirmation box for deletes: "Are you sure you want to delete this prompt?".

---

## 4. Verification Plan
- Click a prompt. Verify popup shows at the bottom, focuses, shows keyboard, cursor is on a new line.
- Edit prompt to be spaces-only and click Update; verify it is rejected.
- Click Delete, verify confirmation alert is shown. Verify prompt remains intact if Cancel is selected.
