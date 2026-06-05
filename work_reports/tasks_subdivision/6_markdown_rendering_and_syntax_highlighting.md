# Task Part 6: Markdown Rendering & Syntax Highlighting

This task details the design and fixes for the Markdown renderer and code syntax highlighter in `MainActivity.java` to ensure correct rendering of Headings, Lists, and fenced Code Blocks.

---

## 1. Rubric of Criteria

| Specification ID | Success Criteria (Pass) | Failure Criteria (Fail) |
|---|---|---|
| **R-MDR-1** | Syntax highlighting is restricted only to code blocks wrapped in triple backticks (```) with auto-detection. Normal text handles markdown elements (e.g. `# headings`). | Raw markdown elements (like `#`) are ignored or syntax highlighting is applied globally to non-code blocks. |

---

## 2. Context & Background Research

### Components & File Path
- `MainActivity.java`: `/data/data/com.termux/files/home/ToggleTalkAndroid/src/main/java/com/toggletalk/android/MainActivity.java`

### The Bugs
1. **Headings Not Rendering**: Currently, headings (`# Title`, `## Subtitle`, `### Header`) are not matching the regex replacements `html.replaceAll("(?m)^#\\s+(.*)$", ...)` inside `renderMarkdown`. This is because the markdown newlines and spacing do not align with the strict line start (`^`) and end (`$`) checks or carriage returns (`\r\n`) interfere.
2. **Whole-Block Syntax Highlighting**: In `addCollapsibleBubble(role, text, index)`, if `role` is `"tool_result"`, the code bypasses `renderMarkdown` entirely and passes the *entire* raw text to `renderAndHighlightCodeBlock(content, lang)` or `renderPlainMonospace(content)` based on `looksLikeCode`. This treats the whole block as code, completely stripping out any rich text markdown formatting (like `# headings`, bold, bullet points, links) that might be inside the tool result.
3. **Fenced Code Blocks inside Markdown**: Fenced code blocks (` ```language ... ``` `) must auto-detect the language if it is not specified, run through the syntax highlighter, and be rendered alongside the normally formatted markdown text around it.

---

## 3. Implementation Steps

### A. Fix Heading and Markdown Parsing in `renderMarkdown(String markdown)`
1. Modify the regex patterns for headings inside `renderMarkdown` in `MainActivity.java` to be line-ending agnostic and handle carriage returns:
   - Replace:
     ```java
     String html = text.replaceAll("(?m)^###\\s+(.*)$", "<br/><font color=\"#00F2FE\"><b>$1</b></font><br/>");
     html = html.replaceAll("(?m)^##\\s+(.*)$", "<br/><font color=\"#00F2FE\"><b><big>$1</big></b></font><br/>");
     html = html.replaceAll("(?m)^#\\s+(.*)$", "<br/><font color=\"#00F2FE\"><b><big><big>$1</big></big></b></font><br/>");
     ```
   - With patterns that match carriage returns or line transitions cleanly:
     ```java
     String html = text.replaceAll("(?m)^###\\s+(.*?)\\r?$", "<br/><font color=\"#00F2FE\"><b>$1</b></font><br/>");
     html = html.replaceAll("(?m)^##\\s+(.*?)\\r?$", "<br/><font color=\"#00F2FE\"><b><big>$1</big></b></font><br/>");
     html = html.replaceAll("(?m)^#\\s+(.*?)\\r?$", "<br/><font color=\"#00F2FE\"><b><big><big>$1</big></big></b></font><br/>");
     ```
2. Ensure bullet lists (`- `, `* `) and numbered lists (`1. `) are also parsed cleanly by adjusting their regexes to handle ending `\r` carriage returns.

### B. Integrate Markdown Rendering into Collapsible Bubbles
1. In `MainActivity.java`'s `addCollapsibleBubble` method, locate the block that processes `"tool_result"`:
   ```java
   } else { // tool_result
       contentTv.setTypeface(android.graphics.Typeface.MONOSPACE);
       if (!content.isEmpty()) {
           String lang = detectLanguage(content, header);
           // ...
   ```
2. Modify this logic:
   - Instead of formatting the *entire* tool result as a syntax-highlighted block by default, pass the `content` through the standard `renderMarkdown(content)` method.
   - Inside `renderMarkdown`, fenced code blocks (` ``` `) will automatically be parsed, auto-detected, syntax-highlighted, and protected, while any other text (like headings or bullet points) inside the tool result will be formatted as markdown correctly.
   - Set the `contentTv` typeface to Monospace *only* inside code block sections, or use a custom TextView/Span styler to render markdown normally while keeping code blocks in monospace.

---

## 4. Verification & Testing Plan
- Trigger a command that returns tool results containing headings (e.g. run a command that outputs markdown headings like `# Output`).
- Verify that headings are rendered in the correct font size and cyan color.
- Put a code block inside a tool result or message:
  ```
  Check this:
  ```
  print("hello")
  ```
  Done.
  ```
  Verify that the surrounding text handles markdown formatting while the enclosed block is highlighted with auto-detected language.
