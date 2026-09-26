# Expired-session recovery

The sample application's Spring Security configuration now returns HTTP 401 with an `HX-Redirect` header for unauthenticated HTMX requests. HTMX navigates the full window to the context-aware login URL instead of following a redirect and swapping login HTML into a table or form. The response has no body and disables caching.

Unauthenticated HTMX posts rejected before reaching a controller also go to login. Authenticated requests with invalid CSRF tokens remain forbidden; this does not disable CSRF checks or retry mutations. Ordinary browser requests retain standard login redirects. The login page explains that pending changes were not saved.

This is host-security integration in the sample, not an automatically installed starter security chain. Consuming applications should apply the same entry-point and access-denied-handler behavior to their own login flow. JSON fetch requests, unsaved-edit recovery and restoring the pre-expiry page after sign-in remain separate work. No form values are persisted or replayed.

## Verification

All 60 tests passed with `mvn test`; packaging also passed. Browser verification left a list open across a server restart, then applied its filters. The browser navigated to `/login?expired` as a full page, with the expiry message and no table/sidebar fragment. Invalid-CSRF rejection for authenticated users and context-prefixed login redirects are covered by integration tests. `git diff --check` passed.
