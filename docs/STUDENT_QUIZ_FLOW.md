# Student Quiz Flow

Scope: UC17 Take Quiz and UC18 Review Quiz Result, linked from UC16 lesson learning. Active lesson quizzes appear under their lesson; active course-wide quizzes appear under every lesson. Only quizzes with linked questions are listed.

## API

- GET `/api/v1/student/quizzes/{id}`: settings and the current student's attempt history.
- POST `/api/v1/student/quizzes/{id}/attempts`: start or resume an unsubmitted attempt; enforce maximum attempts.
- GET `/api/v1/student/quiz-attempts/{id}`: ordered questions and saved selections. No correct IDs, correctness flags, explanations or score are sent before submission.
- PUT `/api/v1/student/quiz-attempts/{id}/answers`: save one question's selected options.
- POST `/api/v1/student/quiz-attempts/{id}/submit`: grade once and return result. Repeated submission returns the same result.
- GET `/api/v1/student/quiz-attempts/{id}/result`: review only the student's own completed attempt.

Every taking/saving operation requires an enabled Student, an active quiz, Published course, active linked lesson where applicable, and current ACTIVE registration. Attempt and option ownership are checked server-side. User-row locking serializes concurrent starts and writes. Duration and pass-rate settings are snapshotted at start and persisted through Hibernate schema update.

The server deadline is authoritative. Expired attempts are finalized with saved answers; late request selections are ignored. Single Choice allows at most one selected option; Multiple Choice earns its question points only for an exact correct set. Scores are weighted by question points and rounded to two decimals. Correct answers and explanations are exposed only in a submitted result. Existing Expert retention guards protect questions, options and quizzes once an attempt exists.

## Browser Flow

Open a lesson, select a lesson/course quiz, start, choose answers, move between questions, submit with confirmation, then view score/pass status, answer comparisons and explanations. Responses are autosaved and restored on reload. The timer uses server time, and expiry triggers submission. Result filters show All/Correct/Incorrect, with links to retake subject to attempt limits or return to learning.

Validation includes backend integration tests for weighted grading, exact multiple choice, resume/limits, expiration, ownership, foreign options, immutable submission and pre-submit answer confidentiality. Desktop/mobile browser tests verify actual MySQL save/reload/submit/results and layout. Only browser-test attempts were removed afterward so the demo Student retains all test attempts.
