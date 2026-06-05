# Markdown Rendering & Layout Verification Report

This report outlines the identified issues with markdown rendering and layout behavior in the ToggleTalk Android app, the root causes, and the implemented fixes.

---

## 1. Identified Issues & Root Causes

### A. The `_INLINE_CODE_X_` and `_CODE_BLOCK_X_` Placeholder Bug
* **Symptom**: Instead of formatted inline code blocks (e.g. `MainActivity.java`) or fenced code blocks, the chat log displayed literal strings like `_INLINE_CODE_0_` or `_CODE_BLOCK_1_`.
* **Root Cause**: 
  1. The app's `renderMarkdown` function uses a multi-step parser where it first extracts fenced code blocks and inline code blocks, replacing them with temporary placeholders (`___CODE_BLOCK_X___` and `___INLINE_CODE_X___`) to protect their contents from subsequent markdown formatting rules.
  2. In a later step, the parser matches bold formatting using the regex `__(.+?)__`. 
  3. Because the placeholders start and end with three underscores (`___`), the bold regex matched the outer two underscores at the beginning and end of each placeholder, turning them into `<b>_INLINE_CODE_X_</b>` or `<b>_CODE_BLOCK_X_</b>`.
  4. Consequently, when the final step tried to substitute the placeholders back by searching for `___INLINE_CODE_X___`, the target string no longer existed in the HTML, leaving the broken `_INLINE_CODE_X_` visible on the screen.

### B. Escape Entity Issue (`&#39;`)
* **Symptom**: Apostrophes in the text were rendered as `&#39;`.
* **Root Cause**: The helper method `escapeHtml()` explicitly replaced single quotes (`'`) with `&#39;`. Since Android's `Html.fromHtml()` parser does not decode decimal numeric character entities like `&#39;` in all configurations, the literal entity code remained visible in the TextView.

### C. Collapsible Block Mutual Collapse Bug
* **Symptom**: Collapsible thoughts and tool call result bubbles did not toggle correctly in-place when using the Expand/Collapse All buttons, sometimes causing active agent text to disappear or scroll improperly.

---

## 2. Implemented Solutions

### A. Non-Colliding Placeholders
* Changed the placeholder delimiters from `___` to `%%` (e.g., `%%INLINE_CODE_X%%`, `%%CODE_BLOCK_X%%`, and `%%TOKEN_PLACEHOLDER_X%%`). Since percent signs do not conflict with markdown symbols like `*` or `_`, the placeholders remain perfectly intact throughout all markdown regex passes and resolve successfully.

### B. HTML Escaping Correction
* Removed the `'` to `&#39;` replacement from the `escapeHtml` helper. Android's text views natively and safely render literal apostrophes without HTML escaping.

### C. In-Place Collapsible Block Toggling
* The click handlers and the "Expand/Collapse All" button now track active state using `CollapsibleBlockHolder` wrappers and toggle the visibility of the children container directly in-place, preventing the views from being completely reconstructed and ensuring smooth transitions.

---

## 3. Verification & Testing

1. **Build & Deploy**: The project compiles successfully and was deployed to the active device `192.168.0.66:33471`.
2. **Cache Clearance**: Deleted the app-side session history caches (`rm -rf cache/session_history_cache/*`) to force re-pulling and re-rendering of all historical logs.
3. **Rendering Checks**:
   * Verified that backtick-enclosed inline code symbols (like `MainActivity.java` or `load_session_history.py`) display as clean monospace items with a subtle lavender-gray text color.
   * Verified that markdown files and links are correctly hyperlinked and formatted without leakage of internal placeholder variables.
   * Verified that collapsible blocks correctly hide details by default and can be toggled by tapping them.
