package com.onlinelearning.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/** Reproduces the legacy MySQL NOT NULL column using an isolated, empty H2 database. */
@DataJpaTest(showSql = false)
@ActiveProfiles("test")
class TransactionTimestampIntegrationTest {
    @Autowired TestEntityManager em;

    @BeforeEach
    void requireLegacyUpdatedAtColumn() {
        // Test-only schema: no connection to the application's live MySQL database.
        em.getEntityManager().createNativeQuery(
            "ALTER TABLE payment_transactions ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP(6) NOT NULL")
            .executeUpdate();
    }

    private Transaction transaction() {
        User user = new User(); user.setEmail("timestamp-test@example.test");
        user.setFullName("Timestamp Test"); user.setPasswordHash("test-only"); user.setEnabled(true);
        em.persist(user);
        Course course = new Course(); course.setTitle("Timestamp course"); course.setExpert(user);
        em.persist(course);
        CourseRegistration registration = new CourseRegistration();
        registration.setStudent(user); registration.setCourse(course); registration.setAmount(new BigDecimal("100000"));
        em.persist(registration);
        Payment payment = new Payment(); payment.setRegistration(registration); payment.setAmount(registration.getAmount());
        payment.setProvider("SEPAY_TEST"); em.persist(payment);
        Transaction tx = new Transaction(); tx.setPayment(payment); tx.setAmount(registration.getAmount());
        tx.setType("SEPAY_PENDING"); tx.setPayload("test-only");
        return tx;
    }

    @Test
    void insertPopulatesBothRequiredDatabaseTimestamps() {
        Transaction tx = transaction();
        em.persistAndFlush(tx);
        assertNotNull(tx.getCreatedAt());
        Object[] times = (Object[]) em.getEntityManager().createNativeQuery(
                "SELECT created_at, updated_at FROM payment_transactions WHERE id=:id")
            .setParameter("id", tx.getId()).getSingleResult();
        assertNotNull(times[0]); assertNotNull(times[1]);
    }

    @Test
    void updatingTransactionRefreshesUpdatedAtAndPreservesExistingCreatedAt() {
        Transaction tx = transaction();
        Instant created = Instant.parse("2025-01-01T00:00:00Z");
        tx.setCreatedAt(created);
        em.persistAndFlush(tx);
        Instant insertedUpdate = tx.getUpdatedAt();
        assertNotNull(insertedUpdate);
        // Deterministic stale value makes this assertion independent of clock resolution or sleeps.
        tx.setUpdatedAt(created);
        tx.setType("SEPAY_EXPIRED");
        em.flush(); em.clear();
        Transaction saved = em.find(Transaction.class, tx.getId());
        assertEquals(created, saved.getCreatedAt());
        assertTrue(saved.getUpdatedAt().isAfter(created));
        assertFalse(saved.getUpdatedAt().isBefore(insertedUpdate.minusNanos(1000)));
        assertEquals("SEPAY_EXPIRED", saved.getType());
    }
}
