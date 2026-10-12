# Course moderation — UC27/UC28

## Implemented

- Admin/Manager: `POST /api/v1/{admin|manager}/courses/{id}/archive`, body `{ "reason": "..." }`, Published → Archived.
- Admin/Manager: existing `POST .../{id}/restore`, Archived → Published.
- Admin/Manager: `DELETE /api/v1/{admin|manager}/courses/{id}`, same reason body, returns 204.
- Reasons are mandatory, non-whitespace, maximum 255 characters. Each successful action retains actor, target ID, reason and timestamp in AuditLog.
- Archive/restore retain course content and registration/payment/learning records. Archived courses disappear from the public catalog.
- Physical deletion is blocked with 409 for registrations (including payment/transaction/report sources), quiz attempts, lesson progress, quiz answers, selected answers and cross-course question references. No retained records are deleted.
- Physical deletion of Draft/Rejected/Published/Archived courses with no retained references removes learning content in dependency order. Pending Review and legacy Approved cannot be deleted through this API.
- Cloudinary assets are not remotely deleted; only their course association is cleared.
- Admin metadata lists never expose quiz answers or grant approval/rejection rights. Expert cannot invoke moderator endpoints.

## UI

Sidebar **Trạng thái khóa học**:

- `/admin/courses/archived`
- `/manager/courses/archived`

Choose Published or Archived, then Hide/Restore/Delete. All actions require a reason modal; Delete warns that it cannot be undone. Retention errors preserve the reason in the dialog. Uses existing course tables and modal styling, no new UI framework.

## Verification

`CourseModerationIntegrationTests`: both moderator roles, reasons, invalid state, missing course, forbidden role, retained registration/quiz attempt/progress, public visibility and deletion of a Published course containing lessons/questions/quiz.

`CourseRestorationIntegrationTests`: restoration and audit, invalid reasons, unauthorized roles and duplicate restoration.

Tests use isolated H2, not the development MySQL database. MySQL runtime behavior and browser visual QA are not yet verified by these tests.

## Remaining Course slices

- CoursePackage pricing: 3 months / 6 months / lifetime, Expert-entered discount percentage.
- Submit readiness: active lessons with video, active quiz and required course/package data; no minimum five lessons.
- Require actual revisions before resubmitting a rejected course.
- Expert deletion reason modal/API (current Expert delete flow is unchanged by this moderator batch).
- Registration package snapshots belong to the registration/payment integration; no payment or reporting flow is added here.

No schema changes, production data deletion, commit or push performed in this batch.
