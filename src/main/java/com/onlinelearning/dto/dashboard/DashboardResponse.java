package com.onlinelearning.dto.dashboard;

import java.math.BigDecimal;
import java.util.List;

public record DashboardResponse(BigDecimal totalRevenue, long totalRegistrations,
        long activeStudents, long activeCourses, long pendingCourses,
        List<DashboardRegistration> recentRegistrations,
        List<DashboardCourseMetric> topCourses) {}
