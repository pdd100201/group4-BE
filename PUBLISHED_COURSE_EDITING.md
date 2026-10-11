# Published course editing — Product Owner decision, 2026-10-11

The owning Expert can update a Published course's basic information (title, description, category, current price) and thumbnail directly. Saving preserves Published status and immediately changes the data returned by public catalog/detail APIs; there is no reapproval step.

- Ownership and Expert route authorization remain required.
- Draft and Rejected remain editable; Pending Review, Archived and legacy Approved remain locked.
- `editable` and `deletable` are separate response permissions. Published is editable but not deletable by Expert. Admin/Manager moderation/retention rules are unchanged.
- Thumbnail uploads accept Course Image on Published. Lesson Video upload restrictions are unchanged.
- Editor explicitly explains that Published changes become public immediately. Saving invalidates public catalog/detail and management queries.
- Lesson/quiz mutation rights on Published are unchanged because that part of the clarification was not answered. Existing lesson controls remain read-only on Published. CoursePackage pricing is still a separate unfinished slice.

Tests cover immediate public title/price updates, Published state preservation, wrong owner, Expert delete rejection and locked states. Frontend typecheck/build are checked separately.

This batch does not migrate or modify the development database. The existing schema issues in `DEMO_COURSE_DATA.md` may still affect writes; isolated H2 success is not proof that the current legacy MySQL schema accepts all writes.
