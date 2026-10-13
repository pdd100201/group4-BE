package com.onlinelearning.service.impl;

import com.onlinelearning.dto.dashboard.DashboardResponse;
import com.onlinelearning.dto.report.ReportKind;
import com.onlinelearning.entity.CourseStatus;
import com.onlinelearning.repository.DashboardRepository;
import com.onlinelearning.repository.ReportRepository;
import com.onlinelearning.repository.UserRepository;
import com.onlinelearning.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {
    private final DashboardRepository dashboard;
    private final ReportRepository reports;
    private final UserRepository users;

    @Override
    public DashboardResponse read() {
        var revenue = reports.read(ReportKind.REVENUE, null, null, null, null, 0, 1).amount();
        return new DashboardResponse(revenue, dashboard.countRegistrations(),
                users.countActiveStudents(), dashboard.countCourses(CourseStatus.PUBLISHED),
                dashboard.countCourses(CourseStatus.PENDING_REVIEW),
                dashboard.recentRegistrations(8), dashboard.topCourses(5));
    }
}
