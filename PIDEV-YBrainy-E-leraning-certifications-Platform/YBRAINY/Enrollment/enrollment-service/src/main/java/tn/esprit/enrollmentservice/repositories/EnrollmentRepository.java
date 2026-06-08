package tn.esprit.enrollmentservice.repositories;

import tn.esprit.enrollmentservice.entities.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    List<Enrollment> findByStudentId(Long studentId);
    Optional<Enrollment> findByStudentIdAndCourseId(Long studentId, Long courseId);
    boolean existsByStudentIdAndCourseId(Long studentId, Long courseId);
    List<Enrollment> findByCourseId(Long courseId);
    void deleteAllByCourseId(Long courseId);
    Optional<Enrollment> findByCertificateId(String certificateId);

    @Query("SELECT MONTH(e.enrollmentDate) as month, YEAR(e.enrollmentDate) as year, COUNT(e) as count FROM Enrollment e GROUP BY YEAR(e.enrollmentDate), MONTH(e.enrollmentDate) ORDER BY YEAR(e.enrollmentDate), MONTH(e.enrollmentDate)")
    List<Object[]> countByMonth();
}
