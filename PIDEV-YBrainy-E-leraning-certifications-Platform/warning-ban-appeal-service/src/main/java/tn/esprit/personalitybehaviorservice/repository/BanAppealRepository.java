package tn.esprit.warningbanappealservice.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import tn.esprit.warningbanappealservice.entity.BanAppeal;

import java.util.List;
import java.util.Optional;

public interface BanAppealRepository extends MongoRepository<BanAppeal, String> {
    List<BanAppeal> findByUserIdOrderBySubmittedDateDesc(Long userId);
    List<BanAppeal> findByAppealStatusOrderBySubmittedDateDesc(String appealStatus);
    List<BanAppeal> findByViewedOrderBySubmittedDateDesc(boolean viewed);
    Optional<BanAppeal> findTopByUserIdOrderBySubmittedDateDesc(Long userId);
    boolean existsByUserIdAndAppealStatus(Long userId, String appealStatus);
    void deleteByUserId(Long userId);
}
