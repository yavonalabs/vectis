# List navigation milestone

20 September 2026.

## Implemented

List record links and create/edit links carry the current search, repeated filter rows, sort direction, page size and page. Detail Back to list links, form Cancel links and breadcrumbs restore that state. The detail drawer forwards the same context into its full-page and edit links. Related-record paging preserves the context on the route back to its source record.

The context is carried explicitly in `_list`, not shared browser storage. Independent tabs can therefore retain different views. Existing list query URLs continue to work, and direct record links without context return to the ordinary list.

Form validation retains the context. Successful HTMX saves open the saved record with its list context; ordinary form saves return to the originating list. Existing query validation and entity permissions run again when returning.

## Input handling

Only known list query keys are retained. Repeated filter values retain their order. Malformed percent encoding, control characters, excessive values, excessive parameters and oversized context are discarded. Redirect destinations are constructed from the configured admin route and current resource; the context never supplies a host or path.

Like existing list URLs, record URLs carrying context can contain search/filter values. This adds no browser persistence or shared-view storage, but host applications must still define their URL/history/logging policy for sensitive search terms.

## Remaining scope

Saved personal/shared views, export, unsaved-edit warnings, mutation lifecycle/concurrency/retry work and production database/load validation remain. Returning across an unrelated target resource uses that resource's own list rather than carrying filters for a different entity. Custom action/delete redirects retain their existing behavior. A saved change may move a record out of the originating filter or leave a formerly populated page empty; the query is restored rather than silently changed.

## Verification

`mvn verify` passed all five modules: 58 tests, no failures or errors. Added coverage for context sanitization, repeated filters, detail/edit/drawer links, custom context/admin paths, normal and HTMX saves, and validation-error context retention. `git diff --check` passed.

Browser checks passed for filtered list → detail → edit → Cancel and for an HTMX page-size update → detail → Back to list. Search, repeated filter rows, sort direction and page size were retained. An expired sample session initially rendered the login page inside the table fragment; after signing in again, the navigation checks passed. Session-expiry handling for HTMX requests remains a separate follow-up.
