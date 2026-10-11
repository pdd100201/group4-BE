# Public course data — UC07 / UC10 / UC11

## Changes

- Homepage course cards now use `GET /api/v1/public/courses` and link to `/courses/{actualId}`. Category counts use the returned published courses; no sample IDs, course titles, teacher names or prices.
- Homepage and catalog share one typed card component.
- `GET /api/v1/public/courses/{id}` returns the requested Published course's title, description, category, current price, thumbnail, Expert display name, active lesson outline and active quiz count.
- Non-Published and missing courses return 404 without revealing private records. Invalid IDs return 400.
- The public DTO does not expose lesson content, video identifiers/URLs, quiz questions/answers, account credentials or registration data.
- Details render plain text descriptions safely (no untrusted HTML execution), loading, retry, unavailable and empty-outline states. Reload/direct URLs work.
- Course moderation/review invalidates the new public detail cache as well as the public catalog cache.

## Verification

- `PublicCourseIntegrationTests`: correct requested ID, active/sorted lesson outline, no private content, non-Published states, missing/invalid IDs, Expert metadata updates, archived visibility.
- Existing Manager review integration tests retained.
- Actual local MySQL API checks passed for sample Published IDs 4 and 6; Archived ID 5 returned 404. Test server used `ddl-auto=none` and disabled demo user initialization, without schema or data writes.
- Headless browser checks in FE `.ui-review/public-course-check.cjs`: homepage click, matching detail, refresh, another course, archived/invalid URL, mobile width and no JavaScript errors. Uses the real local API via a test-port substitution.
- Frontend typecheck and production build passed.

## Scope limitations

Only public course read flows were changed. Registration/payment, persisted CoursePackage pricing, free trial, ratings and review counts are not implemented by this batch. Their former static controls and sample values were removed from Course Detail rather than presented as real features. Homepage blog samples remain outside this request.

The database mapping issues documented in `DEMO_COURSE_DATA.md` still apply to writes. This batch verifies reads without migrating the database. Restart the normal backend to expose the new detail endpoint; no commit/push performed.
