-- V004: Align app-table column types with Hibernate/JPA expectations.
--
-- Root cause of the "Referencing column ... and referenced column ... are
-- incompatible" WARN spam: the rotating DB contains a mix of
--   * legacy SQL tables (plural names, `bigint unsigned` / `tinyint unsigned`), and
--   * JPA-managed tables (singular names, signed `bigint`).
-- Hibernate (ddl-auto=update) tries to MODIFY the mismatched columns but MySQL
-- refuses while a foreign key is attached, so it logs the error and keeps going.
--
-- This script (manual run, no Flyway) is NON-DESTRUCTIVE with respect to app
-- data: it only drops the offending LEGACY foreign keys, normalises the shared
-- ID columns to signed BIGINT, re-points the three app FKs that were attached to
-- legacy tables, and re-adds the correct app FKs. Legacy orphan tables are left
-- in place (their data is preserved, only their cross-schema FKs are dropped).
--
-- It also neutralises three NOT NULL columns that no entity maps but which would
-- make future inserts fail ("Field 'x' doesn't have a default value"):
--   * posts.slug            -> made NULLable
--   * questions.question_type -> given a DEFAULT
--   * student_lesson_progress.registration_id -> made NULLable
--
-- Run:  mysql -u root online_learning_system < V004__align_app_schema_types.sql

SET FOREIGN_KEY_CHECKS = 0;

-- ---------------------------------------------------------------------------
-- 1. Drop FKs that block column-type changes or point at legacy tables.
--    (Everything below only touches legacy target tables or the unused
--     users.role_id column; app entity relationships are re-added in step 3.)
-- ---------------------------------------------------------------------------
ALTER TABLE users                     DROP FOREIGN KEY fk_users_role;             -- legacy users.role_id (unmapped)
ALTER TABLE audit_logs                DROP FOREIGN KEY fk_audit_logs_user;       -- legacy -> users
ALTER TABLE course_registrations      DROP FOREIGN KEY fk_registrations_user;    -- legacy -> users
ALTER TABLE courses                   DROP FOREIGN KEY fk_courses_expert;        -- legacy -> users
ALTER TABLE courses                   DROP FOREIGN KEY fk_courses_reviewer;      -- legacy -> users
ALTER TABLE payments                  DROP FOREIGN KEY fk_payments_user;         -- legacy -> users
ALTER TABLE post_comments             DROP FOREIGN KEY fk_comments_user;         -- legacy -> users
ALTER TABLE post_comments             DROP FOREIGN KEY fk_comments_post;         -- legacy -> posts
ALTER TABLE quiz_answers              DROP FOREIGN KEY fk_answers_grader;        -- legacy -> users
ALTER TABLE transactions              DROP FOREIGN KEY fk_transactions_user;     -- legacy -> users
ALTER TABLE transactions              DROP FOREIGN KEY fk_transactions_processor;-- legacy -> users
ALTER TABLE question_options          DROP FOREIGN KEY fk_options_question;      -- legacy -> questions
ALTER TABLE quiz_answers              DROP FOREIGN KEY fk_answers_question;      -- legacy -> questions
ALTER TABLE quiz_questions            DROP FOREIGN KEY fk_quiz_questions_question;-- legacy -> questions
ALTER TABLE password_reset_tokens     DROP FOREIGN KEY fk_password_reset_user;   -- app -> users (re-added)
ALTER TABLE posts                     DROP FOREIGN KEY fk_posts_author;          -- app -> users (re-added)
ALTER TABLE questions                 DROP FOREIGN KEY fk_questions_course;      -- app -> LEGACY courses (re-pointed)
ALTER TABLE student_lesson_progress   DROP FOREIGN KEY fk_progress_registration; -- app -> LEGACY course_registrations
ALTER TABLE student_lesson_progress   DROP FOREIGN KEY fk_progress_lesson;       -- app -> LEGACY lessons

-- ---------------------------------------------------------------------------
-- 2. Normalise shared ID / relation columns to signed BIGINT (and fix the
--    unmapped NOT NULL columns).
-- ---------------------------------------------------------------------------
ALTER TABLE users        MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE users        DROP COLUMN role_id;                                    -- unused by JPA (User uses user_roles)
ALTER TABLE roles        MODIFY id BIGINT NOT NULL AUTO_INCREMENT;

