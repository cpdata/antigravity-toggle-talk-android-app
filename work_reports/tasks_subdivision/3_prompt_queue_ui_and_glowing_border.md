# Task Part 3: Prompt Queue UI & Glowing Pulsating Border

This task details the design of the prompt queue container, badge icon, dynamic row layout calculations, and the pulsating glowing border animation.

---

## 1. Rubric of Criteria

| Specification ID | Success Criteria (Pass) | Failure Criteria (Fail) |
|---|---|---|
| **R-QUE-1** | Count badge is shown in a small box on the left. | Count badge is missing or shows incorrect count. |
| **R-QUE-2** | Collapsed state shows truncated first line of the most recent prompt. | Shows older prompts or multiple lines. |
| **R-QUE-3** | 1 prompt expanded shows wrapped up to 3 lines. | Height does not wrap or wraps beyond 3 lines. |
| **R-QUE-4** | 2 prompts expanded shows 2 lines for the top prompt, 1 line for the second. | Height distribution is incorrect. |
| **R-QUE-5** | 3 prompts expanded shows exactly 1 line per prompt. | Prompts take more than 1 line. |
| **R-QUE-6** | 4+ prompts expanded shows scrollable list of single lines capped at 3 lines of height. | Scroll height is incorrect or grows indefinitely. |
| **R-QUE-7** | Red glowing border pulsates when prompts exist. | Border does not glow or pulsate. |

---

## 2. Context & Background Research

### Files & Paths
- `activity_main.xml`: `/data/data/com.termux/files/home/ToggleTalkAndroid/src/main/res/layout/activity_main.xml`
- `PromptQueueView.java` [NEW]: `/data/data/com.termux/files/home/ToggleTalkAndroid/src/main/java/com/toggletalk/android/PromptQueueView.java`
- `GlowAnimationHelper.java` [NEW]: `/data/data/com.termux/files/home/ToggleTalkAndroid/src/main/java/com/toggletalk/android/GlowAnimationHelper.java`

### UI Hierarchy
In `activity_main.xml`, the conversation log is wrapped inside `card_container` (LinearLayout) using `scroll_log` (ScrollView) and `layout_chat_container` (LinearLayout). We need to insert a custom glassmorphic box below `scroll_log` to serve as our prompt queue container. To ensure `MainActivity.java` remains clean and maintainable, we must modularize the UI rendering and animations into `PromptQueueView` and `GlowAnimationHelper`.

---

## 3. Implementation Steps

### A. Define Layout Views (`activity_main.xml`)
Add the following container layout directly below the `scroll_log` element:
```xml
<LinearLayout
    android:id="@+id/layout_queue_container"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="6dp"
    android:layout_marginTop="4dp"
    android:layout_marginBottom="4dp"
    android:visibility="gone"
    android:background="@drawable/card_glass">

    <!-- Collapsed Layout -->
    <LinearLayout
        android:id="@+id/layout_queue_collapsed"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical">
        <TextView
            android:id="@+id/tv_queue_badge"
            android:layout_width="20dp"
            android:layout_height="20dp"
            android:gravity="center"
            android:textColor="#FFFFFF"
            android:textSize="10sp"
            android:textStyle="bold"
            android:background="@drawable/bg_badge" />
        <TextView
            android:id="@+id/tv_queue_collapsed_text"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:layout_marginStart="8dp"
            android:textColor="#E6E6FA"
            android:textSize="11sp"
            android:ellipsize="end"
            android:singleLine="true" />
    </LinearLayout>

    <!-- Expanded Scrollable Layout -->
    <ScrollView
        android:id="@+id/scroll_queue_expanded"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:visibility="gone">
        <LinearLayout
            android:id="@+id/layout_queue_expanded_list"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical" />
    </ScrollView>
</LinearLayout>
```
*Note: Create `bg_badge.xml` in `res/drawable` as a circular red solid shape.*

### B. Implement `GlowAnimationHelper.java`
Create this class to handle the pulsating red border:
- It holds a reference to the queue container's background drawable (which must be a `GradientDrawable`).
- It uses a `ValueAnimator.ofArgb` to animate the stroke color between `#33FF3B30` (subtle red glow) and `#FFFF3B30` (bright red glow) over a duration of 1000ms.
- Repeat mode must be set to `ValueAnimator.REVERSE` and repeat count to `ValueAnimator.INFINITE`.
- Expose methods `startGlow()` and `stopGlow()`.

### C. Implement `PromptQueueView.java`
Create this class to encapsulate the queue list rendering:
- Bind UI references (`layout_queue_container`, `layout_queue_collapsed`, `scroll_queue_expanded`, `layout_queue_expanded_list`, `tv_queue_badge`, `tv_queue_collapsed_text`).
- Provide `render(List<String> queue, boolean isExpanded)`:
  - If collapsed:
    - Set collapsed visibility to `VISIBLE`, expanded to `GONE`.
    - Set the count badge text to `queue.size()`.
    - Set `tv_queue_collapsed_text` to the truncated single line of the most recently added prompt (`queue.get(queue.size() - 1)`).
  - If expanded:
    - Set collapsed visibility to `GONE`, expanded to `VISIBLE`.
    - Clear and dynamically rebuild `layout_queue_expanded_list` with prompt rows.
    - Set `ScrollView` height constraints: if queue size is 4 or more, set the height to exactly `120dp` (corresponding to 3 text lines height) and make it scrollable. Otherwise, set it to `WRAP_CONTENT`.
    - **Line limit calculations per row**:
      - If 1 prompt: set prompt text view `maxLines = 3`.
      - If 2 prompts: top prompt `maxLines = 2`, second prompt `maxLines = 1`.
      - If 3 prompts: each prompt `maxLines = 1` and `ellipsize = END`.
      - If 4+ prompts: each prompt `maxLines = 1` and `ellipsize = END`.
    - Append a red trashcan ImageButton (`android.R.drawable.ic_menu_delete` with a red tint) to the right of each row. Set its click listener to trigger the delete confirmation.
    - Set the click listener on the row text to trigger the edit popup.

---

## 4. Verification & Testing Plan
- Tap the queue box to toggle between expanded and collapsed states.
- Verify count badge updates as items are enqueued or deleted.
- Verify line heights match exact layout constraints for 1, 2, 3, and 4+ prompts.
- Ensure the red border glows and pulsates continuously while prompts are in the queue.
