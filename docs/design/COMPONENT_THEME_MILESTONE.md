# Studio component theme

The studio palette now applies to lists and typed filters, record details and related-record pages, create/edit forms, action and delete dialogs, the record drawer, global search, activity history, error pages, feedback toasts and the sample login page.

Template surface, text, border and accent colors use shared `--vx-*` variables. The palette uses ivory canvas, white surfaces, forest-green text and violet actions. Error/success/risk text uses darker colors for light surfaces. The sidebar retains forest green with lilac selection; record tables retain their density and sticky actions. Forms and dialogs remain operational in tone, without decorative characters or celebratory effects.

This changes presentation rather than permissions, routes, form binding or mutation behavior. The sample login imports the same stylesheet. Keyboard focus and reduced-motion rules remain; the mobile sidebar remains a disclosure menu instead of a horizontally clipped strip.

## Verification, 25 September 2026

The local Maven suite passed 60 tests. Browser checks covered successful sign-in, overview navigation, record lists, details, create/edit controls, related-record navigation, the empty activity screen and the search dialog. The edit form and list had no page-level horizontal overflow at 375px. The delete dialog was opened and cancelled without deleting a record.

The browser pass exposed two theme defects: the login card was hidden by an overly broad blur selector, and the delete button retained dark text on a red background. The selector now targets decorative elements only, and the delete control uses white text on a darker red. The login footer now uses readable muted text. The activity screen no longer claims permanent storage or an unimplemented compliance mode.

On 26 September the demo-profile browser pass additionally covered the action preview, reason entry, successful leave-status change, updated record status, activity entry, drawer content/open/close, and read-only record access. The corrected delete button was visually checked, and Escape dismissed its dialog. Tab stayed inside the action dialog in the checked transition. The public-demo notice rendered with both sample accounts and reset semantics.

Read-only access exposed a misleading activity empty state. Overview, detail and drawer templates now explain that activity is unavailable when the account lacks audit permission. A regression test covers all three routes and contrasts restricted and permitted accounts.

Validation after this fix: all 61 Maven tests passed; after extending the fix to the overview, the three permission integration tests and reactor packaging passed again. Docker image execution remains unverified because a container runtime is unavailable in this workspace.

These checks are a component smoke pass, not a measured application-wide contrast audit. Exhaustive feedback-state checks and a complete keyboard pass still need final release verification.

## Limits

The application still uses runtime CDN assets. This milestone does not constitute a complete accessibility audit or replace testing with representative users. Saved views, export and mutation reliability remain separate roadmap items.
