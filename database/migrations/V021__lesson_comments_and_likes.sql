-- MySQL 8. Additive, repeatable migration. Existing lessons and data are preserved.
CREATE TABLE IF NOT EXISTS lesson_comments (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    lesson_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    parent_id BIGINT NULL,
    content VARCHAR(2000) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_lesson_comment_thread (lesson_id,parent_id,id),
    CONSTRAINT fk_lesson_comment_lesson FOREIGN KEY (lesson_id) REFERENCES lesson(id),
    CONSTRAINT fk_lesson_comment_author FOREIGN KEY (author_id) REFERENCES users(id),
    CONSTRAINT fk_lesson_comment_parent FOREIGN KEY (parent_id) REFERENCES lesson_comments(id)
);
CREATE TABLE IF NOT EXISTS lesson_comment_likes (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    comment_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    CONSTRAINT uq_lesson_comment_like UNIQUE (comment_id,student_id),
    CONSTRAINT fk_lesson_comment_like_comment FOREIGN KEY (comment_id) REFERENCES lesson_comments(id),
    CONSTRAINT fk_lesson_comment_like_student FOREIGN KEY (student_id) REFERENCES users(id)
);
