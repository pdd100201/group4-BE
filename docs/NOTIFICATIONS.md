# In-app notifications

Branch: hai. New notifications are generated for events occurring after this change; no historical payments or enrollments are backfilled.

## Where to find them
- Bell in the public signed-in header, Student course portal, lesson player and staff topbar.
- Bell opens the five most recent notifications, with an unread badge and a link to /notifications.
- /notifications supports pagination, unread-only and category filters, individual read and mark-all-read.
- Expert course editor: **Thông báo cho học viên**, including sent history and recipient counts.
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
| Expert course announcement | Enabled, unblocked Students with current course access |

Financial test events carry [TEST], and Admin receipt notifications explicitly say they are not real revenue.
The application has no outgoing expense/refund/payout workflow; no expense notification is fabricated from incoming payments or ignored outgoing callbacks.

## Delivery and permissions
- Notification rows are written inside the business transaction. Failed/rolled-back actions leave no success notification.
- Reconciliation alerts use an independent transaction because the rejected payment confirmation rolls back. They do not activate access or post revenue.
- Unique recipient/event keys and a database upsert prevent duplicate callback notifications and preserve existing read state.
- Broadcast request UUIDs are retained on frontend retry; the course lock serializes submissions. Same UUID/body returns the previous result; different body is rejected.
- Course announcements require the owning enabled Expert and a published course.
- Notifications are addressed to individual persisted accounts, not client-selected recipients. APIs read the current authenticated user; another user's ID cannot be marked read.
- Mark-all-read takes the latest ID the client has seen, preserving newer notifications arriving during the operation.
- Notification text is a display snapshot, not an authorization grant. Destination pages still enforce their existing course/role access.
- Disabled/blocked recipients are skipped. Announcement counts exclude them and expired/pending/cancelled registrations.
- Announcement history and lesson questions prevent destructive course deletion.
- Chat messages have their existing chat unread state; this module does not duplicate every chat message.

## API
Authenticated endpoints:
- GET /api/v1/notifications?unreadOnly=false&category=all&page=0&size=20
- GET /api/v1/notifications/summary
- PUT /api/v1/notifications/{id}/read
- PUT /api/v1/notifications/read-all {throughId}

Expert course endpoints:
- GET /api/v1/expert/courses/{courseId}/announcements?page=0
- POST /api/v1/expert/courses/{courseId}/announcements {requestKey,title,message}

Categories: COURSE, PAYMENT, DISCUSSION, REVIEW (all for no filter).
Announcements: title 200 characters, body 1000; plain text.

## Database and deployment
V017__user_notifications.sql adds user_notifications and course_announcements. No existing financial records are rewritten.
Development currently uses Hibernate update; restart BE so the mappings are created. If automatic schema update is disabled, apply the migration to the configured MySQL database before deployment.
The migration was not executed against the user's local database during implementation.
Restart/reload FE as appropriate for the Vite dev server or production preview.

## Validation
NotificationIntegrationTests covers recipient/ownership rules, pending versus paid events, duplicate callbacks, expired/mismatched payments, read privacy, read-all cutoff, disabled recipients, announcement retries, rollback, filtering/pagination, course state and history retention.
Regression suites: LessonDiscussionIntegrationTests, CourseWorkflowServiceTest, CourseModerationServiceTest, ManagerPostServiceUnitTests, UpdateCourseStatusTest, DeleteCourseTest.
Frontend: TypeScript noEmit and Vite production build.

Manual check:
1. Student registers: Student/Expert receive pending notices, no Admin revenue notice.
2. A valid test payment: Student access notice, Expert activation notice, Admin [TEST] receipt.
3. Expert sends an announcement inside the published course: only currently entitled Students receive it.
4. Student asks a lesson question; Expert replies: each participant receives the appropriate notice.
5. Open the bell and full inbox, follow a link and verify unread counts; sign in with another role and verify isolation.

Verified on 2026-11-02: 160 tests passed (16 notification tests plus 144 regression tests). TypeScript noEmit and production build passed. Browser interaction and MySQL migration execution have not been verified in this environment.
