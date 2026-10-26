-- Additive migration. Existing chat history and file identifiers are preserved.
-- Apply once; check information_schema.COLUMNS before rerunning.
ALTER TABLE chat_attachments
    ADD COLUMN cloud_public_id VARCHAR(255) NULL,
    ADD COLUMN cloud_resource_type VARCHAR(10) NULL,
    ADD COLUMN cloud_format VARCHAR(20) NULL,
    ADD COLUMN cloud_delivery_type VARCHAR(20) NULL;
