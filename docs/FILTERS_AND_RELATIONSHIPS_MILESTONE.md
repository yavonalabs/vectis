# Filters and relationship lookup milestone

14 September 2026. Builds on [the operator UI milestone](OPERATOR_UI_MILESTONE.md).

## Implemented

- Lists provide up to three independently typed conditions combined with AND, alongside text search. Supported scalar fields include text, numbers, enums, booleans, local dates and local date-times. IDs, version fields and ignored fields are excluded from the filter picker.
- Operators include equality, inequality, text contains, numeric/date comparisons and null checks. Invalid field names, incompatible operators, malformed values, excessive conditions and oversized inputs are rejected by the server. Null checks refer to missing database values, rather than whitespace-only strings.
- Enum and boolean conditions use labeled choices. Numeric fields with an explicit currency display that code in the filter picker. Search/filter changes apply together using the Apply button.
- Sorting and pagination preserve the condition arrays and search. Sorting adds the record ID as a tie-breaker. Literal `%` and `_` in text search no longer act as database wildcards.
- Invalid filters produce a visible error and no result table, rather than falling back to an unfiltered query. HTMX handles these marked error responses without treating them as successful queries.
- Single-valued relationship forms load at most 25 initial choices plus the current selection if it is outside that page. They use the related entity's own ID metadata when choosing the selected option.
- A permission-checked relationship lookup endpoint provides search and pages of 25 results. The picker preserves the current selection while searching or paging and after failed requests. Editing the search text invalidates an outstanding lookup so stale responses cannot replace newer choices.

## Request contract

List URLs use repeated, ordered `filterField`, `filterOp` and `filterValue` parameters. Each array must have the same length, with at most three rows. Empty rows are ignored. For example:

```text
filterField=status&filterOp=eq&filterValue=ON_LEAVE
filterField=salary&filterOp=gte&filterValue=90000
```

These represent two conditions when sent together. Values are converted using field metadata and applied with JPA Criteria predicates; they are not interpolated into SQL.

The existing six-argument query-engine `findPage` method remains available and delegates to the version supporting conditions. Existing text search URLs still work. The old enum shortcuts are replaced by the typed filter controls.

Relationship lookup uses `GET /{configured-path}/{resource}/relationships/{association}/options?search=...&page=...`. It requires permission to view and edit the source resource and view the target resource. Only single-valued associations defined in exposed metadata are accepted. Existing entity permissions apply; this does not introduce row-level or field-level policy support.

## Scope and limits

Saved views, nested OR groups, relationship-based list filters, user-defined page sizes, many-valued relationship editing and general timezone-aware filters are not included. Large detail-page collections still need separate pagination work. Query execution limits and production database/load testing remain future work; bounded result pages do not imply bounded database execution time.

No additional business mutation workflow was introduced. The existing transaction, concurrency and durable retry work remains necessary before stronger production guarantees can be made.

## Verification

- `mvn verify` passed across all five modules: 49 tests, no failures or errors. Coverage includes typed conditions, invalid values and request shapes, permission checks, custom application paths, bounded relationship pages and preservation of an off-page selection.
- Browser checks confirmed combined status, salary and text filters, sorting with conditions retained, and invalid conditions hiding the results with a visible error.
- Browser testing exposed a lookup button reading its URL from the wrong element; the picker now reads it from its Alpine component root. Invalid filter states say “Review filters” and provide a reset link.
- Final rebuilt-app browser checks passed: relationship search returned the matching department while retaining the original selection; an empty search result retained a newly selected department and displayed the no-results message. The edit was cancelled without saving. Incomplete filters displayed “Review filters”, focused the validation alert, hid the result table and offered a working reset link.
