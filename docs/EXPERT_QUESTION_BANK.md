# Expert Question Bank

Source: Software Requirement Specification (4).docx, question management flows 2.5.16-2.5.20, Question Bank mockup and BR-32-37.

## Workflow

Open `/expert/questions`, select an owned course, then use the question list and right-hand editor. Course editing also links directly to questions and quizzes.

The list supports content search, Single Choice/Multiple Choice type, active status and database pagination. Four distinct answer options are required. Single Choice has exactly one correct answer; Multiple Choice has at least one. Explanation and a course-scoped uploaded image are optional. Formatting is safely previewed without executing HTML scripts.

Mutation follows the existing lesson/quiz content policy: owning Expert, Draft or Rejected course. Submitted/published courses are read-only. Retained answers/attempts prevent mutation; any linked quiz prevents deletion, and an active quiz prevents deactivation of its question. Actions are audited.

## API

Base: `/api/v1/expert/courses/{courseId}/questions`

- GET: paginated list (`search`, `type`, `active`, `page`, `size`).
- POST: create.
- PUT `/{id}`: update.
- PATCH `/{id}/status`: change `active`.
- DELETE `/{id}`: delete an unreferenced question.
- GET `/template`: download XLSX sample and instructions.
- POST `/import`: multipart XLSX, maximum 5 MB and 500 question rows.

Excel columns: `content`, `type`, `optionA`, `optionB`, `optionC`, `optionD`, `correct`, `explanation`, `active`.
Correct answers use `A` or comma-separated letters such as `A,C`. Type is `SINGLE_CHOICE` or `MULTIPLE_CHOICE`; active is `true` or `false`. Formula cells and malformed files are rejected. Valid rows are persisted in one transaction; invalid rows are returned with Excel row numbers. No data is persisted during parsing, and database failures roll back the valid batch.

Media uploads use `QUESTION_IMAGE` and ownership checks through the existing media service. Hibernate development schema update adds the optional question image association. Question and option timestamp mappings match the existing MySQL schema; legacy `question_type` and `status` are kept synchronized when writing.

## Validation

Backend integration tests cover CRUD, filtering, ownership, role restrictions, state restrictions, correct-answer validation, retained references, and mixed-validity imports. Real MySQL checks cover create/update/status/delete and template/import. Browser checks cover desktop/mobile creation, status, deletion, template download and horizontal overflow. Frontend build, lint, typecheck and existing quiz checks pass.
