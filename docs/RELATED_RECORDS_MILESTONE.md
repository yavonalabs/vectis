# Related-record browsing milestone

20 September 2026.

## Implemented

- Detail pages and record drawers request at most 25 records per exposed collection. They no longer initialize the entire source collection to build its list of labels.
- A separate related-record browser offers previous/next navigation, stable target-ID ordering, a total count, safe labels and links back to the source record.
- Each request checks permission to view both source and target resources before querying collection contents. Read-only users can browse permitted relationships.
- Unknown associations, single-valued associations on the collection route, missing source records and invalid page offsets are rejected.
- Inaccessible collections show an unavailable state rather than falsely reporting an empty collection.
- The existing two-argument query-engine `findById` preserves its collection-initialization behavior for existing callers. Detail rendering uses the new overload with initialization disabled.

## Limits and remaining work

Paging bounds returned collection rows; count queries can still be expensive. Host entity mappings with eager associations can trigger additional loading independently of Vectis. Database/load tests, query execution limits and composite identifier compatibility remain separate work.

Saved personal/shared views, restoring list context through detail/edit navigation, many-valued relationship editing, nested OR filters and permission-aware export remain in the roadmap. Transaction unification, optimistic concurrency and durable duplicate-submission handling remain required before stronger mutation guarantees.

## Verification

`mvn verify` passed all five modules: 53 tests, no failures or errors. New tests cover a 31-record collection split into disjoint 25/6 pages, an uninitialized source collection, read-only browsing, source and target permission denial, invalid offsets, missing records, unsupported relationships and custom context/admin-path links. `git diff --check` passed.

Browser verification confirmed the detail-to-related-record journey, correct labels/counts and the rendered desktop layout. The 31-record multi-page scenario is covered by integration tests; the browser sample contains two skills.
