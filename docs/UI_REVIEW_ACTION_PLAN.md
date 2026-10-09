# UI review: validated findings and bounded delivery plan

Date: 7 October 2026. Baseline: `4ba4d57`. Status: required refinements, not a new visual redesign.

Goal: help an operator find the right record, understand it, perform a permitted operation and understand the result. Preserve the approved ivory/forest-green/violet identity and friendly tone. The [backend plan](BACKEND_REVIEW_ACTION_PLAN.md) supplies the guarantees; the [freeze boundary](CODE_FREEZE_BOUNDARY.md) limits expansion.

## Validated baseline

Desktop browser inspection used the local packaged app, admin and restricted sample accounts. No business mutation was submitted. This was not a full mobile, contrast or screen-reader audit.

- Record labels, restrained sample tables, hidden technical details, named actions, risk badges, a distinct delete treatment, per-record activity and Ctrl/Cmd+K search already exist.
- The action dialog already shows the record, proposed field change, required reason and a named confirmation button. Confirmation stays disabled when required review data/reason is missing.
- Long descriptions in the action column push activity below the initial viewport. Details have a flat field grid and no business-oriented grouping. Activity displays actor/time/action/reason, without readable field differences.
- Personal saved views exist in a collapsed section. Shared/team views do not exist.
- Confirmed bug: Alice's department is Engineering for admin, but the restricted account sees “Not assigned.” The unavailable collection correctly says “Related records unavailable.” Permission denial must not be presented as missing data.
- Shared colours, typography, focus rules and responsive styling already exist. Smaller radii, blue actions and an entirely neutral palette are preferences, not established usability requirements.

## Required work packages

### UI-01 — Honest empty, unavailable and recovery states

- Distinguish an absent single relationship from an inaccessible target; fix the confirmed department label without exposing its value or existence beyond the host's policy.
- Cover list/no results, inaccessible data, missing record, expired session, invalid filters, stale record and failed preview with appropriate next actions.
- Hide empty action containers or explain that no operations are available for the account. Do not leave an unexplained “Operations hub” shell.
- Verify untouched forms do not warn, cancelled navigation retains input, validation retains entered values and session expiry does not discard edits.

Acceptance: admin/restricted views of the same fixture display truthful states; no secret target labels leak; every recoverable error offers a useful next step without recommending blind mutation retries. Keyboard users can discover errors and return to their task.

### UI-02 — Review, submit and outcome flow

- Use action-specific titles/buttons and clearly identify the affected record. Show before/after values where supplied; distinguish no visible scalar change from no external effect.
- Keep reason capture appropriate to the action and expose host-declared consequences accurately. Never infer email, access revocation or payment effects from an action name.
- Give loading/submitting a visible, announced state and prevent accidental repeat submissions. Use backend replay semantics, not disabled buttons alone, for duplicate protection.
- Present succeeded, invalid, rejected, conflict, failed and unknown/pending according to BE-01. Offer activity/result inspection after success and safe recovery after uncertainty.
- Retain input on recoverable failures. Require fresh review when the record/proposal is stale. Do not silently resubmit changed input with an old key/proposal.
- Keep local edits lightweight; add review friction in proportion to the declared consequences. Do not impose six separate screens on every change.

Acceptance: keyboard-accessible review and cancellation; no submit when preview is invalid; late responses cannot overwrite newer edits; final outcome matches the backend. Never show “This operation can be executed safely,” permanent permission checkmarks or fabricated completion states.

Dependency: BE-01 through BE-04 for guaranteed execution states. Layout changes alone cannot satisfy that dependency.

### UI-03 — Record detail and activity hierarchy

