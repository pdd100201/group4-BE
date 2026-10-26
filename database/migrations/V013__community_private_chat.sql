-- Additive MySQL migration. No existing records are modified or removed.
CREATE TABLE IF NOT EXISTS chat_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    sender_id BIGINT NOT NULL,
    recipient_id BIGINT NULL,
    client_message_id VARCHAR(36) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    sent_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_chat_sender_request (sender_id, client_message_id),
    KEY idx_chat_recipient_id (recipient_id, id),
    KEY idx_chat_sender_recipient_id (sender_id, recipient_id, id),
    CONSTRAINT fk_chat_sender FOREIGN KEY (sender_id) REFERENCES users(id),
    CONSTRAINT fk_chat_recipient FOREIGN KEY (recipient_id) REFERENCES users(id)
);
