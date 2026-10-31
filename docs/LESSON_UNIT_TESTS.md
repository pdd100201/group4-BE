# Lesson unit tests

These JUnit 5 + Mockito tests run without Spring Boot, a database, or a running server.
Authentication/ownership, course state and existing student progress are fixture preconditions, not LessonRequest inputs. HTTP security filters and persistence transactions remain covered by the existing integration tests.

| File | Coverage |
| --- | --- |
| ExpertLessonServiceTest | List/read; create/update in DRAFT and REJECTED; trimming; optional video; duplicate position; ownership; missing course/lesson; locked states; audit records; delete with/without retained student progress |
| LessonRequestValidationTest | Required title/content/position/active; title 255/256; content 8000/8001; position 0/1/negative/max; optional video; false active accepted |
| StudentLessonServiceTest | Active student permission; published course; current registration; active lesson outline; resume; completed progress; hidden/missing lessons; complete lesson; registration linkage; idempotent completion; expired access |

Request inputs: title, content, position, active, videoMediaId. Current production validation allows a title up to 255 characters and content up to 8000 characters. Video attachment validity is checked by MediaService and its failures are propagated by the lesson service.

Run from group4-BE with JDK 21:

```powershell
.\mvnw.cmd "-Dtest=ExpertLessonServiceTest,StudentLessonServiceTest,LessonRequestValidationTest" test
```

The tests use isolated fixtures, no database IDs or data are required. Assertions check outputs/errors and absence of save/delete/audit on denied operations. Existing integration tests are preserved.
