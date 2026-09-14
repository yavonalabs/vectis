# First implementation milestone — 13 September 2026

Later update: [the operator UI milestone](OPERATOR_UI_MILESTONE.md) adds presentation improvements and resolves the sample termination/salary conflict mentioned below. This document preserves the first milestone's original verification record.

This change set addresses the initial review's confirmed safety and workflow defects. The full roadmap remains in [VECTIS_IMPROVEMENT_PLAN.md](../VECTIS_IMPROVEMENT_PLAN.md). The original [review](../VECTIS_REVIEW.md) is a historical baseline, not the current test status.

## Implemented

- Unconditional permission checks for action execution and previews; anonymous tokens are rejected.
- Configured administrator and read-only roles, with denial as the fallback when security integration is absent.
- Search, entity navigation, audit summaries, related-record labels, and relationship editing respect entity permissions.
- Relationship labels use allowed metadata rather than arbitrary entity `toString()` output.
- Explicit preview callbacks return proposed values. The execute handler is never called by the preview endpoint. Unknown, ignored, ID, and version fields are excluded from the displayed changes.
- Preview final-state validation, missing-preview errors, and a registered external-call protection aspect that fails closed on intercepted calls.
- Spring service proxy-aware action discovery and method security enabled in the sample.
- Action dialogs now open, display preview/loading/error states, require a reason, and block confirmation when preview is missing or fails.
- Frontend JavaScript is served from one local file; table swaps no longer register repeated document listeners.
- Successful HTMX creates/edits navigate to the saved record and display a success message. Jakarta validation errors retain the form and reason.
- Global search is registered and working. Custom base paths and servlet context paths are covered by tests.
- Invalid pagination, unknown sort fields/directions, invalid record IDs, blank reasons, and stale submitted preview versions are rejected.
- The UI shows the actual signed-in username and configured environment label. Unimplemented export/selection controls and misleading immutable-audit wording were removed.
- Getter-based JPA metadata can now expose the annotations present on that getter, including `@AdminIgnore`.

## Browser verification

Final `mvn verify`: **33 tests passed** (1 core test and 32 sample integration tests), with no failures, errors, or skipped tests. All five modules built successfully. `git diff --check` passed. The run output is saved in `phase1-test-output.log` at the repository root.

Using only the sample application's disposable in-memory H2 data:

1. Logged in as administrator and opened the record list.
2. Opened Carlos's promotion preview; verified confirmation was disabled until a reason was entered.
3. Executed the promotion; verified the success message and salary change from 95,000 to 114,000.
4. Created a new record; verified navigation to its detail page, saved values, success message, and activity entry.
5. Submitted an invalid blank first name; verified the validation message, other values, and retained reason.
6. Corrected and saved the edit; verified successful navigation and feedback.
7. Used global search to open Alice's record.
8. Applied the leave-status filter and opened Carlos in the detail drawer.
9. Logged in as a read-only user; verified creation, editing, action buttons, and audit navigation were unavailable while browsing still worked.

No JavaScript errors were captured during these flows. The local browser-test server was stopped afterward. The final relationship-permission refinement is covered by its Spring integration test; it does not alter the administrator browser flow.

## Compatibility changes

See [SAFE_ACTIONS.md](SAFE_ACTIONS.md) before integrating this version:

- Actions need a separate preview method to be confirmable through the UI.
- Direct execution remains governed by its normal server-side policy; previews are not authorization tokens.
- Access is no longer granted to every authenticated user by default.
- Mutation requests require the configured reason rules.
- Preview text no longer assumes all numeric values are USD.

## Next milestone

1. Shared transaction semantics for standard mutations and their required audit records.
2. Durable protection against duplicate action execution on retries.
3. Required version/concurrency semantics across all supported mutation paths.
4. Resolve the sample termination salary rule, then keep preview and execution aligned.
5. Complete the operator-view redesign, including clearer labels and field-level error presentation.

Structured filters, real export, bounded relationship selectors, broader database/mapping compatibility, production load testing, and accessibility work remain on the roadmap. Explicit preview callbacks are trusted developer code, not a sandbox; authors must keep them free of external effects and related-object mutations.
