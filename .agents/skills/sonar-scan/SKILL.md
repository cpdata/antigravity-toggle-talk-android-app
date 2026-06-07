---
name: sonar-scan
description: Runs static analysis to find complex code, bugs, vulnerabilities, and duplicated logic in Java/Kotlin Android projects. Trigger this whenever structural debt needs measuring.
---

# Knowledge & Execution Protocol

You are equipped with the SonarScanner CLI to pull deterministic metrics regarding complexity, dead code, and duplication within this Android repository.

## When to Use This Skill
- When requested to assess codebase health.
- CRITICAL: Always execute this tool when the user requests a refactor or requests changes to complex structural components.

## Execution Sequence
1. Always execute the local analysis command to generate a fresh telemetry baseline:
   `sonar-scanner -Dsonar.analysis.mode=issues -Dsonar.report.export.path=sonar-report.json`
2. Parse `sonar-report.json` or terminal logs specifically looking for:
   - Cognitive / Cyclomatic Complexity Hotspots (e.g., God classes, massive ViewModel/Activity classes).
   - Duplicated Blocks (Copy-paste fragments).
   - Code Smells / Dead Code (Unused imports, unreachable blocks).
3. Do not blindly write code. Propose structural refactoring directly using the empirical data provided by the Sonar report as your baseline justification.
