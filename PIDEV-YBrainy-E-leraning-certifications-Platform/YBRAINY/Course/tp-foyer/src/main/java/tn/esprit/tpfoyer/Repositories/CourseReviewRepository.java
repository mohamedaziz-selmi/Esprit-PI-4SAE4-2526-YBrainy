package tn.esprit.tpfoyer.Repositories;

import tn.esprit.tpfoyer.Entities.CourseReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourseReviewRepository extends JpaRepository<CourseReview, Long> {

    List<CourseReview> findByCourseIdOrderByCreatedAtDesc(Long courseId);

    boolean existsByStudentIdAndCourseId(Long studentId, Long courseId);

    void deleteAllByCourseId(Long courseId);

    long countByCourseId(Long courseId);

    @Query("SELECT AVG(r.rating) FROM CourseReview r WHERE r.courseId = :courseId")
    Double findAverageRatingByCourseId(@Param("courseId") Long courseId);

    @Query("SELECT COUNT(r) FROM CourseReview r WHERE r.courseId = :courseId AND r.rating = :rating")
    long countByRatingAndCourseId(@Param("courseId") Long courseId, @Param("rating") int rating);
}
