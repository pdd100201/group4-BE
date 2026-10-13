package com.onlinelearning.repository;

import com.onlinelearning.dto.dashboard.DashboardCourseMetric;
import com.onlinelearning.dto.dashboard.DashboardRegistration;
import com.onlinelearning.entity.CourseStatus;
import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DashboardRepository {
    private final EntityManager em;

    public long countCourses(CourseStatus status) {
        return em.createQuery("select count(c.id) from Course c where c.status=:status", Long.class)
                .setParameter("status", status).getSingleResult();
    }

    public long countRegistrations() {
        return em.createQuery("select count(r.id) from CourseRegistration r", Long.class).getSingleResult();
    }

    public List<DashboardRegistration> recentRegistrations(int limit) {
        return em.createQuery("""
                select new com.onlinelearning.dto.dashboard.DashboardRegistration(
                    r.id, r.student.fullName, r.student.email, r.course.title,
                    r.packageNameSnapshot, r.amount, r.status, r.createdAt)
                from CourseRegistration r order by r.id desc
                """, DashboardRegistration.class).setMaxResults(limit).getResultList();
    }

    public List<DashboardCourseMetric> topCourses(int limit) {
        return em.createQuery("""
                select new com.onlinelearning.dto.dashboard.DashboardCourseMetric(
                    c.id, c.title, count(r.id))
                from Course c left join CourseRegistration r on r.course=c
                group by c.id, c.title order by count(r.id) desc, c.id desc
                """, DashboardCourseMetric.class).setMaxResults(limit).getResultList();
    }
}
