-- MySQL: run on the configured application database before using notifications
-- if Hibernate schema update is disabled. No historical financial data is changed.
CREATE TABLE IF NOT EXISTS user_notifications (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 recipient_id BIGINT NOT NULL,
 event_key VARCHAR(180) NOT NULL,
 type VARCHAR(60) NOT NULL,
 category VARCHAR(30) NOT NULL,
 title VARCHAR(200) NOT NULL,
 message VARCHAR(2000) NOT NULL,
 link VARCHAR(400) NOT NULL,
 created_at DATETIME(6) NOT NULL,
 read_at DATETIME(6) NULL,
 CONSTRAINT fk_notification_recipient FOREIGN KEY (recipient_id) REFERENCES users(id),
 CONSTRAINT uq_notification_event UNIQUE (recipient_id,event_key),
 INDEX idx_notification_inbox (recipient_id,id),
 INDEX idx_notification_unread (recipient_id,read_at)
);
CREATE TABLE IF NOT EXISTS course_announcements (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 course_id BIGINT NOT NULL,
 request_key VARCHAR(36) NOT NULL,
 title VARCHAR(200) NOT NULL,
 message VARCHAR(1000) NOT NULL,
 recipient_count INT NOT NULL,
 created_at DATETIME(6) NOT NULL,
 CONSTRAINT fk_announcement_course FOREIGN KEY (course_id) REFERENCES course(id),
 CONSTRAINT uq_announcement_request UNIQUE (course_id,request_key)
);
