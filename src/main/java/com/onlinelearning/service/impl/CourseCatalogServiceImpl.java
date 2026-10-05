package com.onlinelearning.service.impl;

import com.onlinelearning.dto.course.CourseSummaryResponse;
import com.onlinelearning.entity.CourseStatus;
import com.onlinelearning.repository.CourseRepository;
import com.onlinelearning.service.CourseCatalogService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseCatalogServiceImpl implements CourseCatalogService {
    private final CourseRepository courses;

    @Override
    public List<CourseSummaryResponse> listPublished() {
        return courses.findByStatusIn(List.of(CourseStatus.PUBLISHED))
                .stream().map(CourseSummaryResponse::from).toList();
    }
}
