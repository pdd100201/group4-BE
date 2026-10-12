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
            + "and t.id = (select min(ft.id) from Transaction ft where ft.payment = p)";

    public record Result(List<ReportRow> rows, long count, BigDecimal amount) {}

    public Result read(ReportKind kind, Long courseId, RegistrationStatus status,
            Instant from, Instant until, int offset, int size) {
        Map<String, Object> params = new HashMap<>();
        String course = courseId == null ? "" : " and c.id = :courseId";
        if (courseId != null) params.put("courseId", courseId);
        String registrationDates = dates("r", from, until, params);
        String paymentDates = FIRST_TRANSACTION + dates("t", from, until, params) + ")";
        String payments = " from Payment p join p.registration r join r.course c where p.status = :success"
                + " and " + paymentDates + course;
        String query;
        String countQuery;
        String amountQuery;
        if (kind == ReportKind.REGISTRATIONS) {
            String registrations = " from CourseRegistration r join r.course c where 1=1"
                    + course + registrationDates;
            if (status != null) {
                registrations += " and r.status = :status";
                params.put("status", status);
            }
            query = "select new " + DTO + "(r.id, c.title, r.status, r.amount, r.student.fullName, "
                    + "r.student.email, r.createdAt, r.note)" + registrations + " order by r.id desc";
            countQuery = "select count(r.id)" + registrations;
            amountQuery = "select coalesce(sum(r.amount), 0)" + registrations;
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
                        + "(select count(r.id) from CourseRegistration r where r.course = c"
                        + registrationDates + "), (" + correlatedPayments + "))"
                        + " from Course c where 1=1" + course + " order by c.id desc";
                countQuery = "select count(c.id) from Course c where 1=1" + course;
            }
        }
        List<ReportRow> rows = bind(em.createQuery(query, ReportRow.class), params)
                .setFirstResult(offset).setMaxResults(size).getResultList();
        long count = bind(em.createQuery(countQuery, Long.class), params).getSingleResult();
        BigDecimal amount = bind(em.createQuery(amountQuery, BigDecimal.class), params).getSingleResult();
        return new Result(rows, count, amount);
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
