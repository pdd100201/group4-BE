# Lesson creation fix — 2026-10-11

## Reproduced cause

The local MySQL `lesson` table requires `created_at` and `updated_at` without defaults. The Lesson entity previously mapped neither field, so Hibernate inserted only content/position/course/video fields and MySQL rejected the write with `Field 'created_at' doesn't have a default value`. The API's generic error handler displayed `Unexpected server error` in the form.

## Fix

Lesson now maps both existing columns. Hibernate sets creation/update timestamps on insertion, retains creation time on later edits and updates modification time. No schema migration, column defaults, database reset or existing data changes are required.

This change only fixes Lesson timestamp mapping. Other legacy schema mismatches noted in `DEMO_COURSE_DATA.md` are not resolved here. Existing ownership, course-state, duplicate-position and retained-progress rules are unchanged.

## Verification

- Regression test verifies persisted timestamps and unchanged creation time after editing.
- ExpertLesson, Media and PublicCourse integration test suites run against isolated H2.
- `tools/ProbeLessonWrite.java` reproduces a local MySQL service write inside a transaction marked rollback-only, with Hibernate DDL disabled and demo-user initialization disabled. It does not retain a test lesson or alter schema; an auto-increment ID may be consumed by the rollback.

Restart the normal backend to load the entity mapping before retrying the form.
