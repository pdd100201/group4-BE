-- The ORM uses course; questions already has a foreign key to that table.
-- Run only after verifying every questions.course_id exists in course.
-- Remove the obsolete second foreign key to legacy courses without deleting data.
ALTER TABLE questions DROP FOREIGN KEY fk_questions_course;
