# SePay-only QR payment (UC70)

The user replaced the earlier VNPAY-only requirement with SePay bank-transfer QR.
No card checkout or external payment redirect remains. No new test classes were
written, as requested. Existing financial history is retained.

Official references:
- https://docs.sepay.vn/test-mode.html
- https://developer.sepay.vn/vi/sepay-webhooks/tich-hop-webhook
- https://developer.sepay.vn/vi/sepay-webhooks/xac-thuc
- https://developer.sepay.vn/vi/tien-ich-khac/tao-qr-code

## Configure the owner's backend

1. Register/login at https://my.sepay.vn and enable Test Mode. Create/select a
   bank account in this mode. Its bank name and root account must match webhook
   `gateway` and `accountNumber`, not a different Live account.
2. Edit `group4-BE/sepay-local.properties` (already created, ignored by Git).
   Set `sepay.bank`, `sepay.account-number`, `sepay.account-holder` and
   `sepay.webhook-api-key`. Use a random webhook API key of at least 32 characters.
   This is the webhook authentication key, NOT the API token for querying SePay.
   The matching key must be entered in the SePay webhook configuration.
   No key is returned to FE or put into a QR.
3. If the bank requires VA, set `sepay.qr-account` to the VA; the root receiving
   account remains `sepay.account-number`. SePay must send that VA in `subAccount`.
   VietinBank needs `sepay.description-prefix=SEVQR`. Banks using TKP/content VA
   need their documented prefix in this field and a tested matching VA setup.
   Do not guess bank-specific routing; use SePay's instructions for your account.
4. Keep `sepay.mode=test` for demos. Restart Spring Boot (working directory can be
   the BE directory or its parent). DB/SMTP/Google configuration is not changed.
5. Run `ngrok http 8080`. Configure a JSON webhook in SePay **Test Mode**:
   - Event: money received (`in`).
   - Bank account: the same account selected above.
   - URL: `https://serotonin-bonehead-viscous.ngrok-free.dev/api/v1/public/payments/sepay/webhook`
   - Authentication: API Key. Enter the exact key from step 2.
   - Initially avoid filters that would drop the generated payment code.
6. Configure payment code prefixes `OLST` (test) and `OLSL` (live), followed by
   digits, if using SePay's payment-code parser/filter. Backend also extracts the
   exact code from transfer content when `code` is null. The content must contain
   exactly one matching code, not an altered ID or conflicting parsed `code`.

Environment overrides: `SEPAY_MODE`, `SEPAY_BANK`, `SEPAY_ACCOUNT_NUMBER`,
`SEPAY_ACCOUNT_HOLDER`, `SEPAY_QR_ACCOUNT`, `SEPAY_DESCRIPTION_PREFIX`,
`SEPAY_WEBHOOK_API_KEY`. Credentials belong only on the shared BE owner's machine.
The old ignored local VNPAY credential file is no longer loaded; remove it yourself
when no longer needed. No user secret was read, displayed or overwritten.

## Manual Test Mode flow (no real money)

1. Open a demo course, select its package and confirm Student contact information.
2. On the separate payment page choose **Hien ma QR thanh toan**.
3. Copy the **exact** amount and transfer content shown on the page.
4. In SePay Test Mode use **Mo phong giao dich** (money in), or **Mo phong tao ma
   VietQR** and its simulated transfer action, using the same test account, amount
   and content. Test Mode supports amounts from 1,000 to 499,000,000 VND.
5. SePay sends the webhook. The website polls server-side status and shows test
   confirmation. Successful test registration can be used for local access demos,
   and confirmed `SEPAY_TEST` payments are included in this project's demo revenue
   reports and CSV exports, as requested. Report totals therefore include simulated
   payments and are not a statement of real bank receipts.
6. Wrong key/account/amount never grants access. Replay the same webhook to check
   idempotency: validity and successful Payment must not change.

DO NOT scan Test Mode QR with a real bank app or transfer real money for this demo.

### Five-minute payment deadline

The payment deadline is five minutes after the first QR is created, based on BE
time. Reusing a QR or refreshing the page does not extend it. Status reads and
payment confirmation enforce the deadline; a background sweep runs every 15
seconds (100 overdue requests per batch), including when the browser is closed.
An overdue pending SePay payment becomes `CANCELLED`, its attempt is retained as
`SEPAY_EXPIRED`, and its pending registration becomes `CANCELLED` without access.
The user can register again, creating a new registration and payment code.
Already confirmed payments are not expired. Existing old pending SePay QRs also
use their original creation time and will expire on the next check/sweep.

The frontend displays a server-aligned countdown and removes the QR at zero.
The bank-transfer QR itself cannot be revoked at the bank. A transfer arriving
after the deadline, including a delayed webhook after cancellation, is rejected
for automatic activation with HTTP 409 and needs manual reconciliation; no
automatic refund is implemented. Do not pay using an expired code or create a
second payment if money was already transferred but confirmation is missing.

Test QR data must come from the test account. A QR is not a payment confirmation.
The website has no button that allows a Student to mark their own payment paid.

## Live mode (explicit opt-in only)

Set `sepay.mode=live`, use a connected Live bank account and a separate Live webhook
key/configuration, then restart. Live QR transfers are real money. Never reuse
test credentials for Live or switch modes during a pending transfer. Test and Live
have different payment-code and provider-reference namespaces.
There is no automatic refund. Wrong amount or an unexpected transfer needs manual
reconciliation in SePay; consult webhook logs for a 409/error. An already-cancelled
registration is not reactivated even if a matching transfer is received.

## Team without deployment

The owner keeps BE + database + ngrok running. FE team members run `npm ci`, then
`npm run dev:team`. They use the same BE/database and need no SePay secret locally.
`npm run dev` keeps the original local settings. Update `OLS_TEAM_API_URL` and the
webhook URL if ngrok changes. Keep normal JWT/CORS protection enabled.

## Implementation and retained data

- POST/GET `/api/v1/student/registrations/{id}/payment`: active owning Student only;
  amount and validity come from the stored registration, never the browser.
- POST `/api/v1/public/payments/sepay/webhook`: authenticated API Key, JSON payload,
  exact receiving bank/root account/VA, positive integer amount and payment code.
- One Payment per registration and one reusable pending SePay transaction.
- Receiver details and mode are snapshotted in the existing transaction payload.
  No schema migration or historical data deletion is required.
- Successful reference is `SEPAY_TEST:<event-id>` or `SEPAY:<event-id>` in the
  existing unique Payment.providerReference column. Course/registration locks,
  fresh state after locks and the unique reference prevent duplicate grants.
- Unmatched/outgoing transactions are acknowledged but do not activate anything.
  Partial or overpayment is not silently treated as success.
- Access begins at the verified webhook transaction time (Vietnam local time
  converted to UTC), with calendar-month or lifetime terms from the snapshot.
- Old completed payments remain untouched. Old unpaid VNPAY attempts can be
  replaced only after every previous request has expired and no reference/outcome
  was recorded. Their transaction records remain retained, with no legacy callback.

Real SePay end-to-end verification still requires the bank configuration and webhook.
