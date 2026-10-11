-- MySQL 8: back up the database and stop the backend before applying.
-- Preserve all IDs and assignments; align legacy types with BaseEntity Long.
-- MySQL DDL auto-commits. Expected FK uses CASCADE update / RESTRICT delete.
ALTER TABLE users DROP FOREIGN KEY fk_users_role;
ALTER TABLE roles MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE users MODIFY COLUMN role_id BIGINT NULL;
ALTER TABLE users ADD CONSTRAINT fk_users_role FOREIGN KEY (role_id)
    REFERENCES roles (id) ON UPDATE CASCADE ON DELETE RESTRICT;
