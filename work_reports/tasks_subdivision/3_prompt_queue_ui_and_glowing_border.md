# Task Part 3: Prompt Queue View & Glowing Pulsating Border UI

This task details the design of the queue container, badge icon, dynamic row layout calculations, and the pulsating glowing border animation.

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

## 2. Research & Context
- The chat scroll layout is defined as `@+id/scroll_log` inside a linear layout `@+id/card_container` in `activity_main.xml`.
- We need to insert our prompt queue container directly below `@+id/scroll_log` and style it as a glassmorphic card.

---

## 3. Implementation Steps

### A. Create Layout Views (`activity_main.xml`)
Add the following layout element below `scroll_log`:
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

### B. Modular Helper Classes
Create new dedicated helper files:
1. **`GlowAnimationHelper.java`**:
   - Manages a `ValueAnimator` that animates the alpha/color of the border of a `GradientDrawable` background.
   - Interpolates the stroke color between `#33FF3B30` and `#FFFF3B30` every 1000ms using reverse repetition.
2. **`PromptQueueView.java`**:
   - Holds reference to the UI layouts.
   - Provides a `renderQueue(List<String> queue, boolean isExpanded)` method.
   - Implements the dynamic line layout logic:
     - 1 prompt: `maxLines=3`.
     - 2 prompts: prompt 1 = `maxLines=2`, prompt 2 = `maxLines=1`.
     - 3 prompts: each prompt = `maxLines=1`.
     - 4+ prompts: each prompt = `maxLines=1`, and sets the `ScrollView` height to exactly `120dp`.
   - Adds a red trashcan ImageButton (`android.R.drawable.ic_menu_delete`) to the right of each row.

---

## 4. Verification Plan
- Launch app, enqueue various numbers of prompts (1, 2, 3, 4+) and verify heights and truncations match the rubric specifications in both collapsed and expanded states.
- Verify that when the queue is populated, the container shows a red glowing border that pulsates.
