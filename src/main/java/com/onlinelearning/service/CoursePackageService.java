package com.onlinelearning.service;

import com.onlinelearning.dto.course.*;
import java.util.List;

public interface CoursePackageService {
    List<CoursePackageResponse> get(Long courseId, String email);
    List<CoursePackageResponse> update(Long courseId, String email, CoursePackagesRequest request);
}
