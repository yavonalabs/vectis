# Vectis Studio review — 12 September 2026

Reviewed checkout `31ee99e` in `D:\EntityAdmin`. Application code was not changed. Two regression test classes and this report were added.

## First impression

Vectis has a useful product direction: a Spring application can expose named business operations to support staff without requiring those staff to write SQL. Explicit entity opt-in, field descriptions, relationship navigation, Jakarta validation, and human-readable action previews are good foundations. The dark visual design is coherent.

I would currently treat this as an early developer prototype, not a production console for nontechnical operators. Several safety promises are stronger than the behavior I reproduced. Reliability and access control need attention before more features.

The best experience for your audience would lead with tasks such as “Find a customer,” “Put an account on hold,” or “Correct contact details.” The current table explorer still exposes database concepts: `varchar`, `int4`, `int8 [pk]`, `fk`, version columns, “Insert Row,” and enum names such as `ON_LEAVE`. Those are useful to developers but add little for support staff. A simple operations view with an optional developer view would fit your stated audience well.

Running inside Spring does not automatically enforce all business rules. Generic CRUD calls JPA directly through the query engine; it validates bean constraints, but does not automatically call the application's business service methods. Describe that distinction clearly, and consider making explicit domain actions the default way nontechnical users change sensitive records.

## What was actually tested

Environment: Windows, Java 17.0.12, Maven 3.9.14, bundled Spring Boot 3.3.3, disposable in-memory H2 databases. The sample application was also started on local port 18080 for browser checks.

- Baseline `mvn verify`: successful across all five modules; the two existing tests passed.
- Additional regression run: **21 tests, 9 passed, 12 failed, 0 errors, 0 skipped**. Failures assert expected safety/configuration behavior and expose defects; they are not twelve independent root causes.
- Browser checks: login, overview, record list, status filtering, action menu/preview, create form submission, saved-record visibility, global-search interaction, export, and visual inspection of the post-save screen.
- Passing automated checks: anonymous dashboard redirects to login; CSRF blocks an unprotected mutation; eight main routes render; case-insensitive search and salary sorting work; ignored fields are absent from ordinary list/form output; salary preview rolls back database state; invalid salary is rejected without changing the record; create/update/audit/delete works through server requests; missing-record detail returns 404.

Run the added probes from the repository root:

