# Manager course review

Scope: UC24 (Manager list), UC67 (approve), UC68 (reject), read-only learning-content preview for review. SRS BR18, BR21, BR23; product decisions C06, C07, C08.

Frontend:
- `/manager/courses/review`: defaults to Pending Review, with search/category/status filtering and database pagination.
- `/manager/courses/:courseId/review`: basic metadata, Expert name, lessons, quiz questions/options/answers, workflow decision and reviewer details.
- Manager cannot edit course/lesson/question/quiz content. Approval and rejection require confirmation. Rejection requires a nonblank reason of up to 255 characters.

API:
- `GET /api/v1/manager/courses`: missing status means Pending Review; explicit empty status means all states. Search title, exact category, page >= 0, size 1–100.
- `GET /api/v1/manager/courses/{id}`: read-only course content DTO; no User entity or credentials returned. Correct answers are restricted to Manager review endpoints.
- Existing `POST /api/v1/manager/courses/{id}/approve`: Pending Review -> Published directly (C08), stores reviewer, clears prior rejection reason, records audit.
- Existing `POST /api/v1/manager/courses/{id}/reject`: Pending Review -> Rejected, stores trimmed reason, reviewer and audit.

Manager role is enforced by existing URL security and checked again in the decision service. Both decisions use the course row lock; a repeated or stale decision returns 409. Decisions cannot be applied to Draft, Rejected, Published or archived records.

Testing: ManagerCourseIntegrationTests uses the isolated H2 profile and rollback. Covers list/filter/pagination, preview, no content mutation, approval/public visibility/audit, rejection validation/Expert feedback, stale decisions, forbidden roles and missing records.

Prerequisite: the course must already have been submitted through the Expert submit API. Expert's complete curriculum/package editor and submit UI remain the follow-up slice described in EXPERT_COURSE_CRUD.md. This change does not create pending courses, change development data, or bypass submission readiness for a demo.

Limits: pricing preview uses the current Course.price field, not CoursePackage. Thumbnail/package preview will be added with those content features. Rendering is read-only plain text, not executable HTML from lesson content.
