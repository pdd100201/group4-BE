# Expert Quiz Management

Source: Software Requirement Specification (4).docx, sections 2.5.21-2.5.24 and BR-38-42.
Owner clarification: omit level; quiz type is derived from the owning course category.

- UC40: GET /api/v1/expert/quizzes searches the owner's quizzes with course, category, active status and database pagination.
- UC41-43: existing course-scoped CRUD remains ownership/state protected. Saving returns to the course quiz list.
- GET /api/v1/expert/courses/{courseId}/quizzes/question-bank selects active questions from the owned course, with search and pagination.
- Active quizzes require duration, pass rate and attempt settings. Existing attempt retention still prevents edit/delete.
- Quiz and QuizQuestion timestamps match the existing MySQL non-null columns.

Verification: backend tests, frontend build/typecheck/lint, quiz render checks, real MySQL create/update/delete with question links, desktop/mobile browser checks.
