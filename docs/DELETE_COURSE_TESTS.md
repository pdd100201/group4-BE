# UC28 — Delete Course

`DeleteCourseTest.java` contains 33 cases exported to the Report5.1 workbook:

- **EXPERT (12 cases):** Draft/Rejected success, another owner's course,
  missing course, Pending Review/Published/Archived/Approved state rejection,
  and retained registration/lesson/question/quiz rejection.
- **MODERATOR (15 cases):** Manager/Admin success; Expert/Student/missing
  account rejection; null/empty/blank reasons; 1/255/256-character boundaries;
  missing course; Pending Review/legacy Approved rejection; Draft success.
- **RETENTION (6 cases):** retained CourseRegistration, QuizAttempt,
  StudentLessonProgress, QuizAnswer, QuizAnswerOption and cross-course
  QuizQuestion references independently prevent hard deletion.

Use the group plus UTCID to identify a case. Role, login identity, ownership,
existing state and retained references are **preconditions**, not request inputs.
Expert request input is courseId only; moderator input is courseId plus reason.

All tests use mocked fixtures. Expert id3 / Admin id1 / Manager id2 / Student id4
and sample course IDs follow the previously inspected development DB identities;
course states, reference counts and asset id1001 are simulated. No live database
DELETE or Cloudinary call is made.

Success asserts the deletion query sequence, media detachment, package deletion,
course deletion last, persistence-context clear/flush, audit actor/target/action/time,
and trimmed moderator reason. Tests verify SQL calls through mocks, not actual
database row removal. Integration tests remain necessary for FK behavior and rollback.

Failure asserts the exact ApiException status/message, preserved metadata/state,
no mutation query, no clear/flush, and no audit write. A retained-reference conflict
does not automatically archive the course: the user must invoke UC27 separately.
HTTP filter permissions are covered separately by integration tests; the Expert
service checks ownership and state but does not itself execute the security filter.

Run:

```powershell
.\mvnw.cmd '-Dtest=DeleteCourseTest' test
```

The test exports exact inputs and per-case PASS flags to
`target/delete-course-sheet-cases.json`. The workbook renderer reads this export,
using the original template sheets `createAccount`, `methodName2` and `methodName3`
for Expert, Moderator and Retention respectively. English descriptions retain the
original Vietnamese error literals where exact comparison with production is needed.

Verified on 19/10/2026: **33/33 UC28 cases passed**; **72/72 tests passed** when
run with `ExpertCourseServiceTest` and `CourseModerationServiceTest`.
