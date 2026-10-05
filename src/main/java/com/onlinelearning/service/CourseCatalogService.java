package com.onlinelearning.service;

import com.onlinelearning.dto.course.CourseSummaryResponse;
import java.util.List;

public interface CourseCatalogService {
    List<CourseSummaryResponse> listPublished();
}
