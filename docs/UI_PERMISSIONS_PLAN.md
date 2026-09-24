# UI permission loading — implementation plan

Status: proposed implementation, not shipped. Requested on 2026-09-06 during the shift review.

## Existing contract

`AuthService.buildAuthUserResponse` already loads active direct user permissions into
`AuthUserResponse.permissions`. Login returns that user, and `GET /api/auth/me` returns a fresh
copy. Admin-web already declares `AuthUser.permissions` and stores the login response through
`services/authService.ts`. No new permission endpoint or role-derived permission list is needed.

`SecurityService.hasPermission` checks direct grants; only `SYS_ADMIN` bypasses that check.
`OWNER` does not. Some endpoints deliberately use explicit role gates (for example HR): those
must be mapped individually, not converted to guessed permission rules. D36/D37/D38 and D127
remain authoritative. UI visibility helps navigation; the server still authorizes every request.

## Files and implementation order

1. `restaurant-saas-web/src/services/authService.ts`, `types/auth.ts`: validate the received user
   shape and centralize replacing the current user from login and `/me`. Do not merge old and new
   permission arrays. A missing or malformed permissions array grants no actions.
2. New `src/contexts/AuthContext.tsx` and `src/hooks/useAuth.ts`: hold one reactive session with
   explicit loading, authenticated, signed-out, and load-failed states. Use the login user
   immediately after successful authentication; on a restored session, verify `/me` before
   rendering protected content. Subscribe to the existing auth-session event and cross-tab
   storage changes. Ignore late responses from a previous user or tenant.
3. `src/main.tsx`, `src/guards/ProtectedRoute.tsx`, `src/pages/auth/LoginPage.tsx`: install the
   provider, route through its state, and display translated loading/retry or access-denied
   screens. Direct URLs must use the same gate as navigation. Never briefly render actions from
   an unverified stored user during startup.
4. New `src/utils/access.ts`, existing `src/utils/*Access.ts`: introduce one direct-grant helper
   with the backend's `SYS_ADMIN` exception. Start with `shiftAccess.ts`, including separate
   `SHIFTS_VIEW` and `SHIFTS_VIEW_VARIANCE`; remove the `OWNER` shortcut there. Inventory, sales,
   expenses, devices, tables and HR each follow a controller-to-UI gate inventory before migration.
   Preserve explicit backend role gates where they exist.
5. `src/layouts/ClientLayout.tsx`, route declarations, feature hubs and action buttons: consume
   the reactive user instead of reading storage independently. Map each link, route, create,
   edit, void, force-close and variance surface to its actual API authorization rule. Hide
   unavailable actions, and refuse direct navigation to unavailable pages.
6. `src/services/api.ts`, `src/services/authEvents.ts`: refresh `/me` on return to the app and
   after a permission-edit/reset operation affecting the current user. On 403, refresh once to
   update visibility without replaying the rejected mutation. A network failure must remain
   distinguishable from rejected authentication; do not recursively reload `/me` on its own
   failure. Terminal authentication clears reactive state and tenant caches.
7. `src/i18n/locales/{en,ar}/`: add matching permission-loading, retry and denied-access text.
8. Tests alongside the affected auth/access code: cover owner without a direct grant, cashier
   without variance permission, explicit sys-admin exception, direct URLs, no startup flash,
   permission removal after `/me`, failed bootstrap, sign-out, tenant switch and stale responses.
   Add a backend contract assertion that login and `/me` return active direct grants rather than
   role seed entries. Verify actual rendered navigation and actions in Arabic/RTL and English/LTR.

## Decisions still required before implementation

- Freshness guarantee: are startup, focus and permission-change refreshes sufficient, or should
  changes in another administrator's session disappear while this tab remains continuously in use?
  The latter needs a chosen polling interval or a separately scoped push mechanism.
- Scope of the first rollout: all admin-web modules together, or shifts first followed by the
  controller-to-UI inventory above. POS and sys-admin panel are separate apps, not implied here.

No new permission schema, new role model, or backend authorization relaxation is proposed.
