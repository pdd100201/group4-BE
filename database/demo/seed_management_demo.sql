-- Local development fixture only. Never run against production.
-- Creates 60 registrations, 60 simulated payments and 60 transactions.
-- Rerunnable: demo course titles, registration notes and provider references mark this dataset.
-- No VNPAY request/callback is executed. Existing records are never updated/deleted.
DELIMITER //
DROP PROCEDURE IF EXISTS ols_seed_management_demo//
CREATE PROCEDURE ols_seed_management_demo()
BEGIN
    DECLARE student_id_value BIGINT;
    DECLARE expert_id_value BIGINT;
    DECLARE course_id_value BIGINT;
    DECLARE registration_id_value BIGINT;
    DECLARE payment_id_value BIGINT;
    DECLARE item_number INT DEFAULT 1;
    DECLARE group_number INT;
    DECLARE package_days INT;
    DECLARE price_value DECIMAL(38,2);
    DECLARE discount_value DECIMAL(38,2);
    DECLARE registration_state VARCHAR(30);
    DECLARE payment_state VARCHAR(30);
    DECLARE marker_value VARCHAR(255);
    DECLARE title_value VARCHAR(255);
    DECLARE created_value DATETIME(6);
    DECLARE from_value DATETIME(6);
    DECLARE until_value DATETIME(6);
    DECLARE locked_value INT DEFAULT 0;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        IF locked_value = 1 THEN DO RELEASE_LOCK('ols_management_demo_seed'); END IF;
        RESIGNAL;
    END;

    IF DATABASE() <> 'online_learning_system' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Select the local online_learning_system database first';
    END IF;
    SELECT GET_LOCK('ols_management_demo_seed', 10) INTO locked_value;
    IF locked_value <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Another demo seed is running';
    END IF;
    SELECT u.id INTO student_id_value FROM users u
        JOIN user_roles ur ON ur.user_id = u.id JOIN roles r ON r.id = ur.role_id
        WHERE u.email = 'student@onlinelearning.local' AND r.code = 'STUDENT' LIMIT 1;
    SELECT u.id INTO expert_id_value FROM users u
        JOIN user_roles ur ON ur.user_id = u.id JOIN roles r ON r.id = ur.role_id
        WHERE u.email = 'expert@onlinelearning.local' AND r.code = 'EXPERT' LIMIT 1;
    IF student_id_value IS NULL OR expert_id_value IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Existing Student/Expert demo accounts are required';
    END IF;

    START TRANSACTION;
    WHILE item_number <= 60 DO
        SET group_number = MOD(item_number - 1, 3) + 1;
        SET title_value = CONCAT('[DEMO UC44-48] ', CASE group_number
            WHEN 1 THEN 'Spring Boot' WHEN 2 THEN 'React' ELSE 'MySQL' END);
        SELECT MIN(id) INTO course_id_value FROM course
            WHERE title = title_value AND expert_id = expert_id_value;
        IF course_id_value IS NULL THEN
            INSERT INTO course (title, description, category, price, status, expert_id)
            VALUES (title_value, 'LOCAL DEMO: registration/transaction manual testing only.',
                'Demo', 300000, 'PUBLISHED', expert_id_value);
            SET course_id_value = LAST_INSERT_ID();
        END IF;

        SET marker_value = CONCAT('OLS-DEMO-UC44-48-20261011-', LPAD(item_number, 3, '0'));
        SELECT MIN(id) INTO registration_id_value FROM course_registration
            WHERE note = marker_value AND student_id = student_id_value AND course_id = course_id_value;
        IF registration_id_value IS NULL THEN
            SET package_days = CASE group_number WHEN 1 THEN 30 WHEN 2 THEN 90 ELSE 180 END;
            SET price_value = 150000 + group_number * 100000 + item_number * 1000;
            SET discount_value = CASE WHEN MOD(item_number, 2) = 0 THEN 10000 ELSE 0 END;
            SET registration_state = CASE MOD(item_number - 1, 5)
                WHEN 0 THEN 'ACTIVE' WHEN 1 THEN 'PENDING_PAYMENT'
                WHEN 2 THEN 'FAILED' WHEN 3 THEN 'CANCELLED' ELSE 'EXPIRED' END;
            SET payment_state = CASE registration_state WHEN 'ACTIVE' THEN 'SUCCEEDED'
                WHEN 'EXPIRED' THEN 'SUCCEEDED' WHEN 'FAILED' THEN 'FAILED'
                WHEN 'CANCELLED' THEN 'CANCELLED' ELSE 'PENDING' END;
            SET created_value = TIMESTAMPADD(DAY,
                -CASE WHEN registration_state = 'EXPIRED' THEN 200 + item_number
                    ELSE MOD(item_number, 45) END, UTC_TIMESTAMP(6));
            SET from_value = CASE WHEN payment_state = 'SUCCEEDED' THEN created_value ELSE NULL END;
            SET until_value = CASE WHEN payment_state = 'SUCCEEDED'
                THEN TIMESTAMPADD(DAY, package_days, from_value) ELSE NULL END;
            -- Active registrations must still be valid even when the creation date is older.
            IF registration_state = 'ACTIVE' THEN
                SET from_value = TIMESTAMPADD(DAY, -MOD(item_number, 10), UTC_TIMESTAMP(6));
                SET until_value = TIMESTAMPADD(DAY, package_days, from_value);
            END IF;
            INSERT INTO course_registration (student_id, course_id, status, amount, note,
                created_at, valid_from, valid_until, package_name_snapshot, price_snapshot,
                discount_snapshot, validity_days_snapshot)
            VALUES (student_id_value, course_id_value, registration_state, price_value - discount_value,
                marker_value, created_value, from_value, until_value,
                CONCAT('[DEMO] ', package_days, '-day package'), price_value, discount_value, package_days);
            SET registration_id_value = LAST_INSERT_ID();
            INSERT INTO payment (registration_id, amount, provider, provider_reference, status)
            VALUES (registration_id_value, price_value - discount_value, 'VNPAY',
                CONCAT('DEMO-ONLY-', LPAD(item_number, 3, '0'), '-20261011'), payment_state);
            SET payment_id_value = LAST_INSERT_ID();
            INSERT INTO payment_transactions (payment_id, amount, type, payload, created_at)
            VALUES (payment_id_value, price_value - discount_value, 'DEMO_PAYMENT_ATTEMPT',
                'Local simulated payment; no real gateway call or money transfer.',
                TIMESTAMPADD(MINUTE, 5, created_value));
        END IF;
        SET item_number = item_number + 1;
    END WHILE;
    COMMIT;
    DO RELEASE_LOCK('ols_management_demo_seed');
END//
CALL ols_seed_management_demo()//
DROP PROCEDURE ols_seed_management_demo//
DELIMITER ;

SELECT status, COUNT(*) AS demo_registrations
FROM course_registration
WHERE note LIKE 'OLS-DEMO-UC44-48-20261011-%'
GROUP BY status;
SELECT p.status, COUNT(*) AS demo_transactions
FROM payment_transactions t JOIN payment p ON p.id = t.payment_id
WHERE p.provider_reference LIKE 'DEMO-ONLY-%-20261011'
GROUP BY p.status;
