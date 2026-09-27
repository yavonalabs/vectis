# Hosted demo verification — 27 September 2026

URL: https://vectis-demo.onrender.com/login

Expected source: master, commit `34dcc8a9747fd98890ec8a9fcd5cc0a8d9ca5048`. The application does not expose a build identifier, so the deployed SHA must still be confirmed from Render's deployment details. The observed UI includes the approved logo and demo notice from that commit.

## Passed checks

- `python scripts/smoke_demo.py https://vectis-demo.onrender.com` passed: anonymous access redirects to sign-in, both public sample accounts sign in, record lists render, read-only edit access returns 403, restricted activity is unavailable rather than falsely empty, all three logo assets return SVG content, and the H2 console returns 404.
- In-app Chromium browser: login at 320 × 780 rendered the card, logo, account notice and sign-in controls; sign-in succeeded. The focused username field had a visible ring.
- Mobile navigation expanded and exposed Overview, Technical Skills, Departments, Team Members and Activity. Team Members navigation worked.
- Login and record list at 320px had no page-level horizontal overflow. Record detail and an action preview were exercised at 375 × 812. The edit form at that width also had no page-level horizontal overflow and exposed Save changes; it was cancelled without saving.
- A sample leave-status action preview showed Active → On leave. Submitting reason `Hosted demo smoke test - fictional sample record` succeeded, returned to the list with success feedback, updated Alice Vance's status, and recorded that reason in the activity timeline. The fictional sample was left On leave; it resets on process restart.

## Still pending before promoting the public link

- Confirm the deployed commit in Render and verify restart/reseed and session-expiry behavior on the host.
- Complete the remaining hosted smoke checks in DEPLOYMENT.md: all navigation destinations, filters, related records, create form, drawer, delete-dialog cancellation, keyboard focus containment/return, screen-reader feedback, measured contrast, reduced motion, and table-scroll affordances.
- Configure the final custom domain and repeat the release smoke pass there.
- Publish the product page only after the release gates pass. The deployment being reachable is not a completed accessibility audit or production-readiness claim.
