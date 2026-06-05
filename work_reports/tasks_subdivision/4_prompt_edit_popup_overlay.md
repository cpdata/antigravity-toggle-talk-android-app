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

## 2. Context & Background Research

### Files & Paths
- `activity_main.xml`: `/data/data/com.termux/files/home/ToggleTalkAndroid/src/main/res/layout/activity_main.xml`
- `PromptEditPopup.java` [NEW]: `/data/data/com.termux/files/home/ToggleTalkAndroid/src/main/java/com/toggletalk/android/PromptEditPopup.java`

### Pop-up Behavior Specs
To keep the main screen clean, the prompt edit popup must hug the bottom of the screen with narrow margins (e.g. `8dp`) and be dismissable if the user taps outside.
When opened:
1. It pre-populates with the prompt text and appends `\n` to place the cursor on a new line.
2. It requests focus and forces the soft keyboard to open automatically.
3. It intercepts keyboard return keys to ensure they write newlines instead of submitting.
4. It must contain the actions: `Update`, `Cancel`, `Delete`. The `Send` button is only shown if the popup was triggered by the `Combine` button.

---

## 3. Implementation Steps

### A. Define Layout XML Overlay (`activity_main.xml`)
Append the layout code to the root container of the layout file:
```xml
<FrameLayout
    android:id="@+id/prompt_edit_popup_root"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:visibility="gone"
    android:clickable="true"
    android:focusable="true">
    
    <!-- Dim clickable background -->
    <View
        android:id="@+id/prompt_edit_popup_dim_background"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:background="#66000000" />
        
    <!-- Popup panel at the bottom -->
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
            android:scrollbars="vertical"
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

### B. Implement `PromptEditPopup.java`
Create this class to manage edit popup interactions:
- Bind layout references (`prompt_edit_popup_root`, `prompt_edit_popup_dim_background`, `et_edit_prompt`, `btn_edit_delete`, `btn_edit_cancel`, `btn_edit_update`, `btn_edit_send`).
- Implement `show(String promptText, boolean showSendOnly, OnEditActionListener listener)`:
  - Configure button visibility:
    - If `showSendOnly` is true: show `Send` and `Cancel`; hide `Delete` and `Update`.
    - If `showSendOnly` is false: show `Update`, `Cancel`, and `Delete`; hide `Send`.
  - Set text to `promptText + "\n"`.
  - Call `et_edit_prompt.requestFocus()`.
  - Position cursor at the end: `et_edit_prompt.setSelection(et_edit_prompt.getText().length())`.
  - Force show the soft keyboard using `InputMethodManager`.
  - Set key event listener or editor listener on `et_edit_prompt` to ensure Return/Enter key inserts a newline without triggering actions.
- Add outside click dismissal: set click listener on `prompt_edit_popup_dim_background` to dismiss the popup without applying modifications.
- Handle Validation: In the `Update` and `Send` click handlers, verify that `text.trim().isEmpty()` is false. If it is empty or only spaces, reject it (do not dismiss or trigger the listener).
- Handle Delete: Clicking `Delete` must show an `AlertDialog` confirming: "Are you sure you want to delete this prompt?". Only remove the prompt if the user clicks "Yes".

---

## 4. Verification & Testing Plan
- Trigger the popup and check alignment at the bottom of the screen.
- Verify focus and keyboard appearance.
- Type spaces and click Update; verify it is blocked.
- Press Enter key and verify it inserts a newline.
- Click outside the container and verify popup is dismissed.
