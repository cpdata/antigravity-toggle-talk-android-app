---
name: graphify-query
description: Queries the persistent repository knowledge graph to find structural dependencies and map file connections safely.
---

# Knowledge & Execution Protocol

You are equipped with Graphify to traverse code relationships as a structured network.

## When to Use This Skill
- Call this tool before executing any refactoring action to prevent breaking hidden dependencies.
- Call this tool with the update flag (i.e. `graphify . --update`) anytime code structure shifts (i.e. after git commits and/or when done making codebase changes) to keep the index clean and up to date.

## Execution Sequence
1. If the knowledge graph does not exist in graphify-out/, build it now:
   `graphify .`
2. When planning any extractions for refactors, map the exact call bridges by querying the graph to see what classes rely on that logic:
   `graphify . --query "Find dependencies for ..."`
3. After completing an architectural slice, run a structural refresh to sync changes:
   `graphify . --update`
