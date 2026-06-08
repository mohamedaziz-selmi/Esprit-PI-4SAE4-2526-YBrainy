package com.backend.repository;

import com.backend.entity.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LessonRepository extends JpaRepository<Lesson, Long> {

    @Query("SELECT l FROM Lesson l WHERE l.course.id = :courseId ORDER BY l.orderIndex ASC")
    List<Lesson> findByCourseIdOrderByOrderIndexAsc(@Param("courseId") Long courseId);

    @Query("SELECT COUNT(l) FROM Lesson l WHERE l.course.id = :courseId")
    int countByCourseId(@Param("courseId") Long courseId);
}

