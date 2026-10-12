# hai → DongPH payment merge

Merge keeps DongPH's existing SePay test/live QR module, My Courses, registration
filters, reports, responsive fixes and Google configuration endpoint. It does not
introduce a new provider or convert existing financial records. The previous VNPAY
requirement is not reimplemented as part of this Git integration.

Conflicts were resolved by keeping payment-aware registration controllers/services,
filtered list APIs and payment routes rather than restoring the earlier placeholder
payment notice from hai. Course package pricing, pending registration snapshots and
Student homepage landing from hai remain in place. Existing source architecture is
unchanged. Cloudinary credentials are environment-only in the merged YAML.

Student route flow:
`/courses/{id}` → select package → `/student/courses/{id}/register?packageId=...`
→ create/reopen pending registration → `/student/registrations/{id}/payment`
→ QR initiation → authenticated SePay webhook → ACTIVE registration → My Courses.

Registration-detail/payment query caches are scoped to the logged-in user while
retaining ID-based invalidation prefixes; switching accounts cannot reuse another
Student's cached registration/payment response.

The hai regression suite and its H2 test profile were retained after modify/delete
conflicts. Never run integration tests against the development MySQL schema.
`PaymentMergeIntegrationTests` uses its own H2 database, dummy receiver/key and no
network calls. It covers repeated initiation, no access before payment, unchanged
price snapshots, duplicate confirmation, month/lifetime validity, ownership/roles,
bad webhook key, wrong receiver/amount/environment and late cancelled-registration
transfers without reactivation. A successful financial record for a late transfer
does not automatically restore a cancelled registration's access.

Frontend verification: TypeScript, lint, production build and role-route tests.
Browser payment scenarios use mocked APIs/QR imagery only: pending QR, successful
confirmation/My Courses, missing configuration, already-owned course, wrong role.
No live payment, production webhook or database migration is executed by these tests.
