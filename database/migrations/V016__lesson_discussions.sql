-- MySQL: run on the configured database before using lesson discussions.
CREATE TABLE IF NOT EXISTS lesson_discussions (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 lesson_id BIGINT NOT NULL, author_id BIGINT NOT NULL,
 title VARCHAR(200) NOT NULL, content VARCHAR(4000) NOT NULL,
 hidden BIT NOT NULL DEFAULT 0, accepted_answer_id BIGINT NULL,
 created_at DATETIME(6) NOT NULL,
 INDEX idx_discussion_lesson (lesson_id,id),
 CONSTRAINT fk_discussion_lesson FOREIGN KEY (lesson_id) REFERENCES lesson(id),
 CONSTRAINT fk_discussion_author FOREIGN KEY (author_id) REFERENCES users(id)
);
CREATE TABLE IF NOT EXISTS lesson_discussion_answers (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 discussion_id BIGINT NOT NULL, author_id BIGINT NOT NULL,
 content VARCHAR(4000) NOT NULL, hidden BIT NOT NULL DEFAULT 0,
 created_at DATETIME(6) NOT NULL,
 INDEX idx_discussion_answer (discussion_id,id),
 CONSTRAINT fk_answer_discussion FOREIGN KEY (discussion_id) REFERENCES lesson_discussions(id),
 CONSTRAINT fk_answer_author FOREIGN KEY (author_id) REFERENCES users(id)
);
-- Guard for databases where Hibernate update has already generated the FK.
SET @discussion_fk_sql = IF(
 (SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE
  WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='lesson_discussions'
   AND COLUMN_NAME='accepted_answer_id' AND REFERENCED_TABLE_NAME IS NOT NULL)=0,
 'ALTER TABLE lesson_discussions ADD CONSTRAINT fk_discussion_accepted FOREIGN KEY (accepted_answer_id) REFERENCES lesson_discussion_answers(id)',
 'SELECT 1');
PREPARE discussion_fk_stmt FROM @discussion_fk_sql;
EXECUTE discussion_fk_stmt;
DEALLOCATE PREPARE discussion_fk_stmt;