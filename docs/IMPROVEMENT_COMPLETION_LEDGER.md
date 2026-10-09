# Improvement completion ledger

Updated 9 October 2026. Scope remains BE-01–BE-08 and UI-01–UI-06 in the linked plans. **Code is not frozen.** This ledger distinguishes implemented work from its remaining acceptance evidence.

| Area | Current implementation/evidence | Remaining acceptance |
|---|---|---|
| Mutation contract | Immutable typed request/result adapter, server-derived actor, save identity binding, explicit managed/host modes; commit/replay/conflict/unknown and outer-transaction tests | Final HTTP/UI outcome parity and recovery journey |
| Managed local actions | Entity, success audit and receipt share a transaction; version guard covers no-op actions; rollback and independent concurrent-request tests | Current PostgreSQL 16 CI and independent review |
| Replay/recovery | Actor-scoped durable receipts, authorization rechecks, result endpoint, indefinite receipt retention; separate packaged consumer retries after actual JVM restart on persistent H2 | Upgraded artifact restart recheck; PostgreSQL migration/restart CI; user-facing recovery reference |
| Reviewed proposals | Actor/action/record/input/version binding, ten-minute expiry, single-use enforcement with exact completed replay | Related-state dependencies are not implicitly covered; final acceptance review of supported contract |
| Activity | Correlated success audits; readable diffs through current exposed scalar metadata; exact timestamps | General durable failed/unknown journal is not provided; hosted accessibility verification |
| Restricted data | Single and collection relationships distinguish unavailable from absent; inaccessible action containers hidden; permission regression tests | Final hosted restricted-account matrix |
| Export | Separate default-deny permission, explicit scalar columns, applied filters/sort, 1,000 rows / 20 columns / 2 MiB / 2,048 characters per cell; CSV/formula and bounds tests | Hosted permitted/denied/error journeys and driver timeout/cancellation evidence |
| Forms and views | Native conflict input/version retained; HTMX conflict/server-error preservation; personal saved-view protections retained | Final native/HTMX expired-session, conflict, cancellation and persistent saved-view restart matrix |
| Record UI | Host-defined groups, accessible Details/Related tabs, activity link, permitted field diffs, applied-filter chips, unapplied-filter indication | Action-description density, final keyboard/screen-reader/contrast/zoom checks |
| Assets | Pinned local JS/fonts and compiled utilities with licenses; no required runtime CDN | Reproducibility CI; build-time dependency advisories reviewed before release; strict nonce/no-eval CSP remains unsupported |
| Compatibility/install | Java 17; Spring Boot 3.5.16 candidate; separate consuming application, SQL upgrades and restart fixture | Current final package and PostgreSQL CI; remaining consumer scenarios in BE-07 |
| Release identity | Packaged revision endpoint and CI/Docker stamping inputs | Verify actual deployed SHA, artifact checksums, exact hosted matrix |
| External release | Public repository, product/distribution drafts and pilot plan exist | Independent security review, publishing credentials/download proof, independent installation and measured pilot evidence |

Local evidence is recorded in RELEASE_VERIFICATION_PROGRESS.md. Earlier hosted and PostgreSQL passes do not certify this candidate. Do not publish a production-readiness or code-freeze claim until CODE_FREEZE_BOUNDARY.md criteria are satisfied. External review and pilot participants are separate release gates and must not be invented.
