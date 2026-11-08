-- Apply once when upgrading an existing MySQL database. Legacy private and community
-- messages keep a NULL course_id; the new chat API does not expose those old threads.
ALTER TABLE chat_messages
    ADD COLUMN course_id BIGINT NULL,
    ADD KEY idx_chat_course_id (course_id, id),
    ADD CONSTRAINT fk_chat_message_course FOREIGN KEY (course_id) REFERENCES course(id);

ALTER TABLE chat_attachments
    ADD COLUMN course_id BIGINT NULL,
    ADD CONSTRAINT fk_chat_attachment_course FOREIGN KEY (course_id) REFERENCES course(id);
