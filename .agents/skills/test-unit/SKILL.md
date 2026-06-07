---
name: test-unit
description: Runs local JVM unit tests and handles regression validation loops.
---

# Knowledge & Execution Protocol

You are responsible for maintaining test suite integrity using local Gradle tasks.

## When to Use This Skill
- Invoke this skill prior to changing code to establish baseline behavior.
- Invoke this skill immediately after editing files under src/main/ to verify changes.

## Execution Sequence
1. Target changes efficiently by running specific module or class test suites whenever possible:
   `gradle testDebugUnitTest --continue`
2. If tests fail, process the generated XML reports under `build/test-results/` instead of relying entirely on standard terminal stdout logs.
3. Automatically enter a correction loop: modify the code to satisfy the failing assertion, re-run the test, and repeat until the command yields an exit code of 0.
