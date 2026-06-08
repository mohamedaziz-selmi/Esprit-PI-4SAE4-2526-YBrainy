package esprit.tn.breadandbutteruser.repositories;


import esprit.tn.breadandbutteruser.entities.User;
import esprit.tn.breadandbutteruser.entities.Warning;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface WarningRepository extends JpaRepository<Warning, Long> {

    List<Warning> findByUser(User user);

    List<Warning> findByUserUserId(Long userId);

    List<Warning> findBySeverity(String severity);

    List<Warning> findByIssuedDateAfter(LocalDateTime date);

    Long countByUserUserId(Long userId);
}