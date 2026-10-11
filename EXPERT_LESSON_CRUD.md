# Expert lesson CRUD

UC29–33: view/create/edit/display status/delete lessons. Ownership and content state: BR18, BR28–30; retained learning data is preserved.

API prefix: `/api/v1/expert/courses/{courseId}/lessons`.
- GET list and GET /{id}: owner-only, ordered by position then id.
- POST: title (1–255), content (1–8000), positive position and explicit active flag.
- PUT /{id}: edits the same fields, including display status.
- DELETE /{id}: confirmation in UI, rejects retained StudentLessonProgress.

Only owning Experts may mutate, and only for Draft/Rejected courses. Nested lesson identifiers must belong to the specified course. Course row locks serialize writes and prevent review transitions racing with lesson changes. Duplicate positions return 409; positions may have gaps after deletion. Audit events retain actor, lesson id and course id.

Frontend: curriculum section in the existing Expert course editor; real API via TanStack Query. Create/edit/view dialogs, ordered list, loading/empty/error/success states, hide/show selection and delete confirmation. Manager's existing read-only preview automatically receives the saved lesson content.

Content uses the existing plain-text Lesson.content field. Video/file upload, resource attachments and quiz-to-lesson association are outside this slice; no schema changes or fake upload controls.

Verification: ExpertLessonIntegrationTests runs on isolated H2 with transactional rollback; CRUD, ordering, status, audit, ownership, cross-course ids, course state locks, retained progress, duplicate positions, role and request validation.
