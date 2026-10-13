-- V008: optional lesson link for quizzes (SRS: a quiz belongs to a course and may be attached to a lesson).
-- ddl-auto=update would also add the column, but this migration documents/controls the FK explicitly.
ALTER TABLE quiz ADD COLUMN lesson_id BIGINT NULL;
ALTER TABLE quiz ADD CONSTRAINT fk_quiz_lesson FOREIGN KEY (lesson_id) REFERENCES lesson(id);
