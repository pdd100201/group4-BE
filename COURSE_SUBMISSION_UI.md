# Course submission UI — UC66 / UC69

## How to use

Expert → Quản lý khóa học → Chỉnh sửa a Draft/Rejected course. Save changes first. In the sidebar **Gửi khóa học xét duyệt**, complete the checklist, then click **Gửi duyệt** (Draft) or **Gửi lại duyệt** (Rejected) and confirm.

Successful requests change status to Pending Review, not Published. Manager then uses the existing review workspace to approve/publish or reject. Course and lesson editing are locked during Pending Review.

## Shared backend checks

- Name, category and description are nonempty.
- Thumbnail is present.
- Current course price is nonnegative.
- At least one lesson; all lessons Active, with title/content/positive order and attached video media.
- At least one quiz; all quizzes Active, with title, positive duration, pass rate 0–100 and positive attempt limit.
- Only owning Expert and correct Draft→Submit / Rejected→Resubmit transition.

No minimum five lessons. `GET /api/v1/expert/courses/{id}/submission-readiness` supplies checklist DTOs. Submit/resubmit reuse the same validation under the existing course write lock and record audit events. Missing conditions produce 400; invalid state 409; role/ownership failures 403.

The frontend blocks sending unsaved metadata or while uploading/saving/checking. Saving course metadata or lessons refreshes readiness. **Kiểm tra lại** supports refreshing after other content modules update quizzes.

## Remaining scope, not claimed complete

- CoursePackage persistence/pricing UI is not yet implemented. The checklist explicitly uses current course price rather than pretending package requirements are implemented.
- Quiz question/answer validation and quiz management remain separate content-module work; this checklist checks quiz availability/settings, not a fully playable assessment.
- The existing resubmit service checks status and current readiness but does not yet prove a substantive revision occurred after rejection. The UI instructs Expert to revise; this is not a replacement for eventual server-side revision validation.
- Existing legacy MySQL schema issues outside the Lesson timestamp fix remain unchanged. No schema/data migration, automatic seeding, commit or push was performed.

## Verification

- CourseSubmissionIntegrationTests: missing prerequisites, one complete lesson sufficient, missing video, inactive lesson/quiz, invalid quiz setting, role/owner/state checks, successful submit/resubmit and audit.
- Existing workflow tests updated with isolated ready-course fixtures.
- Full backend suite, frontend typecheck and build run.
- `.ui-review/course-submit-check.cjs` uses API stubs to test Draft submit, Rejected resubmit, unsaved-change lock, incomplete readiness, server validation error and Pending Review edit lock. No real course was submitted by browser tests.