```powershell
mvn test '-Dtest=ReviewRegressionTest,ReviewCustomPathTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

The new tests are deliberately failing regression specifications, so ordinary test builds will also fail until these defects are fixed. The original baseline passed before these tests were added. Full output is in `review-test-output.log`; individual reports are under `vectis-sample-app/target/surefire-reports`.

## Confirmed issues, in priority order

### 1. High: admin-only actions can be invoked by a regular user

`AdminController.java:419` calls `canExecuteAction` only when `requiredRole` is populated. The sample promotion action instead uses `@PreAuthorize("hasRole('ADMIN')")`. The sample does not enable Spring method security. A `USER` request to `/admin/employee/action/promoteEmployee/3` was accepted and returned a successful-action flash message and HTTP 302 rather than 403. The test encloses the mutation in a rollback transaction.

The preview endpoint also returned HTTP 200 and the salary diff to a `USER`. `ActionPreviewController` has no permission evaluator check. Hiding a button is insufficient protection.

Fix: evaluate authorization unconditionally for both execution and preview, including entity access. Exercise the same policy at every entry point, and test with real method-security proxies as well as the default sample configuration.

### 2. High: action previews expose ignored fields

`ActionPreviewController.java:95` compares complete entities. When a changed property is absent from the visible metadata, line 106 falls back to its raw property name rather than excluding it.

A test-only action changed `internalSecurityToken`, which is annotated `@AdminIgnore`. The preview returned both the original sample token and the new token in plain text. Ordinary list/form redaction passed, so this is a distinct preview leak. The test action exists only in the review fixture.

Fix: build preview output from an explicit allowed-field projection and apply redaction across related objects too.

### 3. High: preview external-call protection is inactive in the starter configuration

`DryRunProtectionAspect` is annotated as a component but is not registered by `VectisAutoConfiguration`. The sample scans its own package, not the core package. The application context contained no protection aspect.

A test-only service method marked `@ExternalApiCall` incremented a counter during preview. Expected calls: 0; actual: 1. No real external service was contacted. Database rollback passed separately, demonstrating that database rollback alone does not suppress non-database effects.

Fix: explicitly wire the aspect and verify proxy/AOP behavior. Document limits involving self-invocation, asynchronous work, independent transactions, and unannotated external calls. A separate side-effect-free preview contract would offer a stronger guarantee than executing arbitrary business code and rolling it back.

### 4. High usability impact: the action dialog does not open

Browser reproduction: log in as admin, open Team Members, open Carlos's menu, click “Grant 20% Promotion.” The menu closes but the preview dialog does not open.

`templates/vectis/layout.html:277` contains incorrectly escaped quotes inside the Alpine `x-data` expression. Browser logs show `SyntaxError: missing ) after argument list`, followed by undefined preview-state variables.

Fix: correct the expression or move this controller into a JavaScript module, then add a browser test that opens the modal, displays the diff, validates the reason, and executes the action.

### 5. High usability impact: successful form submission removes the form without success navigation

Browser reproduction: create a valid Team Member with a reason and click Save Record. The form disappears; the page remains headed “Create New Team Members,” with a large blank area. Navigating to the list confirms the record was created.

`fragments/edit-form.html:39` uses `hx-select="#edit-form-container"` and an outer swap. `AdminController` returns a normal redirect to the list on success, but the resulting list has no matching form container. An operator cannot tell whether the save succeeded and may retry.

Fix: return an HTMX navigation response on success or a matching success fragment. Keep validation failures in the form and preserve input.

### 6. Medium: global record search is unavailable and export is a placeholder

Authenticated `GET /admin/api/search?q=Alice` returns 404. `GlobalSearchController` exists but is not registered in the auto-configuration. The command palette opens, but does not provide the expected record result in the sample.

Clicking Export displays “CSV Export feature coming soon.” This is a visible unfinished feature, not a working export flow.

Fix: explicitly register the search controller with permission checks, and either implement export or label/remove it until available.

### 7. High conditional security risk: anonymous tokens pass the permission evaluator

`SpringSecurityPermissionEvaluator.java:34` relies on `Authentication.isAuthenticated()`. A Spring anonymous authentication token passes this test. The direct evaluator regression reproduced access being granted.

The default sample's `/admin/**` HTTP security rule still redirects anonymous visitors, which passed testing. This is therefore not a demonstrated anonymous bypass of that default route. It becomes a concern when host applications rely on the evaluator or change the route/security configuration.

Fix: reject anonymous authentication explicitly and make the host application's security requirements clear.

### 8. Medium: custom route configuration is only partially implemented

With `vectis.path=/ops`, the dashboard renders at `/ops` but its entity links still point to `/admin/employee`. Preview at `/ops/api/employee/action/promoteEmployee/3/preview` returns 404. Templates, redirects, JavaScript and preview routes hardcode `/admin`.

Fix: resolve one configured base path consistently, including context-path deployments and HTTP security configuration.

### 9. Medium: server-side safety requirements differ from the UI

A blank reason was accepted for a moderate-risk action (HTTP 302 instead of rejection). HTML `required` is the only effective reason enforcement in this path.

The termination preview returned success for salary changing to zero even though the sample entity has `@Min(30000)`, which the execution/save path validates. Preview does not perform the same validation as execution.

Fix: validate the reason and final entity state on the server. Explain validation failures during preview rather than presenting an operation as executable.

### 10. Medium: pagination accepts an invalid zero page size

`GET /admin/employee?size=0` returned HTTP 200. The query engine divides total rows by zero when calculating total pages. Page and size have no declared validation or upper bounds.

Fix: reject invalid page parameters, cap page size, and test page arithmetic and empty/out-of-range results.

## Further code-review concerns, not fully reproduced

- CRUD and audit persistence use separate transactions. The audit listener uses `REQUIRES_NEW`; a write and its audit are not guaranteed to succeed or fail together. Do not promise a complete immutable trail without testing audit-storage failure and transaction rollback.
- Association options load every related entity through `findAll`, and entity detail eagerly initializes collections. Large production datasets need bounded queries and a relationship selector with search.
- Metadata reads annotation details only when JPA exposes a Java `Field`; property-access entities need dedicated ID, validation, and redaction tests. Composite identifiers also need end-to-end tests because templates frequently render raw IDs rather than using `IdCodec`.
- The starter's `@EntityScan` lists the audit entity; the sample compensates by explicitly scanning both application and audit entities. Test a minimal consuming application without that extra sample configuration before calling setup “single dependency.”
- Status pills implement a general substring search rather than an exact field filter. Multi-column sorting and structured filters described in requirements are not implemented by the current list controller.
- The sample labels every screen “Production.” The UI also declares safety checks “ON” as static copy. Those indicators should reflect actual configuration.
- Dynamic CDN scripts and styling need a deliberate deployment strategy for restricted networks and reproducible assets.

## Product improvements for your intended audience

1. Start with a named business task, search by familiar identifiers, and show the person's/customer's name prominently in confirmations.
2. Hide schema types and version columns in the default operations view. Use “Add team member,” “On leave,” and “Save changes.”
3. Provide clear per-field errors, visible save success, and a reliable route back to the changed record.
4. Expose only actions the user may perform; make view-only roles easy to configure. Sensitive operations should use business services with explicit policy.
5. Show exact filters and an understandable summary of the selected records before any future bulk operation.

My first release priority would be permissions, preview safety/redaction, the action dialog, and save feedback. Once those are reliable, test realistic support workflows with a few nontechnical users before adding more controls.

## Limits of this review

This was a substantial local functional and security-focused review, not production certification. PostgreSQL/MySQL, high-volume load, concurrent edits, multi-tenant isolation, SSO, accessibility/screen readers, mobile layouts, real third-party effects, and dependency vulnerability auditing were not tested. No production database or external account was used. The test counter was a simulated external effect. Review findings distinguish reproduced behavior from inspection concerns.
