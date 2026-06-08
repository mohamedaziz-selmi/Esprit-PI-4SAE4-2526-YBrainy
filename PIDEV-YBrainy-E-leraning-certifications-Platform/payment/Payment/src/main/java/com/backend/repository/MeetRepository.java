package com.backend.repository;

import com.backend.entity.Meet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MeetRepository extends JpaRepository<Meet, Long> {
    List<Meet> findAllByOrderByStartTimeAsc();
    List<Meet> findByCourse_Id(Long courseId);
    List<Meet> findByStartTimeBetweenOrderByStartTimeAsc(LocalDateTime start, LocalDateTime end);
}

