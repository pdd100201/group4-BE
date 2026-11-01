-- Additive chat media migration. Existing text messages and users are preserved.
CREATE TABLE IF NOT EXISTS chat_attachments (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    recipient_id BIGINT NULL,
    storage_key VARCHAR(36) NOT NULL,
    original_name VARCHAR(200) NOT NULL,
    content_type VARCHAR(80) NOT NULL,
    bytes BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_chat_attachment_storage (storage_key),
    CONSTRAINT fk_chat_attachment_owner FOREIGN KEY (owner_id) REFERENCES users(id),
    CONSTRAINT fk_chat_attachment_recipient FOREIGN KEY (recipient_id) REFERENCES users(id)
);
-- Run these statements once, after checking the column does not already exist.
ALTER TABLE chat_messages ADD COLUMN attachment_id BIGINT NULL;
ALTER TABLE chat_messages ADD UNIQUE KEY uk_chat_message_attachment (attachment_id);
ALTER TABLE chat_messages ADD CONSTRAINT fk_chat_message_attachment FOREIGN KEY (attachment_id) REFERENCES chat_attachments(id);