- Keep name/label and the important status easy to scan; make technical identifiers secondary rather than repeating them prominently. Do not remove business identifiers operators need.
- Support a small, backward-compatible field-group presentation mechanism with a “Details” fallback. Group labels come from host presentation metadata, not guesses about fields or business meaning.
- Make Details, Related records and Activity discoverable without placing activity underneath a long action list. If presented as tabs, implement selected-state semantics, labels, keyboard behavior and focus handling.
- Give routine actions, consequential changes and destructive actions appropriate prominence using labels plus visual treatment. A moderate-risk badge does not imply reversibility.
- Keep descriptions short in the action list and show full consequences in review. Preserve permission filtering.
- Render readable permitted before/after changes with actor, time and reason. Provide exact timestamps alongside any relative display. Show only outcomes backed by BE-05.

Acceptance: the sample workflow can be completed without searching below unrelated action cards for activity; absent/restricted sections remain understandable; long labels and large amounts wrap/scroll within their own containers; field grouping never exposes additional fields.

Dependency: BE-05 for historical change projection. Raw stored audit JSON must not be rendered directly.

### UI-04 — Filters, personal views and export discoverability

- Retain the visual typed-filter builder as the default. Add readable applied-filter summaries/chips with removal and clear-all, keeping the existing bounded AND semantics.
- Distinguish applied filters from changes not yet applied. Saving or exporting must use the configuration the interface says it will use.
- Make personal views easier to find within the record section; keep clear empty, saved, removed, obsolete and quota states. No team-sharing controls in this cycle.
- Add export controls only when permitted. Explain selection/row/byte limits and incomplete-result behavior before or alongside the download; preserve a useful error path.
- Keep the sample's restrained default columns. Developer presentation metadata remains the column configuration mechanism for this cycle.

Acceptance: an operator can apply conditions, save a view, reopen it, remove one condition and export the permitted result without learning query syntax. Saving/removing a view does not mutate records. Hidden fields remain absent.

Dependency: BE-06. Query-language parsing, column-personalization persistence and shared views are deferred.

### UI-05 — Consolidate the existing design system

- Keep the approved palette, logo and visual personality. Consolidate duplicated theme overrides into shared semantic tokens and reusable component rules; do not introduce a framework rewrite.
- Use consistent body/secondary text, spacing, buttons, inputs, focus indicators and dialogs. Treat the review's exact pixel/radius choices as suggestions, not acceptance criteria.
- Use colour with text/icons, never as the only carrier of risk, status or validation. Keep playful illustration/copy away from destructive, financial and uncertain-result confirmations.
- Preserve comfortable operational density and avoid new dashboard charts, marketing-style sections on task screens or decorative animation in critical flows.

Acceptance: the affected screens share consistent tokens and controls; measured text/control contrast meets applicable WCAG AA criteria; focus is visible, motion preferences respected and information remains readable at 200% zoom. This does not itself constitute a full accessibility certification.

### UI-06 — Local and hosted verification

For the exact candidate SHA, record desktop and 320/375/768px checks on the local package and hosted custom domain. Include login/error/logout; navigation; list/filter/no-results; saved views; detail/related/activity; create/edit validation; review/cancel; expired session; conflict/unknown outcome; and permitted/denied export.

Verify keyboard order, skip link, modal containment and focus return, Escape, accessible tab behavior, table scrolling, error/status announcements, reduced motion, contrast and 200% zoom. Perform a real screen-reader check; record unavailable tooling as pending, not passed. Verify responsive navigation has no undiscoverable clipped destinations.

Use fictional fixtures. Prefer cancelling destructive operations; use separately identified disposable records where an actual write is necessary and verify cleanup. Do not generalize a desktop screenshot or automated accessibility scan to the full matrix.

Acceptance: every matrix entry has build/environment/result/evidence; blocking defects are fixed and affected paths retested. No release claim that “mobile works” inherits automatically from an earlier local milestone.

## Explicitly deferred

Team/shared views, bulk operations, dark mode, query syntax/natural-language filters, configurable-column persistence, new shortcuts beyond maintaining existing ones, analytics dashboards and another brand redesign are outside this freeze cycle. See the freeze document for how scope can be reopened.
