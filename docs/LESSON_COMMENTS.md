# Lesson comments

Apply `database/migrations/V021__lesson_comments_and_likes.sql` to the configured
MySQL database after taking a backup. It adds two tables without changing existing data.

The student lesson player has a separate **Bình luận** tab. Students with current
course access can read/post comments, reply to a top-level comment and like/unlike
both comments and replies. Replies are one level deep. Each list uses pages of 20;
comments show newest first, replies oldest first. The frontend refreshes visible
lists every 30 seconds and after writes.

All endpoints require an enabled, unblocked Student, an active lesson in a published
course, and an ACTIVE registration valid now (or lifetime access). These checks
run on every read/write. A reply or like target must belong to the requested lesson.
This feature does not change instructor Q&A.

Authors can edit/delete their own comments and replies while their course access
is active. Apply `V022__lesson_comment_edit_delete.sql` for existing databases.
Editing retains likes/replies and shows an edited label. Deleting a root comment
clears and hides the entire thread, including all replies. Deleting a reply only
clears/hides that reply and updates the parent's reply count. Deleted comments and
replies cannot be listed, liked or edited. The UI asks for confirmation with this scope.

API prefix: `/api/v1/student/lessons/{lessonId}/comments`

- `GET ?page=0`: top-level comments; add `parentId` to page through replies.
- `POST {"content":"...","parentId":null}`: create a comment or reply (max 2,000 characters).
- `PUT /{commentId}/like {"liked":true}`: set like state; false removes it.
- `PUT /{commentId} {"content":"..."}`: edit one's own comment/reply.
- `DELETE /{commentId}`: delete one's own comment and its replies, or one's own individual reply (204).

Likes are unique per comment/student. A comment row lock and READ COMMITTED
transaction isolate concurrent requests and preserve idempotency on MySQL.
Author responses include public name/avatar only; text is rendered as plain text.

