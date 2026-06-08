package tn.esprit.lessonservice.repositories;

import tn.esprit.lessonservice.entities.LessonProgress;
import tn.esprit.lessonservice.entities.ProgressStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface LessonProgressRepository extends JpaRepository<LessonProgress, Long> {
    List<LessonProgress> findByEnrollmentId(Long enrollmentId);
    Optional<LessonProgress> findByEnrollmentIdAndLessonId(Long enrollmentId, Long lessonId);
    long countByEnrollmentIdAndStatus(Long enrollmentId, ProgressStatus status);
    List<LessonProgress> findByEnrollmentIdIn(List<Long> enrollmentIds);
    void deleteAllByEnrollmentIdIn(List<Long> enrollmentIds);

    @Query("SELECT DISTINCT lp.enrollmentId FROM LessonProgress lp WHERE lp.lastActivityAt >= :since")
    List<Long> findEnrollmentIdsWithActivitySince(@Param("since") LocalDateTime since);
}
