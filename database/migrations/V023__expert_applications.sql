-- Additive, repeatable MySQL 8 migration. Applicant submissions and review history
-- are preserved separately; evidence files are private and downloaded via authorization.
CREATE TABLE IF NOT EXISTS expert_applications (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    applicant_id BIGINT NOT NULL,
    specialty VARCHAR(200) NOT NULL,
    experience_years INT NOT NULL,
    experience VARCHAR(4000) NOT NULL,
    introduction VARCHAR(4000) NOT NULL,
    portfolio_url VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL,
    submitted_at DATETIME(6) NOT NULL,
    reviewed_by_id BIGINT NULL,
    reviewed_at DATETIME(6) NULL,
    review_reason VARCHAR(2000) NULL,
    INDEX idx_expert_application_applicant (applicant_id,id),
    INDEX idx_expert_application_status (status,id),
    CONSTRAINT fk_expert_application_user FOREIGN KEY (applicant_id) REFERENCES users(id),
    CONSTRAINT fk_expert_application_reviewer FOREIGN KEY (reviewed_by_id) REFERENCES users(id)
);
CREATE TABLE IF NOT EXISTS expert_application_documents (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    application_id BIGINT NOT NULL,
    original_name VARCHAR(200) NOT NULL,
    content_type VARCHAR(80) NOT NULL,
    size_bytes BIGINT NOT NULL,
    content LONGBLOB NOT NULL,
    CONSTRAINT fk_expert_document_application FOREIGN KEY (application_id) REFERENCES expert_applications(id)
);
