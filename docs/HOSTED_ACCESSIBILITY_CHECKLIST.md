# Final hosted mobile and screen-reader checks

Use https://demo.vectis.yavonalabs.com with the disposable sample accounts. Use fictional input only. Record the deployed revision from `/admin/api/build` while signed in; wait until it matches the candidate being checked. A passing check on an older revision is historical evidence.

The owner volunteered to perform these checks on 9 October 2026. Current state: not yet performed/reported. An accessibility tree inspection is not a real screen-reader test.

## Mobile: real phone or desktop responsive mode

Use portrait widths around 320 and 375 pixels; also check a tablet around 768 pixels. In Chrome, DevTools → device toolbar can supply these widths. Report browser/device and width.

1. Open the login page. Its card, credentials notice and sign-in button must remain readable without sideways page scrolling. Sign in with `admin / admin`.
2. Open navigation. Overview, Technical Skills, Team Members, Departments and Activity must all be discoverable and usable.
3. Open Technical Skills → Export applied results. Id and Name should have compact checkboxes beside their labels; tapping the label should toggle its checkbox. Select Name and download CSV. Only the Name column should be included.
4. Open Team Members. The wide table may scroll within its own container; the page itself should not scroll sideways. Apply a search with no matching results; remove it and recover the full list. Open My saved views and inspect its controls.
5. Open Alice Vance. Details, Related records, View activity and permitted operations must be reachable. Open Change leave status: proposed status, reason, Cancel and confirmation must fit and remain usable. **Cancel this review.** Avoid submitting financial/destructive operations during this check.
6. Open Edit details. Enter fictional text without saving. Cancel navigation and choose to stay: text must remain. Restore the original value, then leave. Verify required-field messages can be found and understood. Inspect the recovery reference; opening its result should keep the draft form available separately.
7. Log out. Sign in with `restricted / password`: department/team target labels must remain unavailable, with no “Not assigned” claim for denied data. Log out and confirm protected screens require sign-in again.

## Screen reader and keyboard

Use NVDA with Chrome/Firefox on Windows, or VoiceOver with Safari on macOS/iOS. Report the reader and browser versions. Do not install or change system software solely for this checklist without the device owner's agreement.

1. On login, Username, Password and Sign in must have understandable names. The demo notice must be discoverable in reading order.
2. Tab through the workspace. The skip link must move to main content; navigation/search/logout must have clear names and visible focus. At 200% browser zoom, core controls and text must remain readable and reachable. Reset zoom afterward.
3. On record detail, the reader must announce the Details/Related tabs and the selected tab. Left/Right arrows should select and focus the other tab; its panel should be named by the selected tab.
4. Open a review dialog using keyboard. Its title, record, proposed change and required reason must be announced. Tab must remain within the open dialog. Escape must close it and return focus to the trigger. Confirmation must remain disabled until required review/reason exists.
5. In an edit form, required/invalid fields and their messages must be discoverable. The recovery-result page must announce committed versus unconfirmed truthfully. Do not interpret an absent receipt as proof that an operation failed.
6. Export checkboxes must announce their column names and checked state; Space should toggle them. Risk/status information must include text rather than rely on colour alone.

## Report template

```text
Candidate/deployed revision:
Mobile device/browser and widths:
Mobile steps 1–7: pass/fail per step
Screen reader/browser and versions:
Reader/keyboard steps 1–6: pass/fail per step
200% zoom result:
Any issue: page, exact steps, expected and actual behavior
Tester and date:
```

A failed core task blocks freeze. Missing entries remain pending; “looks fine” does not establish reader/keyboard results. Report actual failures so they can be fixed and retested. This focused checklist does not claim full WCAG certification.
