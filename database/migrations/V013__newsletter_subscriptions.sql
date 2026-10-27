CREATE TABLE IF NOT EXISTS newsletter_subscriptions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    subscribed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_newsletter_subscriptions_email UNIQUE (email)
);
