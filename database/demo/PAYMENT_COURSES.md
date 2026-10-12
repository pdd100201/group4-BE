# Local payment course fixture

After V007, run seed_payment_courses.sql against local online_learning_system.
The script uses an existing active expert@onlinelearning.local account and inserts:
- 3 PUBLISHED courses with the [DEMO PAYMENT] title prefix.
- 9 active packages: ONE_MONTH, ONE_YEAR and LIFETIME for each course.
- 6 text-only lesson outlines. No video or external media is fabricated.

Existing records are never updated/deleted. Rerunning skips matching demo records,
so Expert price edits are preserved. There are no automatic startup seeds.
The procedure serializes seed runs and rolls back its data transaction on failure.

## Manual flow

1. Open http://localhost:5173/courses and select a [DEMO PAYMENT] course.
2. Log in using a Student account, select a package and click registration.
3. Confirm Student name/phone. Backend creates PENDING_PAYMENT with retained snapshots.
4. The registration ID and saved amount are inputs for SePay QR payment; see `docs/SEPAY_SETUP.md`.

Monthly prices: Spring Boot 50000, React 100000, MySQL 150000 VND.
Yearly packages cost 5x the monthly price before a 10% discount.
Lifetime packages cost 10x the monthly price before a 15% discount.
The backend calculates and stores the final amount, not the frontend or Course.price.

This seed creates no registration, Payment or Transaction and grants no learning access.
The existing payment gateway is not implemented by this seed.
