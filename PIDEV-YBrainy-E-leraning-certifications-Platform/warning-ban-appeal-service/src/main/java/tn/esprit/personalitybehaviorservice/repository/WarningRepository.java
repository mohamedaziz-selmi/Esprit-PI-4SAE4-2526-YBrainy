package tn.esprit.warningbanappealservice.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import tn.esprit.warningbanappealservice.entity.Warning;

import java.util.List;

public interface WarningRepository extends MongoRepository<Warning, String> {
    List<Warning> findByUserIdOrderByIssuedDateDesc(Long userId);
    void deleteByUserId(Long userId);
}
