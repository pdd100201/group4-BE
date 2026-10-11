# Manual testing data for UC44-48

Local development fixture, not a production migration and not a real VNPAY payment.
Run seed_management_demo.sql only against the local online_learning_system database,
after V006 and after creating the existing Student and Expert demo accounts.

The script inserts 3 demo courses, 60 registrations, 60 simulated payments and
60 transactions. All rows are associated with identifiable DEMO titles, notes,
provider references or transaction types. Existing rows are not updated/deleted.
A SQL exception rolls back the data transaction. Rerunning does not duplicate it.

## Try in the browser

Log in as manager@onlinelearning.local (default demo password Online@123), or Admin.
Open /manager/registrations and /manager/transactions (Admin uses /admin).
Refresh the list if it was already open.

- Default 20-row pagination: 60 records, 3 pages.
- Search React: 20 registrations or transactions.
- Registration status Active: 12 records.
- Transaction status Succeeded: 24 records.
- Other registration statuses: 12 each (Pending Payment, Failed, Cancelled, Expired).
- Other payment statuses: 12 each (Pending, Failed, Cancelled).
- Search DEMO-ONLY on transactions to isolate provider references from this fixture.
- Use amount, course ID and UTC date filters to narrow results; try a nonsense query for empty state.
- Click a registration detail for package snapshots, discount, final cost and validity.
- Click a transaction detail for the linked registration/payment.
- Back to list preserves the filters and page.
- Student/Expert cannot access these management screens.

ValidFrom/ValidUntil are populated only for successful-payment Active/Expired
fixtures. Failed, cancelled and pending payments do not grant access.
These fixture courses are clearly marked DEMO and appear in the local public catalogue.
No gateway request, callback, refund or actual financial transfer is performed.

The database was backed up before the first local run. This script does not contain
automatic cleanup and does not delete user data.
