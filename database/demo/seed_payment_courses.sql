-- Local fixture for Student registration and future VNPAY integration.
-- Inserts only demo courses, packages and lesson outlines. No registrations/payments/access.
-- Apply V007 first. Existing rows, including edited demo rows, are never updated/deleted.
DELIMITER //
DROP PROCEDURE IF EXISTS ols_seed_payment_courses//
CREATE PROCEDURE ols_seed_payment_courses()
BEGIN
    DECLARE expert_value BIGINT;
    DECLARE course_value BIGINT;
    DECLARE course_number INT DEFAULT 1;
    DECLARE tier_number INT;
    DECLARE lesson_number INT;
    DECLARE base_price DECIMAL(12,0);
    DECLARE title_value VARCHAR(255);
    DECLARE duration_value VARCHAR(20);
    DECLARE locked_value INT DEFAULT 0;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        IF locked_value = 1 THEN DO RELEASE_LOCK('ols_payment_course_seed'); END IF;
        RESIGNAL;
    END;
    IF DATABASE() <> 'online_learning_system' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Select the local online_learning_system database first';
    END IF;
    SELECT GET_LOCK('ols_payment_course_seed',10) INTO locked_value;
    IF locked_value <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Another payment demo seed is running';
    END IF;
    SELECT MIN(u.id) INTO expert_value FROM users u
        JOIN user_roles ur ON ur.user_id=u.id JOIN roles r ON r.id=ur.role_id
        WHERE u.email='expert@onlinelearning.local' AND r.code='EXPERT'
            AND u.enabled=1 AND u.blocked=0;
    IF expert_value IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='An active Expert demo account is required';
    END IF;

    START TRANSACTION;
    WHILE course_number <= 3 DO
        SET title_value=CONCAT('[DEMO PAYMENT] ',CASE course_number
            WHEN 1 THEN 'Spring Boot' WHEN 2 THEN 'React' ELSE 'MySQL' END);
        SET base_price=50000*course_number;
        SELECT MIN(id) INTO course_value FROM course WHERE title=title_value AND expert_id=expert_value;
        IF course_value IS NULL THEN
            INSERT INTO course(title,description,category,price,status,expert_id)
            VALUES(title_value,'LOCAL DEMO ONLY: select a package and create a pending registration for payment integration testing.',
                'Demo Payment',base_price,'PUBLISHED',expert_value);
            SET course_value=LAST_INSERT_ID();
        END IF;

        SET tier_number=1;
        WHILE tier_number <= 3 DO
            SET duration_value=CASE tier_number WHEN 1 THEN 'ONE_MONTH' WHEN 2 THEN 'ONE_YEAR' ELSE 'LIFETIME' END;
            IF NOT EXISTS(SELECT 1 FROM course_package WHERE course_id=course_value AND duration=duration_value) THEN
                INSERT INTO course_package(course_id,duration,price,discount_percent,active)
                VALUES(course_value,duration_value,
                    base_price*CASE tier_number WHEN 1 THEN 1 WHEN 2 THEN 5 ELSE 10 END,
                    CASE tier_number WHEN 1 THEN 0 WHEN 2 THEN 10 ELSE 15 END,1);
            END IF;
            SET tier_number=tier_number+1;
        END WHILE;

        SET lesson_number=1;
        WHILE lesson_number <= 2 DO
            IF NOT EXISTS(SELECT 1 FROM lesson WHERE course_id=course_value
                    AND title=CONCAT('[DEMO PAYMENT] Lesson ',lesson_number)) THEN
                INSERT INTO lesson(course_id,title,content,position,active,created_at,updated_at)
                VALUES(course_value,CONCAT('[DEMO PAYMENT] Lesson ',lesson_number),
                    'Sample lesson outline for local testing only; no video asset.',
                    lesson_number,1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6));
            END IF;
            SET lesson_number=lesson_number+1;
        END WHILE;
        SET course_number=course_number+1;
    END WHILE;
    COMMIT;
    DO RELEASE_LOCK('ols_payment_course_seed');
END//
CALL ols_seed_payment_courses()//
DROP PROCEDURE ols_seed_payment_courses//
DELIMITER ;

SELECT c.id AS course_id,c.title,p.id AS package_id,p.duration,p.price,p.discount_percent,
    ROUND(p.price*(100-p.discount_percent)/100,0) AS final_price
FROM course c JOIN course_package p ON p.course_id=c.id
WHERE c.title IN ('[DEMO PAYMENT] Spring Boot','[DEMO PAYMENT] React','[DEMO PAYMENT] MySQL')
ORDER BY c.id,p.id;
