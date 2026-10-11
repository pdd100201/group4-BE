package com.onlinelearning.service;

import com.onlinelearning.dto.course.CourseSummaryResponse;
import java.util.List;

public interface CourseCatalogService {
    List<CourseSummaryResponse> listPublished();
    List<CourseSummaryResponse> listPublished(String search, String category);
    com.onlinelearning.dto.course.PublicCourseDetailResponse getPublished(Long id);
}