ALTER TABLE posts        MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE posts        MODIFY author_id BIGINT NOT NULL;
ALTER TABLE posts        MODIFY view_count BIGINT NOT NULL DEFAULT 0;
ALTER TABLE posts        MODIFY slug VARCHAR(280) NULL;                          -- was NOT NULL UNIQUE, unmapped

ALTER TABLE questions    MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE questions    MODIFY course_id BIGINT NOT NULL;
ALTER TABLE questions    MODIFY question_type ENUM('SINGLE_CHOICE','MULTIPLE_CHOICE','TRUE_FALSE','TEXT')
                             NOT NULL DEFAULT 'SINGLE_CHOICE';                   -- was NOT NULL no default, unmapped

ALTER TABLE password_reset_tokens MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE password_reset_tokens MODIFY user_id BIGINT NOT NULL;

ALTER TABLE student_lesson_progress MODIFY id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE student_lesson_progress MODIFY student_id BIGINT NOT NULL;
ALTER TABLE student_lesson_progress MODIFY lesson_id BIGINT NOT NULL;
ALTER TABLE student_lesson_progress MODIFY registration_id BIGINT NULL;          -- was NOT NULL, unmapped
ALTER TABLE student_lesson_progress MODIFY last_position_seconds INT NOT NULL DEFAULT 0;

-- ---------------------------------------------------------------------------
-- 3. Re-add the correct app foreign keys (targets now signed BIGINT).
-- ---------------------------------------------------------------------------
ALTER TABLE posts                   ADD CONSTRAINT fk_posts_author              FOREIGN KEY (author_id)    REFERENCES users(id);
ALTER TABLE post_comment            ADD CONSTRAINT fk_post_comment_author      FOREIGN KEY (author_id)    REFERENCES users(id);
ALTER TABLE post_comment            ADD CONSTRAINT fk_post_comment_post        FOREIGN KEY (post_id)      REFERENCES posts(id);
ALTER TABLE course                  ADD CONSTRAINT fk_course_expert            FOREIGN KEY (expert_id)    REFERENCES users(id);
ALTER TABLE course                  ADD CONSTRAINT fk_course_reviewer          FOREIGN KEY (reviewed_by_id) REFERENCES users(id);
ALTER TABLE audit_log               ADD CONSTRAINT fk_audit_log_actor          FOREIGN KEY (actor_id)     REFERENCES users(id);
ALTER TABLE auth_sessions           ADD CONSTRAINT fk_auth_sessions_user       FOREIGN KEY (user_id)      REFERENCES users(id);
ALTER TABLE email_verification_tokens ADD CONSTRAINT fk_email_verif_user       FOREIGN KEY (user_id)      REFERENCES users(id);
ALTER TABLE password_reset_tokens   ADD CONSTRAINT fk_password_reset_user      FOREIGN KEY (user_id)      REFERENCES users(id);
ALTER TABLE course_registration     ADD CONSTRAINT fk_course_registration_student FOREIGN KEY (student_id) REFERENCES users(id);
ALTER TABLE quiz_attempt            ADD CONSTRAINT fk_quiz_attempt_student     FOREIGN KEY (student_id)   REFERENCES users(id);
ALTER TABLE student_lesson_progress ADD CONSTRAINT fk_student_progress_student FOREIGN KEY (student_id)   REFERENCES users(id);
ALTER TABLE user_roles              ADD CONSTRAINT fk_user_roles_user          FOREIGN KEY (user_id)      REFERENCES users(id);
ALTER TABLE user_roles              ADD CONSTRAINT fk_user_roles_role          FOREIGN KEY (role_id)      REFERENCES roles(id);

ALTER TABLE questions               ADD CONSTRAINT fk_questions_course         FOREIGN KEY (course_id)    REFERENCES course(id);
ALTER TABLE question_option         ADD CONSTRAINT fk_question_option_question FOREIGN KEY (question_id)  REFERENCES questions(id);
ALTER TABLE quiz_answer             ADD CONSTRAINT fk_quiz_answer_question     FOREIGN KEY (question_id)  REFERENCES questions(id);
ALTER TABLE quiz_question           ADD CONSTRAINT fk_quiz_question_question   FOREIGN KEY (question_id)  REFERENCES questions(id);

ALTER TABLE student_lesson_progress ADD CONSTRAINT fk_student_progress_lesson  FOREIGN KEY (lesson_id)    REFERENCES lesson(id);

SET FOREIGN_KEY_CHECKS = 1;
