package com.onlinelearning.repository;
import com.onlinelearning.entity.CourseAnnouncement;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface CourseAnnouncementRepository extends JpaRepository<CourseAnnouncement,Long> {
 Optional<CourseAnnouncement> findByCourseIdAndRequestKey(Long courseId,String key);
 Page<CourseAnnouncement> findByCourseIdOrderByIdDesc(Long courseId,Pageable page);
}