-- Preflight: SELECT COUNT(*) FROM student_lesson_progress;
-- Apply to the local legacy schema only after verifying no existing rows need remapping.
-- ORM entities use singular lesson/course_registration tables.
ALTER TABLE student_lesson_progress DROP FOREIGN KEY fk_progress_lesson;
ALTER TABLE student_lesson_progress DROP FOREIGN KEY fk_progress_registration;
ALTER TABLE student_lesson_progress MODIFY registration_id BIGINT NOT NULL;
ALTER TABLE student_lesson_progress ADD CONSTRAINT fk_progress_current_registration
    FOREIGN KEY (registration_id) REFERENCES course_registration(id);
