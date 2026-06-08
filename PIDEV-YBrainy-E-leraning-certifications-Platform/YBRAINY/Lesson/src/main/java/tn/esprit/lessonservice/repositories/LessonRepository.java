package tn.esprit.lessonservice.repositories;

import tn.esprit.lessonservice.entities.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface LessonRepository extends JpaRepository<Lesson, Long> {
    List<Lesson> findByCourseIdOrderByOrderIndexAsc(Long courseId);
    List<Lesson> findByCourseId(Long courseId);
    Optional<Lesson> findByIdAndCourseId(Long id, Long courseId);
    boolean existsByIdAndCourseId(Long id, Long courseId);
    long countByCourseId(Long courseId);

    @Query("SELECT MAX(l.orderIndex) FROM Lesson l WHERE l.courseId = :courseId")
    Optional<Integer> findMaxOrderIndexByCourseId(Long courseId);

    List<Lesson> findByTitleContainingIgnoreCase(String title);

    @Query("SELECT l FROM Lesson l LEFT JOIN FETCH l.contents WHERE l.id = :id")
    Optional<Lesson> findByIdWithContents(@Param("id") Long id);

    @Query("SELECT DISTINCT l FROM Lesson l LEFT JOIN FETCH l.contents WHERE l.courseId = :courseId ORDER BY l.orderIndex")
    List<Lesson> findByCourseIdWithContents(@Param("courseId") Long courseId);
}
