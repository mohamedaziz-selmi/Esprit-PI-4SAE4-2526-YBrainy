package tn.esprit.tpfoyer.Services;

import tn.esprit.tpfoyer.Dto.AiSearchResultDTO;
import tn.esprit.tpfoyer.Dto.CourseAnalyticsDTO;
import tn.esprit.tpfoyer.Dto.CourseDetailResponseDTO;
import tn.esprit.tpfoyer.Dto.CourseRequestDTO;
import tn.esprit.tpfoyer.Dto.CourseResponseDTO;
import tn.esprit.tpfoyer.Entities.enums.CourseCategory;
import tn.esprit.tpfoyer.Entities.enums.CourseLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.Map;

public interface ICourseService {

    CourseResponseDTO createCourse(CourseRequestDTO dto, MultipartFile thumbnail, MultipartFile certificate);

    Page<CourseResponseDTO> getAllCourses(
            String search,
            CourseCategory category,
            CourseLevel level,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean isPublished,
            Long instructorId,
            Pageable pageable);

    CourseDetailResponseDTO getCourseById(Long id);

    CourseResponseDTO updateCourse(Long id, CourseRequestDTO dto, MultipartFile thumbnail, MultipartFile certificate, Long requestingUserId, String requestingRole);

    void deleteCourse(Long id, Long requestingUserId, String requestingRole);
    
    /**
     * Get analytics data for a specific course.
     * @param courseId the course ID
     * @return analytics data including enrollments, progress, and engagement metrics
     */
    CourseAnalyticsDTO getCourseAnalytics(Long courseId);

    AiSearchResultDTO aiSearch(String query, int page, int size);

    /**
     * Toggle publish status of a course. Throws IllegalArgumentException on ownership violation.
     */
    Map<String, Object> togglePublish(Long id, boolean publish, Long requestingUserId, String requestingRole);

    boolean existsById(Long id);

    /**
     * Return a count of published+unpublished courses grouped by category name.
     */
    Map<String, Long> getCourseStatsByCategory();
}
