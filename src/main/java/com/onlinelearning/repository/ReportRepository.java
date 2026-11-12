package com.onlinelearning.repository;

import com.onlinelearning.dto.report.*;
import com.onlinelearning.entity.PaymentStatus;
import com.onlinelearning.entity.RegistrationStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ReportRepository {
    private final EntityManager em;
    private static final String DTO = "com.onlinelearning.dto.report.ReportRow";
    private static final String FIRST_TRANSACTION = "exists (select t.id from Transaction t where t.payment = p "
            + "and t.id = (select min(ft.id) from Transaction ft where ft.payment = p "
            + "and (ft.type in ('VNPAY_SUCCEEDED', 'SEPAY_SUCCEEDED') or (ft.type not like 'VNPAY_%' and ft.type not like 'SEPAY_%')))";

    // A registration is confirmed by a successful payment, even if the student later cancels access.
    private static final String CONFIRMED_REGISTRATION = "exists (select p.id from Payment p where p.registration = r "
            + "and p.status = :success and " + FIRST_TRANSACTION + "))";
    private static final String REGISTRATION_PAID_AMOUNT = "(select coalesce(sum(p.amount), 0) from Payment p "
            + "where p.registration = r and p.status = :success and " + FIRST_TRANSACTION + "))";

    public record Result(List<ReportRow> rows, long count, BigDecimal amount, Map<String, Long> metrics) {}

    public Result read(ReportKind kind, Long courseId, RegistrationStatus status,
            Instant from, Instant until, int offset, int size) {
        if (kind == ReportKind.COURSES) return content(courseId, from, until, offset, size);
        Map<String, Object> params = new HashMap<>();
        String course = courseId == null ? "" : " and c.id = :courseId";
        if (courseId != null) params.put("courseId", courseId);
        String registrationDates = dates("r", from, until, params);
        String paymentDates = FIRST_TRANSACTION + dates("t", from, until, params) + ")";
        // Demo reports include confirmed test payments as requested; pending/failed never count.
        String payments = " from Payment p join p.registration r join r.course c where p.status = :success"
                + " and " + paymentDates + course;
        String query;
        String countQuery;
        String amountQuery;
        if (kind == ReportKind.REGISTRATIONS) {
            params.put("success", PaymentStatus.SUCCEEDED);
            String registrations = " from CourseRegistration r join r.course c where " + CONFIRMED_REGISTRATION
                    + course + registrationDates;
            if (status != null) {
                registrations += " and r.status = :status";
                params.put("status", status);
            }
            query = "select new " + DTO + "(r.id, c.title, r.status, " + REGISTRATION_PAID_AMOUNT + ", r.student.fullName, "
                    + "r.student.email, r.createdAt, r.note)" + registrations + " order by r.id desc";
            countQuery = "select count(r.id)" + registrations;
            amountQuery = "select coalesce(sum(p.amount), 0) from Payment p join p.registration r join r.course c "
                    + "where p.status = :success and " + FIRST_TRANSACTION + ")" + course + registrationDates
                    + (status == null ? "" : " and r.status = :status");
        } else {
            params.put("success", PaymentStatus.SUCCEEDED);
            amountQuery = "select coalesce(sum(p.amount), 0)" + payments;
            if (kind == ReportKind.REVENUE) {
                query = "select new " + DTO + "(c.id, c.title, count(p.id), sum(p.amount))"
                        + payments + " group by c.id, c.title order by c.id desc";
                countQuery = "select count(distinct c.id)" + payments;
            } else {
                String correlatedPayments = "select coalesce(sum(p.amount), 0) from Payment p "
                        + "where p.registration.course = c and p.status = :success and " + paymentDates;
                query = "select new " + DTO + "(c.id, c.title, c.status, "
                        + "(select count(r.id) from CourseRegistration r where r.course = c and " + CONFIRMED_REGISTRATION
                        + registrationDates + "), (" + correlatedPayments + "))"
                        + " from Course c where 1=1" + course + " order by c.id desc";
                countQuery = "select count(c.id) from Course c where 1=1" + course;
            }
        }
        List<ReportRow> rows = bind(em.createQuery(query, ReportRow.class), params)
                .setFirstResult(offset).setMaxResults(size).getResultList();
        long count = bind(em.createQuery(countQuery, Long.class), params).getSingleResult();
        BigDecimal amount = bind(em.createQuery(amountQuery, BigDecimal.class), params).getSingleResult();
        Map<String, Long> metrics = kind == ReportKind.REVENUE
                ? Map.of("successfulPayments", bind(em.createQuery("select count(p.id)" + payments, Long.class), params).getSingleResult())
                : Map.of("confirmedRegistrations", count);
        return new Result(rows, count, amount, metrics);
    }

    private Result content(Long courseId, Instant from, Instant until, int offset, int size) {
        Map<String,Object> params = new HashMap<>();
        String filter = "c.deletedAt is null";
        if (courseId != null) { filter += " and c.id = :courseId"; params.put("courseId", courseId); }
        filter += dates("c", from, until, params);
        String questionFilter = "q.active = true and (q.status is null or q.status <> 'RETIRED')";
        String base = " from Course c where " + filter;
        String query = "select new " + DTO + "(c.id,c.title,c.status,"
                + "(select count(l.id) from Lesson l where l.course = c and l.active = true),"
                + "(select count(q.id) from Question q where q.course = c and " + questionFilter + "))"
                + base + " order by c.id desc";
        var rows = bind(em.createQuery(query, ReportRow.class),params).setFirstResult(offset).setMaxResults(size).getResultList();
        long count = bind(em.createQuery("select count(c.id)" + base,Long.class),params).getSingleResult();
        Map<String,Long> metrics = new HashMap<>();
        metrics.put("lessons",bind(em.createQuery("select count(l.id) from Lesson l join l.course c where l.active = true and " + filter,Long.class),params).getSingleResult());
        metrics.put("questions",bind(em.createQuery("select count(q.id) from Question q join q.course c where " + questionFilter + " and " + filter,Long.class),params).getSingleResult());
        for (Object[] entry : bind(em.createQuery("select c.status,count(c.id)" + base + " group by c.status",Object[].class),params).getResultList())
            metrics.put(entry[0].toString(),((Number)entry[1]).longValue());
        return new Result(rows,count,BigDecimal.ZERO,metrics);
    }

    private String dates(String alias, Instant from, Instant until, Map<String, Object> params) {
        String result = "";
        if (from != null) { params.put("from", from); result += " and " + alias + ".createdAt >= :from"; }
        if (until != null) { params.put("until", until); result += " and " + alias + ".createdAt < :until"; }
        return result;
    }

    private <T> TypedQuery<T> bind(TypedQuery<T> query, Map<String, Object> params) {
        for (var parameter : query.getParameters()) query.setParameter(parameter.getName(), params.get(parameter.getName()));
        return query;
    }
}
