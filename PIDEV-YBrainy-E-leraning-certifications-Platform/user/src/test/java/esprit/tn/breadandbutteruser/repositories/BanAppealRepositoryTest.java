package esprit.tn.breadandbutteruser.repositories;

import esprit.tn.breadandbutteruser.entities.BanAppeal;
import esprit.tn.breadandbutteruser.entities.User;
import esprit.tn.breadandbutteruser.entities.enums.IntegrityStatus;
import esprit.tn.breadandbutteruser.entities.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class BanAppealRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private BanAppealRepository banAppealRepository;

    @Test
    void findByUserUserIdOrderBySubmittedDateDesc_returnsAppealsForUser() {
        // Given
        User user = createUser("kc-123", "johndoe");
        entityManager.persist(user);
        
        BanAppeal appeal1 = createAppeal(user, "PENDING", false);
        BanAppeal appeal2 = createAppeal(user, "APPROVED", true);
        entityManager.persist(appeal1);
        entityManager.persist(appeal2);
        entityManager.flush();

        // When
        List<BanAppeal> result = banAppealRepository.findByUserUserIdOrderBySubmittedDateDesc(user.getUserId());

        // Then
        assertThat(result).hasSize(2);
    }

    @Test
    void findByAppealStatusOrderBySubmittedDateDesc_returnsFiltered() {
        // Given
        User user = createUser("kc-123", "johndoe");
        entityManager.persist(user);
        
        BanAppeal pending = createAppeal(user, "PENDING", false);
        BanAppeal approved = createAppeal(user, "APPROVED", true);
        entityManager.persist(pending);
        entityManager.persist(approved);
        entityManager.flush();

        // When
        List<BanAppeal> result = banAppealRepository.findByAppealStatusOrderBySubmittedDateDesc("PENDING");

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAppealStatus()).isEqualTo("PENDING");
    }

    @Test
    void findByViewedOrderBySubmittedDateDesc_returnsByViewedStatus() {
        // Given
        User user = createUser("kc-123", "johndoe");
        entityManager.persist(user);
        
        BanAppeal unviewed = createAppeal(user, "PENDING", false);
        BanAppeal viewed = createAppeal(user, "PENDING", true);
        entityManager.persist(unviewed);
        entityManager.persist(viewed);
        entityManager.flush();

        // When
        List<BanAppeal> result = banAppealRepository.findByViewedOrderBySubmittedDateDesc(false);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).isViewed()).isFalse();
    }

    @Test
    void existsByUserUserIdAndAppealStatus_returnsCorrectBoolean() {
        // Given
        User user = createUser("kc-123", "johndoe");
        entityManager.persist(user);
        
        BanAppeal appeal = createAppeal(user, "PENDING", false);
        entityManager.persist(appeal);
        entityManager.flush();

        // Then
        assertThat(banAppealRepository.existsByUserUserIdAndAppealStatus(user.getUserId(), "PENDING")).isTrue();
        assertThat(banAppealRepository.existsByUserUserIdAndAppealStatus(user.getUserId(), "REJECTED")).isFalse();
    }

    @Test
    void findTopByUserUserIdOrderBySubmittedDateDesc_returnsLatest() {
        // Given
        User user = createUser("kc-123", "johndoe");
        entityManager.persist(user);
        
        BanAppeal old = createAppeal(user, "REJECTED", true);
        old.setSubmittedDate(LocalDateTime.now().minusDays(5));
        BanAppeal recent = createAppeal(user, "PENDING", false);
        recent.setSubmittedDate(LocalDateTime.now());
        entityManager.persist(old);
        entityManager.persist(recent);
        entityManager.flush();

        // When
        Optional<BanAppeal> result = banAppealRepository.findTopByUserUserIdOrderBySubmittedDateDesc(user.getUserId());

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getAppealStatus()).isEqualTo("PENDING");
    }

    @Test
    void findAllByOrderBySubmittedDateDesc_returnsAllOrdered() {
        // Given
        User user = createUser("kc-123", "johndoe");
        entityManager.persist(user);
        
        entityManager.persist(createAppeal(user, "PENDING", false));
        entityManager.persist(createAppeal(user, "APPROVED", true));
        entityManager.persist(createAppeal(user, "REJECTED", true));
        entityManager.flush();

        // When
        List<BanAppeal> result = banAppealRepository.findAllByOrderBySubmittedDateDesc();

        // Then
        assertThat(result).hasSize(3);
    }

    @Test
    void saveAndRetrieveBanAppeal() {
        // Given
        User user = createUser("kc-123", "johndoe");
        entityManager.persist(user);
        
        BanAppeal appeal = new BanAppeal();
        appeal.setUser(user);
        appeal.setDescription("I promise to behave");
        appeal.setAppealStatus("PENDING");
        appeal.setViewed(false);
        appeal.setSubmittedDate(LocalDateTime.now());

        // When
        BanAppeal saved = banAppealRepository.save(appeal);

        // Then
        assertThat(saved.getAppealId()).isNotNull();
        BanAppeal retrieved = entityManager.find(BanAppeal.class, saved.getAppealId());
        assertThat(retrieved.getDescription()).isEqualTo("I promise to behave");
        assertThat(retrieved.getAppealStatus()).isEqualTo("PENDING");
    }

    private User createUser(String keycloakId, String username) {
        User user = User.builder()
                .keycloakUserId(keycloakId)
                .username(username)
                .email(username + "@test.com")
                .firstName("First")
                .lastName("Last")
                .role(Role.STUDENT)
                .dateOfBirth(LocalDate.of(2000, 1, 1))
                .age(25)
                .country("Tunisia")
                .status(IntegrityStatus.SUSPENDED)
                .accountCreatedAt(LocalDateTime.now())
                .lastProfileUpdate(LocalDateTime.now())
                .build();
        return user;
    }

    private BanAppeal createAppeal(User user, String status, boolean viewed) {
        BanAppeal appeal = new BanAppeal();
        appeal.setUser(user);
        appeal.setDescription("Appeal description");
        appeal.setAppealStatus(status);
        appeal.setViewed(viewed);
        appeal.setSubmittedDate(LocalDateTime.now());
        return appeal;
    }
}
