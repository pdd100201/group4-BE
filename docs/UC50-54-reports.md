# UC50-54 Admin reports

Scope approved in chat: proceed with Admin-only course, registration, revenue reports,
CSV export and the existing FE layout without the unavailable SRS mockups.
UC50 is View Reports (the spreadsheet labels it Dashboard); UC19 Dashboard is unchanged.
Manager reporting permissions remain unassigned. VNPAY integration is not implemented here.

## Contracts

- GET /api/v1/admin/reports: authorized report types.
- GET /api/v1/admin/reports/{COURSES|REGISTRATIONS|REVENUE}: database pagination and totals.
- GET /api/v1/admin/reports/{kind}/export: UTF-8 BOM CSV, all filtered rows, maximum 10000.
- Filters: courseId, from, to; status applies only to registrations. page/size apply to read.
- Dates use UTC, inclusive end date. Undated legacy records are excluded only when date filtered.
- Course report retains all courses, including zero-registration and archived records.
  Its registration count uses registration creation dates; revenue uses payment transaction dates.
- Registration totalAmount means registration charges, NOT successful payment revenue.
- Revenue sums Payment.amount where Payment.status is SUCCEEDED, once per payment.
  The earliest retained transaction ID provides its reporting date, since Payment currently
  has no confirmed-paid-at timestamp. This is a retained-data reporting convention,
  not a claim that the first transaction timestamp is a verified settlement time.
  Payments without transactions are not included. Period rules must be revisited when
  a verified settlement timestamp is introduced with payment integration.
- Demo values are included in local reports and marked DEMO; they are not actual revenue.
  The dataset is database/demo/seed_management_demo.sql (60 registrations, 24 successful payments).
- CSV preserves filter and permission scope, quotes all cells and neutralizes leading formulas.
- Roles are checked in both SecurityConfig and ReportServiceImpl. Payloads/secrets are excluded.

## Verification

ReportControllerTests runs with isolated H2; it does not modify local MySQL.
Covers retained courses, duplicate transactions, failed/pending exclusion, dates,
registration status, pagination, empty results, CSV quoting/formulas and denied roles.
Frontend structure is features/reports/api + screens with shared/hooks and shared/types.
