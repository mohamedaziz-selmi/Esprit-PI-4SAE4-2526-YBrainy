package esprit.tn.breadandbutteruser.repositories;

import esprit.tn.breadandbutteruser.entities.BanAppeal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BanAppealRepository extends JpaRepository<BanAppeal, Long> {
    List<BanAppeal> findAllByOrderBySubmittedDateDesc();
    List<BanAppeal> findByUserUserIdOrderBySubmittedDateDesc(Long userId);
    List<BanAppeal> findByAppealStatusOrderBySubmittedDateDesc(String appealStatus);
    List<BanAppeal> findByViewedOrderBySubmittedDateDesc(boolean viewed);
    boolean existsByUserUserIdAndAppealStatus(Long userId, String appealStatus);
    Optional<BanAppeal> findTopByUserUserIdOrderBySubmittedDateDesc(Long userId);
}
