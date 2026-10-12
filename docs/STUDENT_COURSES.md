# UC15 - Current owned course list

GET `/api/v1/student/courses?page=0&size=12&q=&sort=newest` is restricted to an
active Student. Identity comes from the authenticated session, never a client ID.
The response contains `content`, `page`, `size`, `totalElements`, `totalPages`.

Only the Student's ACTIVE registrations with validFrom <= now and either a
lifetime snapshot or validUntil > now appear. Pending, cancelled, expired, future
access and other students' registrations are excluded. Each course appears once
using its latest currently valid registration. Hidden/archived retained courses
can appear with `available:false`, without a public-course link.

DTO includes course ID/title/category/Expert/thumbnail, registration ID, saved
package name and validity. Title search escapes SQL wildcard characters; sorting
supports `newest` and `title`; size is bounded to 100, and paging happens in DB.

FE replaces the old My Courses placeholder with these records and invalidates its
user-scoped cache after a successful payment. No payment data is re-created.
Lesson playback, progress tabs, completion statistics and certificates are not
implemented in this narrow list change. No fake progress or learning action.

Verification uses compilation, FE lint/typecheck/build, isolated H2 command checks
and desktop/mobile screenshots. No new test classes or MySQL data edits.
