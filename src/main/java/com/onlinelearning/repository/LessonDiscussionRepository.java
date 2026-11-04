package com.onlinelearning.repository;
import com.onlinelearning.entity.LessonDiscussion;
import org.springframework.data.jpa.repository.*;
public interface LessonDiscussionRepository extends JpaRepository<LessonDiscussion,Long>, JpaSpecificationExecutor<LessonDiscussion> {}