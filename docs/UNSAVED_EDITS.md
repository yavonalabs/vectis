# Unsaved edits

The record form tracks changes in memory. It does not store record values in browser storage. Leaving a changed form requests the browser's native warning; HTMX navigation that would replace the form asks for confirmation. Browser policies determine whether native unload warnings appear, especially on mobile. This is not crash recovery or autosave.

Successful saves with a redirect clear the warning. Validation responses retain the original dirty baseline. A 401 response preserves edited values and focuses an explanation that they were not saved; copy needed values before signing in again.

If an operator types while a save is pending, the response cannot replace those newer values. When the earlier submission succeeded, the form explains that the newer changes remain unsaved and blocks another submission with the old version/operation key. Copy the newer values and reload the record before editing again. A validation response for earlier values is suppressed while keeping the newer values available for another attempt.

## Verification — 7 October 2026

- Maven reactor verification passed: 88 Java tests, no failures or skips.
- Node suite passed: 16 tests across action-modal and unsaved-edits suites, including validation replacement, session expiry, successful redirect, late edits, and reverting during an in-flight save.
- Local demo on port 18085: browser validation retained an invalid salary; a corrected create reached the saved record without an unload warning. Logout in a second tab followed by Save in the first retained the changed first name and reason and focused the expired-session alert.
- The browser check used disposable local H2 data, not the hosted service. Native cancel-navigation, full mobile coverage and hosted verification remain pending. The in-flight response cases have automated coverage, not a timed browser reproduction.
