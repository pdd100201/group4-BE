# Expert course CRUD

Scope: UC24 (list/view), UC25 (create), UC26 (edit), UC28 (delete).
Rules: SRS 2.5.6–2.5.9; BR18, BR26, BR27; product decisions C05, C06.

- `GET /api/v1/expert/courses`: owner-only database pagination; search/title, exact category, status; page defaults to 0, size defaults to 10 (maximum 100).
- `GET /api/v1/expert/courses/{id}`: owner-only details.
- `POST /api/v1/expert/courses`: validated title, description, category and nonnegative price; actor becomes owner and state is Draft.
- `PUT /api/v1/expert/courses/{id}`: same fields; only owned Draft/Rejected courses; client cannot set owner or workflow state.
- `DELETE /api/v1/expert/courses/{id}`: only owned Draft/Rejected courses; blocks records referenced by registrations, lessons, questions or quizzes. No cascading removal of learning records.

Writes record audit events. Update/delete and review transitions lock the course row to prevent concurrent state changes. Role enforcement uses existing expert URL security.

Frontend: `/expert/courses`, `/expert/courses/new`, `/expert/courses/:courseId`; actual APIs through TanStack Query. Includes loading/error/empty states, pagination, status badges and delete confirmation.

The editor covers basic metadata only. Thumbnail upload, rich-text editing, CoursePackage pricing and curriculum CRUD are not implemented in this slice. The existing Course.price field is retained for compatibility; it is not a substitute for course package pricing. The editor does not expose submit/resubmit until the content editor and its readiness validation are complete.

Existing workflow corrected to distinguish Draft submit from Rejected resubmit and approve directly to Published (C08/C09). Complete learning-content readiness and revision enforcement remain follow-up work on that existing workflow.

Verification: `ExpertCourseIntegrationTests` uses the isolated H2 test profile, transactional rollback and MockMvc; covers CRUD, filters, ownership, status locking, retained references, role restrictions and request validation. No development MySQL records are created or deleted by these tests.
