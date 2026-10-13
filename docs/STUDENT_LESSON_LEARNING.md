# Student Lesson Learning

Scope: UC16 protected lesson content and lesson completion progress. Student quiz taking remains a separate workflow.

Routes: `/student/courses/{courseId}/learn` resumes the first incomplete active lesson; `/student/courses/{courseId}/lessons/{lessonId}` opens a selected active lesson. Both the course detail page and My Courses now provide a working `Vao hoc` action.

API:

- GET `/api/v1/student/courses/{courseId}/learning`: outline, course progress and first incomplete lesson content.
- GET `/api/v1/student/courses/{courseId}/lessons/{id}`: selected lesson content.
- POST `/api/v1/student/courses/{courseId}/lessons/{id}/complete`: idempotently records completion and returns updated progress.
- Existing GET `/api/v1/media/{id}/playback`: protected signed video URL; answer information is never included in lesson responses.

Every request checks enabled Student role, Published course and an ACTIVE registration whose validity window includes the current time. Hidden lessons and mismatched course/lesson IDs are denied. Only active lessons contribute to progress. Completion is serialized on the student row and linked to the valid registration.

Local legacy database preflight found zero progress rows and foreign keys pointing to the unused plural `lessons` and `course_registrations` tables. Applied `V010__align_student_progress_references.sql` to remove those legacy references and link registration to the ORM's `course_registration`. No existing learning records were deleted.

Validation: full backend tests; frontend build/lint/typecheck; real MySQL idempotent progress write; real Cloudinary video playback with advancing video time in desktop/mobile browsers; lesson navigation and horizontal overflow checks. Test-created completion was removed so the demo student starts at zero progress.
