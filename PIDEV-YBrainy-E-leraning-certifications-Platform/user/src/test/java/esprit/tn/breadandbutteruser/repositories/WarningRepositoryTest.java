package esprit.tn.breadandbutteruser.repositories;

import esprit.tn.breadandbutteruser.entities.User;
import esprit.tn.breadandbutteruser.entities.Warning;
import esprit.tn.breadandbutteruser.entities.enums.IntegrityStatus;
import esprit.tn.breadandbutteruser.entities.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class WarningRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private WarningRepository warningRepository;

    @Test
    void findByUser_returnsWarningsForUser() {
        // Given
        User user = createUser("kc-123", "johndoe");
        entityManager.persist(user);
        
        Warning w1 = createWarning(user, "LOW", "First warning");
        Warning w2 = createWarning(user, "HIGH", "Second warning");
        entityManager.persist(w1);
        entityManager.persist(w2);
        entityManager.flush();

        // When
        List<Warning> result = warningRepository.findByUser(user);

        // Then
        assertThat(result).hasSize(2);
    }

    @Test
    void findByUserUserId_returnsWarningsForUserId() {
        // Given
        User user = createUser("kc-123", "johndoe");
        entityManager.persist(user);
        
        Warning warning = createWarning(user, "MEDIUM", "Warning message");
        entityManager.persist(warning);
        entityManager.flush();

        // When
        List<Warning> result = warningRepository.findByUserUserId(user.getUserId());

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getReason()).isEqualTo("Warning message");
    }

    @Test
    void findBySeverity_returnsWarningsBySeverity() {
        // Given
        User user = createUser("kc-123", "johndoe");
        entityManager.persist(user);
        
        entityManager.persist(createWarning(user, "LOW", "Low warning"));
        entityManager.persist(createWarning(user, "LOW", "Another low"));
        entityManager.persist(createWarning(user, "HIGH", "High warning"));
        entityManager.flush();

        // When
        List<Warning> result = warningRepository.findBySeverity("LOW");

        // Then
        assertThat(result).hasSize(2);
    }

    @Test
    void findByIssuedDateAfter_returnsRecentWarnings() {
        // Given
        User user = createUser("kc-123", "johndoe");
        entityManager.persist(user);
        
        Warning old = createWarning(user, "LOW", "Old");
        old.setIssuedDate(LocalDateTime.now().minusDays(10));
        Warning recent = createWarning(user, "HIGH", "Recent");
        recent.setIssuedDate(LocalDateTime.now().minusDays(1));
        entityManager.persist(old);
        entityManager.persist(recent);
        entityManager.flush();

        // When
        List<Warning> result = warningRepository.findByIssuedDateAfter(LocalDateTime.now().minusDays(5));

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getReason()).isEqualTo("Recent");
    }

    @Test
    void countByUserUserId_returnsCount() {
        // Given
        User user1 = createUser("kc-1", "user1");
        User user2 = createUser("kc-2", "user2");
        entityManager.persist(user1);
        entityManager.persist(user2);
        
        entityManager.persist(createWarning(user1, "LOW", "W1"));
        entityManager.persist(createWarning(user1, "MEDIUM", "W2"));
        entityManager.persist(createWarning(user2, "HIGH", "W3"));
        entityManager.flush();

        // When & Then
        assertThat(warningRepository.countByUserUserId(user1.getUserId())).isEqualTo(2L);
        assertThat(warningRepository.countByUserUserId(user2.getUserId())).isEqualTo(1L);
    }

    @Test
    void saveAndRetrieveWarning() {
        // Given
        User user = createUser("kc-123", "johndoe");
        entityManager.persist(user);
        
        Warning warning = new Warning();
        warning.setUser(user);
        warning.setSeverity("HIGH");
        warning.setReason("This is a serious warning");
        warning.setIssuedDate(LocalDateTime.now());
        warning.setIssuedBy("admin");

        // When
        Warning saved = warningRepository.save(warning);

        // Then
        assertThat(saved.getWarningId()).isNotNull();
        Warning retrieved = entityManager.find(Warning.class, saved.getWarningId());
        assertThat(retrieved.getReason()).isEqualTo("This is a serious warning");
        assertThat(retrieved.getSeverity()).isEqualTo("HIGH");
    }

    private User createUser(String keycloakId, String username) {
        return User.builder()
                .keycloakUserId(keycloakId)
                .username(username)
                .email(username + "@test.com")
                .firstName("First")
                .lastName("Last")
                .role(Role.STUDENT)
                .dateOfBirth(LocalDate.of(2000, 1, 1))
                .age(25)
                .country("Tunisia")
                .status(IntegrityStatus.SECURE)
                .accountCreatedAt(LocalDateTime.now())
                .lastProfileUpdate(LocalDateTime.now())
                .build();
    }

    private Warning createWarning(User user, String severity, String reason) {
        Warning warning = new Warning();
        warning.setUser(user);
        warning.setSeverity(severity);
        warning.setReason(reason);
        warning.setIssuedDate(LocalDateTime.now());
        warning.setIssuedBy("admin");
        return warning;
    }
}
