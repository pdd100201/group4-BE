package com.onlinelearning.repository;
import com.onlinelearning.entity.*; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface CourseRepository extends JpaRepository<Course,Long> { List<Course> findByExpertId(Long expertId); List<Course> findByStatusIn(Collection<CourseStatus> statuses); }
