package com.onlinelearning.service.impl;

import com.onlinelearning.dto.course.CourseSummaryResponse;
import com.onlinelearning.dto.course.PublicCourseDetailResponse;
import com.onlinelearning.dto.course.PublicCourseDetailResponse.LessonOutline;
import com.onlinelearning.exception.ApiException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import org.springframework.http.HttpStatus;
import com.onlinelearning.entity.CourseStatus;
import com.onlinelearning.repository.CourseRepository;
import com.onlinelearning.repository.CoursePackageRepository;
import com.onlinelearning.dto.course.CoursePackageResponse;
import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;
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
    private final EntityManager em;
    private final CoursePackageRepository packages;

    @Override
    public List<CourseSummaryResponse> listPublished() {
        return listPublished("", "");
    }

    @Override
    public List<CourseSummaryResponse> listPublished(String search, String category) {
        if ((search != null && search.length() > 255) || (category != null && category.length() > 255))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Từ khóa hoặc danh mục quá dài.");
        org.springframework.data.jpa.domain.Specification<com.onlinelearning.entity.Course> spec = (root, query, cb) -> {
            root.fetch("expert"); root.fetch("thumbnail", jakarta.persistence.criteria.JoinType.LEFT);
            return cb.equal(root.get("status"), CourseStatus.PUBLISHED);
        };
        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.trim().toLowerCase(java.util.Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("title")), pattern, '\\'));
        }
        if (category != null && !category.isBlank()) spec = spec.and((root, query, cb) -> cb.equal(root.get("category"), category.trim()));
        var published = courses.findAll(spec, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "id"));
        if (published.isEmpty()) return List.of();
        Map<Long, BigDecimal> lowest = packages.findByCourseIdInAndActiveTrue(published.stream().map(c -> c.getId()).toList()).stream()
            .collect(Collectors.toMap(p -> p.getCourse().getId(), p -> CoursePackageResponse.from(p).finalPrice(), BigDecimal::min));
        return published.stream().map(c -> { var r = CourseSummaryResponse.from(c);
            return new CourseSummaryResponse(r.id(), r.title(), r.description(), r.category(), lowest.get(c.getId()), r.thumbnailUrl(), r.expertName()); }).toList();
    }

    @Override
    public PublicCourseDetailResponse getPublished(Long id) {
        if (id == null || id <= 0) throw new ApiException(HttpStatus.BAD_REQUEST, "Mã khóa học không hợp lệ.");
        var course = courses.findByIdAndStatus(id, CourseStatus.PUBLISHED)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Khóa học không tồn tại hoặc chưa được xuất bản."));
        var lessons = em.createQuery("select l.id as id, l.title as title, l.position as position from Lesson l where l.course.id=:id and l.active=true order by l.position, l.id", Tuple.class)
            .setParameter("id", id).getResultList().stream()
            .map(row -> new LessonOutline(row.get("id", Long.class), row.get("title", String.class), row.get("position", Integer.class))).toList();
        long quizCount = em.createQuery("select count(q) from Quiz q where q.course.id=:id and q.active=true", Long.class)
            .setParameter("id", id).getSingleResult();
        var available=packages.findByCourseIdAndActiveTrueOrderByIdAsc(id).stream().map(CoursePackageResponse::from).toList();
        BigDecimal lowest=available.stream().map(CoursePackageResponse::finalPrice).min(BigDecimal::compareTo).orElse(null);
        return new PublicCourseDetailResponse(course.getId(), course.getTitle(), course.getDescription(), course.getCategory(), lowest,
            course.getThumbnail() == null ? null : course.getThumbnail().getSecureUrl(), course.getExpert().getFullName(), lessons, quizCount, available);
    }
}
