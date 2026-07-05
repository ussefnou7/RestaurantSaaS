---
name: i18n-sync
description: Keep translations in sync. Use for any FE user-facing text or any new backend errorCode — "translation", "i18n", "locale", "new error", "add error code".
---

Single rule: no hardcoded user-facing strings, and `ar` ⇄ `en` always match.

## Rules
- All FE user-facing text goes through `useTranslation()` — never a literal string in JSX.
- Every new/changed key is added to **both** locales, with identical key sets:
  - Arabic: `restaurant-saas-web/src/i18n/locales/ar/<feature>.ts`
  - English: `restaurant-saas-web/src/i18n/locales/en/<feature>.ts`
- A new backend `errorCode` **must** get a matching FE translation entry so `translateApiError()`
  resolves it (`errorCode` + `params`).
- RTL-safe: no left/right-hardcoded layout that breaks under `dir="rtl"`.
- Enum-value translations (e.g. `DRAFT → مسودة`) belong here when tackled; remove dead keys
  (e.g. the unused `leaveAssign.errors.*`) when you touch that file.
