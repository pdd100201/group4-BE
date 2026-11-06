# Expert applications

Students register from **Hồ sơ cá nhân → Đăng ký trở thành giảng viên**.
They provide specialty, years of experience, work history and teaching introduction.
A HTTPS portfolio link and up to 3 PDF/JPG/PNG/WebP documents (5 MB each) are optional.
Evidence is stored privately in the database and downloaded through an authenticated
endpoint available only to the applicant and Admin.

Only one pending submission is allowed per applicant. Submissions are immutable.
After rejection, the applicant can submit a new application; the old record remains
available to Admin. Attachments must be selected again for a new submission.

Admin uses **Duyệt giảng viên** to search/filter applications, review details and
download evidence, then approve or reject. Rejection requires a reason. Decisions
are final for that application and concurrent reviews are serialized. Approval adds
EXPERT while retaining STUDENT and existing enrollments. Admin account management
also keeps STUDENT when assigning EXPERT. New decisions create in-app notifications
and audit records in the same transaction.

The frontend refreshes the pending status every 15 seconds. Account roles are
refreshed from `/profile` on focus and every minute while visible; approval on the
profile page refreshes them immediately. JWT authorization already reads current
database roles. Expert and Student navigation remain available for dual-role users.

Before running the updated backend on MySQL, back up the configured database and
apply `database/migrations/V023__expert_applications.sql`. It creates two new tables
without modifying existing accounts, applications or financial records.

API:

- `GET /api/v1/expert-applications/mine`: latest own submission, or 204 if none.
- `POST /api/v1/expert-applications`: multipart `details` JSON + optional `files`.
- `GET /api/v1/admin/expert-applications?status=PENDING&search=&page=0`: Admin queue.
- `GET /api/v1/admin/expert-applications/{id}`: Admin details and document metadata.
- `PUT /api/v1/admin/expert-applications/{id}/review`: `{ "approve": true, "reason": "..." }`.
- `GET /api/v1/expert-applications/documents/{id}`: authorized private download.
