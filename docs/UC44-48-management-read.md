# UC44-48 Management Read Flows

## Current UC48 transaction filters

Admin/Manager transaction pages use the existing paginated GET endpoint. Supported
filters combine with AND: courseId, outcome, provider (SePay or SePay Test Mode),
UTC date range and inclusive amount range. Search matches provider reference,
Student name/email, course title, or a transaction ID (`62`, `#62`, `OLST62`,
`OLSL62`, `OLS62`). Text wildcard characters are escaped rather than interpreted.

Each known payment attempt has its own outcome. An expired/failed old attempt is
not reported as successful just because its parent Payment later succeeded. DTO
display and database filtering share the same type/status mapping; unknown legacy
types retain the existing parent-Payment fallback. Retired provider attempts remain
readable and are not mislabeled as SePay after an unpaid provider transition.

Validation: positive course ID; search <= 200 characters; provider <= 64 characters;
nonnegative amounts with <= 12 integer digits and 2 decimals; valid ordered date
and amount ranges; bounded pagination. Dates include the entire final UTC day and
are displayed in UTC consistently. Filters survive paging and detail/back navigation;
applying filters returns to page 0, and Reset removes them.

Verified using isolated H2 command checks and desktop/mobile browser fixtures,
without new test classes or modifications to the user's MySQL records.

## Original Slice Reference

Scope agreed in chat: implement registration/transaction list, detail and filters with
Spring Boot + React; payment initiation (UC70) and revenue (UC49) are deferred.
The user authorized proceeding from OLS_MASTER_CODING_PROMPT.md and existing UI;
the original SRS and visual mockups were not available. Visual conformity to SRS
has therefore not been certified.

| UC | Backend | Frontend | Verification | Status |
| --- | --- | --- | --- | --- |
| UC44 | GET /api/v1/{admin,manager}/registrations | /{admin,manager}/registrations | pagination, search, role tests | Read slice implemented; C12 actions pending |
| UC45 | GET /api/v1/{admin,manager}/registrations/{id} | /{admin,manager}/registrations/:id | snapshot values, 404, role tests | Read slice implemented |
| UC46 | GET /api/v1/{admin,manager}/transactions | /{admin,manager}/transactions | payment/registration relation tests | Read slice implemented |
| UC47 | GET /api/v1/{admin,manager}/transactions/{id} | /{admin,manager}/transactions/:id | no raw provider payload, 404 | Read slice implemented |
| UC48 | Shared transaction GET with query parameters | Transaction filters | date endpoints, amount, provider, status | Implemented to MD scope; SRS filter conformity pending |

## Business-rule boundaries

- BR48-55: registration details use retained financial/package snapshots. Missing
  snapshots remain null; the UI says they were not saved, rather than recalculating
  from today's course prices.
- BR56-62: payment/transaction data is read-only; no callback, gateway request,
  access grant or financial mutation is introduced.
- Manager/Admin authorization is enforced both by SecurityFilterChain URL rules
  and in ManagementReadService. Student, Expert and Guest have no management access.
- Sorting uses descending ID, a stable fallback when historical dates are unknown.
  Lists paginate/filter at the database, with page sizes capped at 100.
- Dates in query parameters are UTC calendar dates with an exclusive end boundary.
- No password hashes, tokens or raw provider payloads are returned by these DTOs.

## Migration

V006__management_read_metadata.sql is rerunnable on MySQL 8 and adds nullable
registration metadata, transaction timestamps and three indexes. No historical
values are fabricated. Local application was preceded by a mysqldump backup.
New entities record createdAt on persist; historical records without a known date
remain null and will not match date-range queries.

## Pending scope

- C12 registration mutation actions (Add Note, Approve pending transfer, Cancel,
  Restore, Extend Validity), their audited transitions and export are not
  implemented in this read slice. Exact transition/extension/export rules require
  the original SRS or explicit Product Owner decisions.
- CoursePackage creation, package maintenance and registration creation/snapshot
  population are upstream work, not implemented by these read APIs.
- Payment (UC70) and revenue (UC49) intentionally deferred.
- No production/demo registrations or transactions were inserted for UI tests.
  Local live data is empty. Populated detail rendering uses isolated UI fixtures.

## Verification

- Maven test: 15 tests passed, including 9 new H2 management API tests.
- Frontend lint, TypeScript noEmit and production build passed.
- Live MySQL APIs responded successfully for Manager; existing list data is empty.
- Browser QA script and screenshots are in the task's visualization directory.
