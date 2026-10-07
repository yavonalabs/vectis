# Personal saved views

Open **My saved views** above a record list. Apply the search, filters and sorting first, then enter a name and choose **Save applied view**. Unsaved changes in the filter controls are not included. Open a saved name to run the current query from its first page. **Remove view** deletes only that preference, never records.

Views belong to the server-authenticated account and entity section. Read-only operators may save their own views. Administrators do not receive access to other accounts' views through this feature. Owner identity comes from `MutationActorProvider`; submitted owner parameters cannot select it. Hosts must provide stable unique principal names (including tenant identity where applicable); this feature does not establish tenant or row-level isolation.

Every list, create, open and delete checks console/entity access. Opening validates the stored version and fields again. Removed fields, invalid filters and unsupported versions fail with an explanation rather than silently removing conditions. An owner who still has entity access can remove an obsolete view.

Version 1 allows search, scalar filters (at most three), sort, direction and page size (10/25/50). Page is validated but discarded. No redirect destination, record snapshot, CSRF token or action parameters are stored. Input is bounded to 8,192 characters, 200 characters per value and 80 characters per name. The database reserves one of 50 slots per account and section; concurrent creates competing for a slot may return a conflict and require a refresh. There is no automatic retry.

Names and filter values are persisted as ordinary application data. Do not put credentials or secrets in them. Database administrators and backups can access this data; “personal” means account-scoped application access, not encryption. No telemetry is added. Define account-deletion retention with the host application and avoid reassigning principal names to different people.

## Upgrade and rollback

Include `io.github.yavonalabs.vectis.core.view.SavedView` in explicit entity scanning. Auto-configuration includes it; the sample's explicit scan is updated too. Apply `docs/sql/saved-views-postgresql.sql` through the host migration process before upgrading a schema-managed installation. The demo uses Hibernate-generated disposable H2 tables and loses views on restart.

Application rollback can leave this additive table intact. Dropping it loses personal preferences and requires a separate deliberate database migration. PostgreSQL schema/CI verification and actual restart persistence must be recorded separately from H2 tests. Do not claim those checks from the presence of a SQL file.
