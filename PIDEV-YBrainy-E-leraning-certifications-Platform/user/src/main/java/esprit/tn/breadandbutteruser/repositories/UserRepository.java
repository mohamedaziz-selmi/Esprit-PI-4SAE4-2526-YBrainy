package esprit.tn.breadandbutteruser.repositories;

import esprit.tn.breadandbutteruser.entities.User;
import esprit.tn.breadandbutteruser.entities.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByKeycloakUserId(String keycloakUserId);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByFaceBiometricHash(String faceBiometricHash);

    boolean existsByKeycloakUserId(String keycloakUserId);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Optional<User> findFirstByRoleOrderByUserIdAsc(Role role);

    List<User> findAllByRole(Role role);

    @Query("select u.userId from User u")
    List<Long> findAllUserIds();

    @Query("select u.userId from User u where u.role = :role")
    List<Long> findAllUserIdsByRole(@Param("role") Role role);

    Optional<User> findByPersonalityPersonalityId(Long personalityId);

    Optional<User> findByPersonalityBehaviorBehaviorId(Long behaviorId);

    // Forum leaderboard support
    List<User> findAllByOrderByXpDesc();
}
