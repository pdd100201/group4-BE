# UC27 — Update Course Status

`UpdateCourseStatusTest.java` contains two sets of 15 cases:

- **ARCHIVE:** `CourseModerationService.archive`, Published → Archived.
- **RESTORE:** `CourseRestorationService.restore`, Archived → Published.

Both sets use the same UTCID ordering. Prefix the UTCID with the action to
identify a unique case, for example `ARCHIVE / UTCID01`.

| UTCID | Input / precondition | Expected |
|---|---|---|
| 01 | Manager, valid reason and initial state | Transition + audit |
| 02 | Admin, valid reason and initial state | Transition + audit |
| 03 | Expert | 403, no mutation/audit |
| 04 | Student | 403, no mutation/audit |
| 05 | Actor account missing | 403, no course lookup |
| 06 | Null reason | 400 |
| 07 | Empty reason | 400 |
| 08 | Whitespace-only reason | 400 |
| 09 | Reason length 1 | Transition + audit |
| 10 | Reason length 255 | Transition + audit |
| 11 | Reason length 256 | 400 |
| 12 | Missing course id999999 | 404 |
| 13 | Current state Draft | 409 |
| 14 | Current state Pending Review | 409 |
| 15 | Already in the requested target state | 409 |

The identities (Admin id1, Manager id2, Expert id3 and Student id4) and course id4
come from the development database snapshot read on 19/10/2026. All data is
mocked during execution. Restore tests deliberately set the fixture to Archived;
they do not change the live Published record. Thumbnail id1001 is a mock asset.

Every case verifies preservation of title, description, category, price, owner
and thumbnail. Failure cases preserve the original state and never save an audit
entry. Success cases check action, actor, target, trimmed reason and timestamp.

These service tests do not prove HTTP security filter behavior, database locking,
transaction rollback or production code coverage. Submit/Approve/Reject are
UC66–69 and remain in the separate workflow tests. No production rules are changed.

Run:

```powershell
.\mvnw.cmd '-Dtest=UpdateCourseStatusTest' test
```

After execution, `target/update-course-status-sheet-cases.json` supplies the
Excel renderer with the exact case inputs and per-case PASS flags. The workbook
uses `methodName2` for Archive and `methodName3` for Restore to preserve the
Report5.1 template's sheet structure. Original Vietnamese application messages
are retained verbatim alongside English explanations for assertion accuracy.

Verified on 19/10/2026: **30/30 UC27 cases passed**; **83/83 tests passed** when
run with `CourseModerationServiceTest` and `CourseWorkflowServiceTest`.
