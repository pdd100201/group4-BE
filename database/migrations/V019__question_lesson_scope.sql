-- Existing questions remain shared course questions until an Expert assigns a lesson.
ALTER TABLE questions ADD COLUMN lesson_id BIGINT NULL;
ALTER TABLE questions ADD CONSTRAINT fk_question_lesson FOREIGN KEY (lesson_id) REFERENCES lesson(id);
CREATE INDEX idx_question_course_lesson ON questions(course_id,lesson_id);
