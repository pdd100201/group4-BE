-- Certificate module. Back up the database before applying schema changes.
-- Snapshot requirement IDs deliberately do not reference mutable lesson/quiz rows.
CREATE TABLE IF NOT EXISTS certificate_policies (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 course_id BIGINT NOT NULL, version INT NOT NULL, enabled BIT NOT NULL,
 issuer_name VARCHAR(160) NOT NULL, content_fingerprint VARCHAR(64) NOT NULL,
 configured_by_id BIGINT NOT NULL, created_at DATETIME(6) NOT NULL,
 CONSTRAINT uq_certificate_policy_version UNIQUE(course_id,version),
 CONSTRAINT fk_cert_policy_course FOREIGN KEY(course_id) REFERENCES course(id),
 CONSTRAINT fk_cert_policy_actor FOREIGN KEY(configured_by_id) REFERENCES users(id)
);
CREATE TABLE IF NOT EXISTS certificate_policy_lessons (
 policy_id BIGINT NOT NULL, lesson_id BIGINT NOT NULL, lesson_title VARCHAR(255) NOT NULL,
 PRIMARY KEY(policy_id,lesson_id),
 CONSTRAINT fk_cert_lesson_policy FOREIGN KEY(policy_id) REFERENCES certificate_policies(id)
);
CREATE TABLE IF NOT EXISTS certificate_policy_quizzes (
 policy_id BIGINT NOT NULL, quiz_id BIGINT NOT NULL, quiz_title VARCHAR(255) NOT NULL,
 PRIMARY KEY(policy_id,quiz_id),
 CONSTRAINT fk_cert_quiz_policy FOREIGN KEY(policy_id) REFERENCES certificate_policies(id)
);
CREATE TABLE IF NOT EXISTS course_certificates (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 student_id BIGINT NOT NULL, course_id BIGINT NOT NULL, registration_id BIGINT NOT NULL,
 policy_id BIGINT NOT NULL, code VARCHAR(32) NOT NULL, status VARCHAR(16) NOT NULL,
 student_name_snapshot VARCHAR(255) NOT NULL, course_title_snapshot VARCHAR(255) NOT NULL,
 expert_name_snapshot VARCHAR(255) NOT NULL, issuer_name_snapshot VARCHAR(160) NOT NULL,
 issued_at DATETIME(6) NOT NULL, evidence_snapshot LONGTEXT NOT NULL,
 revoked_at DATETIME(6) NULL, revocation_reason VARCHAR(500) NULL,
 CONSTRAINT uq_certificate_student_course UNIQUE(student_id,course_id),
 CONSTRAINT uq_certificate_code UNIQUE(code),
 CONSTRAINT fk_cert_student FOREIGN KEY(student_id) REFERENCES users(id),
 CONSTRAINT fk_cert_course FOREIGN KEY(course_id) REFERENCES course(id),
 CONSTRAINT fk_cert_registration FOREIGN KEY(registration_id) REFERENCES course_registration(id),
 CONSTRAINT fk_cert_policy FOREIGN KEY(policy_id) REFERENCES certificate_policies(id),
 INDEX idx_certificate_student(student_id,id)
);
CREATE TABLE IF NOT EXISTS certificate_audit_logs (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 certificate_id BIGINT NOT NULL, actor_id BIGINT NOT NULL,
 action VARCHAR(20) NOT NULL, reason VARCHAR(500) NULL, occurred_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_cert_audit_certificate FOREIGN KEY(certificate_id) REFERENCES course_certificates(id),
 CONSTRAINT fk_cert_audit_actor FOREIGN KEY(actor_id) REFERENCES users(id),
 INDEX idx_certificate_audit(certificate_id,id)
);
