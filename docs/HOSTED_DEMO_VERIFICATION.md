# Hosted demo verification — 27 September 2026

URL: https://vectis-demo.onrender.com/login

The application does not expose a build identifier, so the deployed SHA must still be confirmed from Render's deployment details. The initial observed UI included the approved logo and demo notice from `34dcc8a`. A later check verified the root redirect and protected logout introduced in `f80dee9`; behavior alone does not establish the exact deployed SHA.

## Passed checks

- Follow-up on 27 September: reran the updated HTTP smoke script successfully against Render. Root redirects to sign-in, both accounts render a CSRF-protected logout control, POST logout returns to sign-in, and the old session cannot access the workspace. All earlier HTTP checks also passed.
- Follow-up browser checks at 1280 × 720: admin login, overview, Team Members navigation and the create form rendered. The create form exposes named fields, association search, reason, Save changes and Cancel controls. No record was created during this follow-up. This desktop check does not replace the outstanding mobile and assistive-technology checks below.

- `python scripts/smoke_demo.py https://vectis-demo.onrender.com` passed: anonymous access redirects to sign-in, both public sample accounts sign in, record lists render, read-only edit access returns 403, restricted activity is unavailable rather than falsely empty, all three logo assets return SVG content, and the H2 console returns 404.
- In-app Chromium browser: login at 320 × 780 rendered the card, logo, account notice and sign-in controls; sign-in succeeded. The focused username field had a visible ring.
- Mobile navigation expanded and exposed Overview, Technical Skills, Departments, Team Members and Activity. Team Members navigation worked.
- Login and record list at 320px had no page-level horizontal overflow. Record detail and an action preview were exercised at 375 × 812. The edit form at that width also had no page-level horizontal overflow and exposed Save changes; it was cancelled without saving.
- A sample leave-status action preview showed Active → On leave. Submitting reason `Hosted demo smoke test - fictional sample record` succeeded, returned to the list with success feedback, updated Alice Vance's status, and recorded that reason in the activity timeline. The fictional sample was left On leave; it resets on process restart.

## Follow-up on 28 September

- Render's authenticated dashboard confirmed live commit `852d7e79acbd5e680ecf4a21c3af93aefec29a63` (deployment `dep-dasjr95sa01s738phdv0`).
- Hosted keyboard testing found that closing a list-row delete dialog returned focus to the page body because its triggering dropdown item was hidden. The fix remembers the visible record Actions button for both delete and business-action dialogs. Local browser checks confirmed Escape from delete and Cancel from the action dialog return focus to that button. Maven verification passed all 63 tests. The local action preview returned Forbidden, so this check establishes focus return only, not successful preview behavior.
- Cloudflare access is confirmed; existing DNS has no Vectis subdomain records. Render access is confirmed, but the settings page's custom-domain section remains on Loading after a refresh. No DNS records have been changed.

Subsequent domain setup: the owner added `demo.vectis.yavonalabs.com`; DNS resolved its CNAME to `vectis-demo.onrender.com`. The full HTTP smoke script passed over HTTPS on the custom domain, including both accounts, access restrictions and logout. This supersedes the earlier domain-configuration blocker, but does not complete final-domain browser verification. A fresh diagnostic timed out on 1 October; see [current progress](RELEASE_VERIFICATION_PROGRESS.md).

## Remaining release gates

- Confirm the deployed commit in Render and verify restart/reseed and session-expiry behavior on the host.
- Complete the remaining hosted smoke checks in DEPLOYMENT.md: all navigation destinations, filters, related records, create form, drawer, delete-dialog cancellation, keyboard focus containment/return, screen-reader feedback, measured contrast, reduced motion, and table-scroll affordances.
- Repeat the full browser release matrix on the configured custom domain and recheck current availability.
- Publish the product page only after the release gates pass. The deployment being reachable is not a completed accessibility audit or production-readiness claim.
