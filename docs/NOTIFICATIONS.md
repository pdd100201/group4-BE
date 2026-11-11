# In-app notifications

Branch: hai. New notifications are generated for events occurring after this change; no historical payments or enrollments are backfilled.

## Where to find them
- Bell in the public signed-in header, Student course portal, lesson player and staff topbar.
- Bell opens the five most recent notifications, with an unread badge and a link to /notifications.
- /notifications supports pagination, unread-only and category filters, individual read and mark-all-read.
- Polling refreshes the badge and inbox every 30 seconds while the page is active. There are no browser push, email or SMS deliveries.

## Events
| Event | Recipients |
| --- | --- |
| New unpaid registration | Student (pending-payment wording), owning Expert |
| Validated successful SePay payment | Student, owning Expert, enabled Admins |
| QR expiration detected by existing payment-status/expiration flow | Student |
| Validated incoming bank callback with incorrect amount, expired QR, out-of-window payment or cancelled registration | Admins, for manual reconciliation |
| Course submission/resubmission | Managers |
| Course approval/rejection | Owning Expert |
| Course archived/restored | Owning Expert and currently entitled Students |
| New lesson question | Owning Expert |
| Expert answer | Question author |
| Blog submitted/reviewed | Managers / author |

Financial test events carry [TEST], and Admin receipt notifications explicitly say they are not real revenue.
The application has no outgoing expense/refund/payout workflow; no expense notification is fabricated from incoming payments or ignored outgoing callbacks.

## Delivery and permissions
- Notification rows are written inside the business transaction. Failed/rolled-back actions leave no success notification.
- Reconciliation alerts use an independent transaction because the rejected payment confirmation rolls back. They do not activate access or post revenue.
- Unique recipient/event keys and a database upsert prevent duplicate callback notifications and preserve existing read state.
- Notifications are addressed to individual persisted accounts, not client-selected recipients. APIs read the current authenticated user; another user's ID cannot be marked read.
- Mark-all-read takes the latest ID the client has seen, preserving newer notifications arriving during the operation.
- Notification text is a display snapshot, not an authorization grant. Destination pages still enforce their existing course/role access.
- Disabled/blocked recipients are skipped.
- Lesson questions and the remaining retained course data prevent destructive course deletion.
- Chat messages have their existing chat unread state; this module does not duplicate every chat message.

## API
Authenticated endpoints:
- GET /api/v1/notifications?unreadOnly=false&category=all&page=0&size=20
- GET /api/v1/notifications/summary
- PUT /api/v1/notifications/{id}/read
- PUT /api/v1/notifications/read-all {throughId}


Categories: COURSE, PAYMENT, DISCUSSION, REVIEW (all for no filter).

## Database and deployment
The current notification module uses `user_notifications`. Course-wide announcements and their table have been removed.
Hibernate uses `ddl-auto: validate`; apply the agreed database changes before starting the updated backend. No existing financial records are rewritten.
Restart/reload FE as appropriate for the Vite dev server or production preview.

## Validation
NotificationIntegrationTests covers recipient/ownership rules, pending versus paid events, duplicate callbacks, expired/mismatched payments, read privacy, read-all cutoff, disabled recipients, rollback, filtering/pagination, course state and history retention.
Regression suites: LessonDiscussionIntegrationTests, CourseWorkflowServiceTest, CourseModerationServiceTest, ManagerPostServiceUnitTests, UpdateCourseStatusTest, DeleteCourseTest.
Frontend: TypeScript noEmit and Vite production build.

Manual check:
1. Student registers: Student/Expert receive pending notices, no Admin revenue notice.
2. A valid test payment: Student access notice, Expert activation notice, Admin [TEST] receipt.
3. Student asks a lesson question; Expert replies: each participant receives the appropriate notice.
4. Open the bell and full inbox, follow a link and verify unread counts; sign in with another role and verify isolation.

Verified on 2026-11-02: 160 tests passed (16 notification tests plus 144 regression tests). TypeScript noEmit and production build passed. Browser interaction and MySQL migration execution have not been verified in this environment.
