# Student registration → Payment handoff

Scope: UC12/13 (create, own list/detail), UC26 package configuration; BR48–55.
Product Owner: ONE_MONTH (1 calendar month), ONE_YEAR (12 calendar months), LIFETIME.
Expert configures the price and discount percentage separately for all three tiers.
No payment gateway, transaction creation, callback or registration activation is implemented here.

## Run and schema

On an existing MySQL database, back up first, verify the existing V004–V006 schema,
then apply `database/migrations/V007__course_packages_student_registration.sql`.
It adds one package table and nullable registration snapshots, without changing old
rows or fabricating prices/terms. The script is rerunnable. It is not automatically
run by Flyway, and has not been applied to the user's MySQL database by this batch.
The automated tests use a separate H2 database with the `test` profile only.

Restart the backend after schema installation. For a published course, open it as
the owning Expert and save three package rows. Existing courses without an active
package remain visible but cannot be registered; public price is null rather than
inventing a package from legacy `Course.price`. No demo packages were inserted.
`Course.price` remains a legacy metadata field; payment must never use it.

## APIs available now

| API | Authorization | Contract |
| --- | --- | --- |
| GET /api/v1/expert/courses/{courseId}/packages | Owning Expert | All configured tiers, including inactive |
| PUT /api/v1/expert/courses/{courseId}/packages | Owning Expert; Draft/Rejected/Published | `{packages:[{duration,price,discountPercent,active}, ...]}`; exactly the three distinct durations |
| GET /api/v1/public/courses/{courseId} | Public | Published metadata and active `packages`; price is lowest active final price |
| POST /api/v1/student/registrations | Active Student | `{courseId,packageId,fullName,phone}`; email/Student are resolved from login |
| GET /api/v1/student/registrations?page=0&size=20 | Active Student | Own paginated `content`, `totalElements`, `totalPages` |
| GET /api/v1/student/registrations/{id} | Owning Student | Stored financial/access/contact snapshots; another Student gets 404 |

New registration: HTTP 201 and Location header. Reopened pending registration:
HTTP 200, identical ID and price snapshots, `reused:true`.
The course row is pessimistically locked for creation and package updates to
serialize concurrent duplicate submissions and pricing changes.
An existing current ACTIVE registration (monthly/yearly/lifetime) returns 409.
An expired/cancelled/failed registration does not prevent a new registration.
An old pending registration is reopened even if a different tier was selected;
its snapshots are not silently changed. Legacy registrations lacking snapshots
remain legacy and must not be activated using guessed package terms.

Example create request:

```json
{"courseId":8,"packageId":12,"fullName":"Học viên","phone":"0912345678"}
```

Important response fields: `id` (=registrationId), `courseId`, `packageId`,
`packageName`, `packageDuration`, `durationMonths`, `lifetime`, `originalPrice`,
`discountPercent`, `discountAmount`, `amount`, `currency:"VND"`,
`status:"PENDING_PAYMENT"`, `validFrom:null`, `validUntil:null`, `reused`.
Requests cannot override amount, discount, Student ID, email or status.
Prices are non-negative integer VND, discounts 0–100% with at most two decimals.
Final amount is rounded HALF_UP to whole VND, then discountAmount=price-finalAmount.
A price/discount change after registration never modifies its snapshots.
A zero final amount still creates an unpaid registration and grants no access;
the payment module needs an explicit zero-cost policy before activating it.

## Integration steps for the payment teammate

1. Use the stored registration ID, validate ownership and PENDING_PAYMENT server-side.
   Recheck course availability and retained snapshots. Do not recalculate using
   current package prices or trust an amount supplied by the browser.
2. Initiate VNPAY from `CourseRegistration.amount`. Payment initiation route/UI are
   not present yet; attach that module to the Student registration detail screen.
3. Existing `Payment` is one-to-one with registration. Avoid duplicate Payment
   rows on retries; agree a schema change before introducing multiple attempts.
4. Validate signature, provider reference, outcome and exact amount in the callback.
   Make callback processing idempotent, including duplicate/out-of-order notifications.
   Check pending state/owner and avoid reactivating a cancelled registration.
5. In one short transaction update Payment + Transaction + registration to ACTIVE,
   set validFrom from the first successful payment time, and calculate validUntil:
   `RegistrationAccessTerms.validUntil(paidAt, validityMonthsSnapshot, lifetimeSnapshot)`.
   Months mean calendar months in UTC (Jan 31 + 1 month becomes Feb 28), not 30/365 days.
   Lifetime uses `lifetimeSnapshot=true` and validUntil=null, never a fabricated year 9999.
6. A failed/incomplete payment must not create access. Never reset validity or create
   a second access grant on a repeated callback. Retain financial history.

Media playback already recognizes an ACTIVE lifetime snapshot with validFrom set.
Pending lifetime registration has no access. Student lesson/quiz access must use
the same predicate: ACTIVE && validFrom<=now && (lifetimeSnapshot || validUntil>now).
No Student learning or registration cancellation endpoints were added in this batch.

## Frontend

Owning Expert: course editor → Gói giá và thời hạn → Lưu các gói giá.
Guest/Student: course detail → select tier → Đăng ký khóa học → contact confirmation.
Guest login/Google registration preserves selected course/package within the same
browser session. Confirmation opens `/student/registrations/{registrationId}`.
This screen explicitly says payment is not integrated; there is no fake Pay button
or fake success. Student can inspect their own registrations and stored prices.

## Verification

Tests cover package ownership/state/validation, snapshots/rounding, no Payment/access,
pending reuse, ACTIVE/lifetime rejection, expired renewal, unavailable/foreign tiers,
role/owner boundaries, forged amount/Student ID, retained-data deletion, public pricing,
and UTC calendar-month/lifetime calculation. Run `mvnw.cmd test` and FE lint/typecheck/build.
