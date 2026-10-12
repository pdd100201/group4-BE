package com.onlinelearning.repository;

import com.onlinelearning.entity.CoursePackage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface CoursePackageRepository extends JpaRepository<CoursePackage,Long> {
    List<CoursePackage> findByCourseIdOrderByIdAsc(Long courseId);
    List<CoursePackage> findByCourseIdAndActiveTrueOrderByIdAsc(Long courseId);
    List<CoursePackage> findByCourseIdInAndActiveTrue(Collection<Long> courseIds);
}
