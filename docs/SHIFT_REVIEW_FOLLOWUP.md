# Shift review follow-up — 2026-09-06

Scope: resume the interrupted pass following review of backend `85d9b7a`, POS `99c6463`,
and uncommitted admin-web. The user approved specific changes and reserved the other findings
for a later decision. This pass does not certify the shift rewrite for release.

## Approved implementation

- Finding 2: order branch and warehouse follow the signed device's branch; the authenticated
  user identifies the payment actor. No extra device-validation query.
- Finding 3: remove order creation's caller-supplied user header and use the current principal.
- Finding 5: shift expense timestamps and late classification use branch time, converting
  existing tenant-local audit values without changing historical storage semantics.
- Findings 7–10: shared expiry recovery, observable session termination and stopped uploads,
  retryable network refresh failure, close-only exit, retained unpaid tickets, and durable
  retry of server logout after a successful close.
- Finding 11: show pending-order count and explicit destructive confirmation before force reset.
- Admin-web: four authentication error translations in English and Arabic, plus the expense
  shift picker in page and modal creation paths.
- Finding 14: prepare [UI_PERMISSIONS_PLAN.md](UI_PERMISSIONS_PLAN.md); permission architecture
  implementation remains a future pass.

## Findings retained for the user's next review

| Original finding | Remaining work |
|---|---|
| 1 — blind-count disclosure | Define and enforce the restricted API surface across header counts, orders, payment methods and expenses. Omitting named variance fields does not prevent reconstruction. |
| 4 — competing closes | Serialize competing closes or use an equivalent conditional write, preserving the first count/actor/variance. Sequential rejection tests do not establish this. |
| 6 — expense/close race | Coordinate expense creation with the freeze boundary; an expense can currently miss both the frozen sum and late classification. |
| 12 — shifts-list filter authorization | Supply cashier/device filter options to valid shift viewers without unrelated user/device-management grants. |
| 13 — reconciliation detail | Show authorized managers opening, closing and expected cash plus handover variance; preserve restricted-caller blindness. |
| 14 — frontend/backend permission mismatch | Implement the reviewed permission plan, including removal of unsupported owner shortcuts. Existing login responses already contain direct permissions. |

Other retained limits: historical archived shifts/order links lack a read API; V48 is unused
(O66); business-date continuity across separate shifts remains explicitly deferred under D120;
cashier performance/report completeness remains D125 follow-up. Timezone conversion alone does
not repair historical ambiguity from changed zone settings or repeated local DST times.

Verification must distinguish focused regressions, builds and rendered checks from concurrency
evidence. No blanket claim that every guard fails when reverted is made; the requested
guard-reversion mutation audit remains outstanding.
