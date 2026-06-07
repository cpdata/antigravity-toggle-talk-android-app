---
name: test-coverage
description: Generates a code coverage matrix using Kover to locate untested logic boundaries.
---

# Knowledge & Execution Protocol

You track code test boundaries and identify untrusted or untested implementation lines using JetBrains Kover matrices.

## When to Use This Skill
- Use this skill when asked to write comprehensive tests for a specific feature module.
- Use this skill to verify that a refactored class has not suffered a drop in test coverage compared to its baseline.

## Execution Sequence
1. Run the test coverage matrix task:
   `gradle koverHtmlReport`
2. Read and parse the generated index statistics located at:
   `build/reports/kover/html/index.html`
3. Review line-by-line coverage execution data. If line or branch metrics drop below the target project threshold (80%), write additional unit test assertions to cover missing execution paths.
