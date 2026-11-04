# Lesson discussions

Implemented on branch hai.

## Screens
- Student: open an enrolled course, select a lesson, then choose **Hỏi đáp**.
- Expert: **Hỏi đáp bài học** in the sidebar (`/expert/discussions`).
- Manager/Admin: **Hỏi đáp bài học** in the sidebar for moderation.

## Rules
- Only enabled, unblocked accounts can use discussions.
- Students must have a currently active course registration. Expired registrations cannot read old discussions, including their own.
- Student access requires a published course and active lesson.
- Active students can ask questions and read answers. Only the owning Expert can reply; Student self-replies and peer replies are denied by the API.
- Questions require a title (200 characters maximum) and body (4000 maximum); replies allow 4000 characters. Text is plain text, escaped by React.
- Experts see only their own courses, including archived courses, and can reply and select an accepted answer.
- Question authors can also accept a visible answer or reopen the question. An answer from another question cannot be accepted.
- Managers/Admins can inspect, hide and restore questions and answers. Hidden questions are inaccessible to students and experts; hidden replies are excluded from their pagination/counts.
- Hiding an accepted answer clears the resolution. Restoring it does not automatically resolve the question again.
- The question list shows summaries only. Selecting a row opens the question detail; replies are fetched only for the selected question. Search, status and list page are preserved in URL parameters qaSearch, qaState and qaPage; qaQuestion identifies the selected question. Returning to the list preserves these filters. Question/reply pagination includes page numbers and previous/next controls.
- Question mutations lock the parent row to serialize moderation and resolution.
- Search matches literal case-insensitive title/body substrings; SQL wildcard characters are escaped.
- Pagination: 20 questions/replies per page. Filters: all, unanswered, open, resolved. Unanswered excludes hidden replies.
- No notification, attachment or editing feature is included in this module.

## Database
Use `database/migrations/V016__lesson_discussions.sql` on the configured MySQL database before using this feature when schema auto-update is disabled. It adds two tables and references existing signed BIGINT lesson/users IDs.
Local development currently uses Hibernate update, which creates the new mappings after restarting BE. The migration is repeatable for tables/FK already created by Hibernate.
No existing learning, quiz or payment data is changed. Database migration was not run against the user's local database during implementation.

## API
Authenticated base path: `/api/v1/discussions`.
- GET /?lessonId=&search=&state=&page= (lessonId required for Student; staff inbox otherwise)
- POST / {lessonId,title,content}
- GET /{id}?page= (question and paginated replies)
- POST /{id}/answers {content}
- PUT /{id}/resolution {answerId} (null reopens)
- PUT /{id}/visibility {hidden}
- PUT /{id}/answers/{answerId}/visibility {hidden}

Sender identity comes from Authentication; permissions are checked from persisted account/registration data, not request fields.
DTOs expose display names and IDs only, not account email/password.

## Verification
`mvnw.cmd -Dtest=LessonDiscussionIntegrationTests test`
`npm exec tsc -- --noEmit`
`npm run build`

Integration tests use H2, MockMvc and a mocked unrelated chat-media provider. On environments that forbid dynamic agent attachment, run Mockito with a preloaded Byte Buddy Java agent.
Manual flow: create a question as an enrolled Student, reply from the owning Expert inbox, accept the answer as Student, then hide/restore it as Manager. Verify both desktop and mobile layouts.

Verified: 10 integration tests passed; frontend TypeScript and production build passed. Browser/manual verification and MySQL migration execution remain to be done.
