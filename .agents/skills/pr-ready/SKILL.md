---
name: pr-ready
description: Final polish before handing code to review. Use before every handoff — "pr ready", "finalize", "prep for review".
---

Self-review your own diff before review sees it.

## Checklist
1. Run the **full `docs/REVIEW.md` checklist** against your diff; fix anything that would BLOCK
   (all hard invariants D1–D13 must hold).
2. **Diff hygiene**: no stray files, no debug logging, no commented-out code, no unrelated
   changes.
3. **ErrorCode ↔ FE translation parity**: every new `errorCode` has a matching FE translation
   entry; locale files updated → run `i18n-sync`.
4. Write a **conventional-commit** message stating intent (`type(scope): summary`) and output it.

Do **not** run `git commit` or any git command — just output the message.
